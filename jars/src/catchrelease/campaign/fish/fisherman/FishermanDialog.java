package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishShopDialog;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Talking to the Fisherman: survey data bought with fish, the outfitter, a fish buyer at market
 * price, and a monthly rumor.
 * <p>
 * A scripted dialog rather than rules.csv, because everything here is machinery a sheet cannot
 * do - counting the hold, pricing a survey ladder, driving the cargo picker - and the jobs'
 * contract is for jobs. The outfitter is the shop the player already knows: the dialog swaps
 * its own plugin for {@link FishShopDialog}, which rebuilds the same screen in place.
 */
public class FishermanDialog implements InteractionDialogPlugin {

    protected enum Option {
        MAIN,
        SURVEY,
        SURVEY_PAGE,
        OUTFITTER,
        SELL,
        SELL_PICK,
        RUMOR,
        LEAVE
    }

    /** Species per page of the survey list - the dialog column only holds so many options. */
    public static final int SURVEY_PAGE_SIZE = 5;

    protected InteractionDialogAPI dialog;
    protected int surveyPage = 0;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.getVisualPanel().showFleetInfo(FishermanConstants.FLEET_NAME,
                (com.fs.starfarer.api.campaign.CampaignFleetAPI) dialog.getInteractionTarget(),
                null, null);

        dialog.getTextPanel().addPara("The trawler's comm crackles. \"Evening. Lights are good"
                + " tonight. Buying, selling, or just drifting?\"");

        showMain();
    }

    protected void showMain() {
        dialog.getOptionPanel().clearOptions();

        dialog.getOptionPanel().addOption("Purchase survey data", Option.SURVEY);
        dialog.getOptionPanel().addOption("Access the outfitter", Option.OUTFITTER);
        dialog.getOptionPanel().addOption("Sell fish", Option.SELL);
        dialog.getOptionPanel().addOption("Ask about rumors", Option.RUMOR);
        dialog.getOptionPanel().addOption("Leave", Option.LEAVE);

        dialog.getOptionPanel().setShortcut(Option.LEAVE,
                org.lwjgl.input.Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof Option)) {
            if (optionData instanceof SurveyOffer) {
                buySurvey((SurveyOffer) optionData);
                return;
            }
            if (optionData instanceof FishRarity) {
                sellUpTo((FishRarity) optionData);
                return;
            }
            return;
        }

        switch ((Option) optionData) {
            case SURVEY:
                surveyPage = 0;
                showSurvey();
                break;
            case SURVEY_PAGE:
                surveyPage++;
                showSurvey();
                break;
            case OUTFITTER:
                openOutfitter();
                break;
            case SELL:
                showSell();
                break;
            case SELL_PICK:
                showSellPicker();
                break;
            case RUMOR:
                askRumor();
                break;
            case MAIN:
                showMain();
                break;
            case LEAVE:
                dialog.dismiss();
                break;
        }
    }

    //---------------------------------------------------------------- survey data

    /** One species on offer, with the fish that pay for it. */
    protected static class SurveyOffer {
        FishSpec spec;
        FishRarity costRarity;
        int costCount;
    }

    /**
     * The ladder: survey data costs fish one rung below the species' own rarity - commons cost a
     * common, since there is nothing below them to pay with. What is already caught or bought is
     * not on offer; knowing where it lives is no longer for sale.
     */
    protected void showSurvey() {
        List<SurveyOffer> offers = getSurveyOffers();

        dialog.getOptionPanel().clearOptions();

        if (offers.isEmpty()) {
            dialog.getTextPanel().addPara("\"Nothing left to sell you - you know these waters"
                    + " as well as I do.\"", Misc.getGrayColor());
            dialog.getOptionPanel().addOption("Back", Option.MAIN);
            return;
        }

        dialog.getTextPanel().addPara("\"Charts for charts. I mark your map, you fill my hold.\"");

        int pages = (offers.size() + SURVEY_PAGE_SIZE - 1) / SURVEY_PAGE_SIZE;
        if (surveyPage >= pages) surveyPage = 0;

        int start = surveyPage * SURVEY_PAGE_SIZE;
        int end = Math.min(offers.size(), start + SURVEY_PAGE_SIZE);

        for (int i = start; i < end; i++) {
            SurveyOffer offer = offers.get(i);

            String label = offer.spec.getDisplayName()
                    + " - " + offer.costCount + " "
                    + offer.costRarity.name().toLowerCase()
                    + (offer.costCount == 1 ? " specimen" : " specimens");

            dialog.getOptionPanel().addOption(label, offer, offer.spec.rarity.color, null);

            if (FishCurrency.count(offer.costRarity) < offer.costCount) {
                dialog.getOptionPanel().setEnabled(offer, false);
            }
        }

        if (pages > 1) {
            dialog.getOptionPanel().addOption(
                    "More (" + (surveyPage + 1) + "/" + pages + ")", Option.SURVEY_PAGE);
        }

        dialog.getOptionPanel().addOption("Back", Option.MAIN);
    }

    protected List<SurveyOffer> getSurveyOffers() {
        List<SurveyOffer> offers = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || spec.regions.isEmpty()) continue;
            if (FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id)) continue;

            SurveyOffer offer = new SurveyOffer();
            offer.spec = spec;

            int rung = spec.rarity.ordinal();
            offer.costRarity = rung == 0 ? FishRarity.COMMON : FishRarity.values()[rung - 1];
            offer.costCount = rung == 0 ? 1 : FishermanConstants.SURVEY_COST;

            offers.add(offer);
        }

        offers.sort(Comparator
                .comparingInt((SurveyOffer offer) -> offer.spec.rarity.ordinal())
                .thenComparing(offer -> offer.spec.getDisplayName()));

        return offers;
    }

    protected void buySurvey(SurveyOffer offer) {
        if (!FishCurrency.spend(offer.costRarity, offer.costCount)) {
            dialog.getTextPanel().addPara("\"Hold's short. Come back with the fish.\"",
                    Misc.getNegativeHighlightColor());
            showSurvey();
            return;
        }

        FishLog.unlockLocationData(offer.spec.id);

        dialog.getTextPanel().addPara("The Fisherman marks your charts: "
                        + offer.spec.getDisplayName() + "'s waters are on your map now.",
                Misc.getPositiveHighlightColor());

        showSurvey();
    }

    //---------------------------------------------------------------- outfitter

    /** The shop the player already has, rebuilt inside this same dialog frame. */
    protected void openOutfitter() {
        FishShopDialog shop = new FishShopDialog();
        dialog.setPlugin(shop);
        shop.init(dialog);
    }

    //---------------------------------------------------------------- selling

    protected void showSell() {
        dialog.getOptionPanel().clearOptions();

        List<StackValue> held = readAllFish();
        boolean any = false;
        int listed = 0;

        for (FishRarity rarity : FishRarity.values()) {
            int upTo = 0;
            float value = 0f;

            for (StackValue stack : held) {
                if (stack.rarity == null || stack.rarity.ordinal() > rarity.ordinal()) continue;

                upTo += stack.count;
                value += stack.value;
            }

            //a rung is only worth a row if it takes more than the rung below it did
            if (upTo <= listed) continue;
            listed = upTo;
            any = true;

            dialog.getOptionPanel().addOption("Sell everything up to "
                            + Misc.ucFirst(rarity.name().toLowerCase())
                            + " (" + upTo + " fish, " + Misc.getDGSCredits(value) + ")",
                    rarity, rarity.color, null);
        }

        if (!any) {
            dialog.getTextPanel().addPara("\"Empty hold. I know the feeling.\"",
                    Misc.getGrayColor());
        } else {
            dialog.getTextPanel().addPara("\"Market rate, no haggling. The market cannot hear"
                    + " you out here.\"");
            dialog.getOptionPanel().addOption("Pick specimens to sell", Option.SELL_PICK);
        }

        dialog.getOptionPanel().addOption("Back", Option.MAIN);
    }

    /** One stack's worth of fish: how many, their shared rarity, what they are worth together. */
    protected static class StackValue {
        SpecialItemData data;
        int count;
        FishRarity rarity;
        float value;
    }

    /** Every fish aboard, stack by stack - bundles valued by their contents. */
    protected List<StackValue> readAllFish() {
        List<StackValue> out = new ArrayList<>();

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            StackValue held = new StackValue();
            held.data = data;

            if (FishItems.FISH.equals(data.getId())) {
                FishCatch entry = FishCatch.decode(data.getData());
                if (entry == null || entry.getSpec() == null) continue;

                held.count = (int) stack.getSize();
                held.rarity = entry.getSpec().rarity;
                held.value = entry.getValue() * held.count;
            } else if (FishItems.BUNDLE.equals(data.getId())) {
                List<FishCatch> contents = FishItems.decodeBundle(data.getData());
                if (contents.isEmpty()) continue;

                FishRarity worst = null;
                for (FishCatch entry : contents) {
                    if (entry.getSpec() == null) continue;

                    held.count++;
                    held.value += entry.getValue();

                    //a crate sells as its rarest content for the up-to cut, so a mixed crate is
                    //never quietly sold below what is in it
                    if (worst == null
                            || entry.getSpec().rarity.ordinal() > worst.ordinal()) {
                        worst = entry.getSpec().rarity;
                    }
                }
                held.rarity = worst;
            } else {
                continue;
            }

            if (held.count > 0) out.add(held);
        }

        return out;
    }

    /** Everything at or below the rarity goes, for the market rate of each specimen. */
    protected void sellUpTo(FishRarity cap) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        int sold = 0;
        float credits = 0f;

        for (StackValue held : readAllFish()) {
            if (held.rarity == null || held.rarity.ordinal() > cap.ordinal()) continue;

            //a bundle is one item however many swim in it
            cargo.removeItems(CargoItemType.SPECIAL, held.data,
                    FishItems.BUNDLE.equals(held.data.getId()) ? 1 : held.count);

            sold += held.count;
            credits += held.value;
        }

        finishSale(sold, credits);
        showSell();
    }

    /** The vanilla cargo picker over a copy of the hold that only carries fish. */
    protected void showSellPicker() {
        CargoAPI offer = Global.getFactory().createCargo(true);

        for (StackValue held : readAllFish()) {
            offer.addSpecial(held.data,
                    FishItems.BUNDLE.equals(held.data.getId()) ? 1 : held.count);
        }

        dialog.showCargoPickerDialog("Select specimens to sell", "Sell", "Never mind",
                false, 330f, offer, new CargoPickerListener() {

                    @Override
                    public void pickedCargo(CargoAPI picked) {
                        sellPicked(picked);
                    }

                    @Override
                    public void cancelledCargoSelection() {
                    }

                    @Override
                    public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo,
                                                  CargoStackAPI pickedUp,
                                                  boolean pickedUpFromSource, CargoAPI combined) {
                        float total = valueOf(combined);

                        panel.setParaFontOrbitron();
                        panel.addPara(FishermanConstants.FLEET_NAME, Misc.getBasePlayerColor(), 0f);
                        panel.setParaFontDefault();

                        panel.addPara("Sold at market price - what each specimen would fetch"
                                + " from a buyer who wanted it.", Misc.getGrayColor(), 10f);

                        panel.addPara("Total: %s", 10f, Misc.getHighlightColor(),
                                Misc.getDGSCredits(total));
                    }
                });
    }

    protected void sellPicked(CargoAPI picked) {
        if (picked == null) return;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        int sold = 0;
        float credits = 0f;

        for (CargoStackAPI stack : picked.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            float value = valueOf(stack);
            if (value <= 0f) continue;

            cargo.removeItems(CargoItemType.SPECIAL, data, stack.getSize());

            credits += value;
            sold += FishItems.BUNDLE.equals(data.getId())
                    ? FishItems.decodeBundle(data.getData()).size() : (int) stack.getSize();
        }

        finishSale(sold, credits);
        showSell();
    }

    protected float valueOf(CargoAPI cargo) {
        float total = 0f;

        for (CargoStackAPI stack : cargo.getStacksCopy()) total += valueOf(stack);

        return total;
    }

    protected float valueOf(CargoStackAPI stack) {
        SpecialItemData data = stack.getSpecialDataIfSpecial();
        if (data == null) return 0f;

        if (FishItems.FISH.equals(data.getId())) {
            FishCatch entry = FishCatch.decode(data.getData());

            return entry == null ? 0f : entry.getValue() * stack.getSize();
        }

        if (FishItems.BUNDLE.equals(data.getId())) {
            float total = 0f;
            for (FishCatch entry : FishItems.decodeBundle(data.getData())) {
                total += entry.getValue();
            }

            return total * stack.getSize();
        }

        return 0f;
    }

    protected void finishSale(int sold, float credits) {
        if (sold <= 0) return;

        Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);

        dialog.getTextPanel().addPara("Sold " + sold
                        + (sold == 1 ? " specimen" : " specimens") + " for %s.",
                Misc.getPositiveHighlightColor(), Misc.getHighlightColor(),
                Misc.getDGSCredits(credits));
    }

    //---------------------------------------------------------------- rumors

    protected void askRumor() {
        dialog.getOptionPanel().clearOptions();

        if (!FishRumors.isAvailable()) {
            FishRumors.Saved running = FishRumors.getActive();

            if (running != null) {
                dialog.getTextPanel().addPara("\"Told you what I know already. "
                        + FishRumors.describe(running) + "\"", Misc.getGrayColor());
            } else {
                dialog.getTextPanel().addPara("\"Nothing on the wind this month. Ask me again"
                        + " when the season turns.\"", Misc.getGrayColor());
            }

            dialog.getOptionPanel().addOption("Back", Option.MAIN);
            return;
        }

        FishRumors.Saved rumor = FishRumors.create();

        if (rumor == null) {
            dialog.getTextPanel().addPara("\"Nothing on the wind. Strange days.\"",
                    Misc.getGrayColor());
        } else {
            dialog.getTextPanel().addPara("The Fisherman leans in. \""
                    + FishRumors.describe(rumor) + "\"");
            dialog.getTextPanel().addPara("An intel note marks the system.",
                    Misc.getGrayColor());
        }

        dialog.getOptionPanel().addOption("Back", Option.MAIN);
    }

    //---------------------------------------------------------------- plumbing

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
