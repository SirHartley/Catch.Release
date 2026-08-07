package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishShopDialog;
import catchrelease.campaign.fish.tutorial.FishermanInterception;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.FishingIntroDialog;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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
        OUTFITTER,
        SELL,
        SELL_PICK,
        RUMOR,
        INTRO,
        LEAVE
    }

    protected InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        showFace();

        float drift = FishermanIdentity.getDrift(dialog.getInteractionTarget()
                .getContainingLocation());

        dialog.getTextPanel().addPara(FishermanIdentity.getGreeting(drift));

        //hailing a boat is a way into the introduction all by itself, and the only one that needs
        //nothing to have happened first
        FishingIntro.point();

        //a boat that moved itself across a system to get in front of somebody says so before
        //anything else, because the alternative is the player wondering whether that was a bug
        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI
                && FishermanInterception.hasIntercepted(
                        (CampaignFleetAPI) dialog.getInteractionTarget())
                && !FishingIntro.isAtLeast(FishingIntro.TAUGHT)) {

            dialog.getTextPanel().addPara("\"You were about to put a hand in that. You are not"
                    + " equipped for it, and I would rather not fish you out afterwards.\"",
                    Misc.getHighlightColor());
        }

        //what is wrong here, where anything is - said in the colour the mod already
        //reads a failing coherence in, so it lands as the same fact about the same water
        String wrong = FishermanIdentity.describe(drift);
        if (wrong != null) {
            dialog.getTextPanel().addPara(wrong,
                    catchrelease.campaign.fish.items.FishItemPlugin.getAberrationColor(drift));
        }

        showMain();
    }

    /**
     * The man rather than the hulls.
     * <p>
     * Every boat in the trade answers with the same face - that is the plot point, and it only
     * lands if the screen says it without comment. A fleet readout cannot say "them again"; a
     * portrait says it without a word, and says it identically on a boat four jumps from the last
     * one. See {@link FishermanIdentity}.
     */
    protected void showFace() {
        dialog.getVisualPanel().showPersonInfo(FishermanIdentity.get(), true);
    }

    protected void showMain() {
        dialog.getOptionPanel().clearOptions();

        dialog.getOptionPanel().addOption("Purchase survey data", Option.SURVEY);
        dialog.getOptionPanel().addOption("Access the outfitter", Option.OUTFITTER);
        dialog.getOptionPanel().addOption("Sell fish", Option.SELL);
        dialog.getOptionPanel().addOption("Ask about rumors", Option.RUMOR);

        //coloured while the introduction is still owed, which is the only time it matters
        dialog.getOptionPanel().addOption(FishingIntro.isAtLeast(FishingIntro.TAUGHT)
                        ? "Ask about the water" : "Ask what any of this is", Option.INTRO,
                FishingIntroDialog.hasBusiness() ? Misc.getHighlightColor() : null, null);

        dialog.getOptionPanel().addOption("Leave", Option.LEAVE);

        dialog.getOptionPanel().setShortcut(Option.LEAVE,
                org.lwjgl.input.Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        //the player's line goes into the log before anything answers it - the engine's own
        //echo, the same convention every vanilla dialog reads by
        if (optionData != null) dialog.addOptionSelectedText(optionData);

        if (!(optionData instanceof Option)) {
            if (optionData instanceof FishRarity) {
                sellUpTo((FishRarity) optionData);
                return;
            }
            return;
        }

        switch ((Option) optionData) {
            case SURVEY:
                openSurvey();
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
            case INTRO:
                openIntro();
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

    /**
     * The chart counter: its own panel in the outfitter's dress, handed the frame the same way.
     * Which shelf is behind the counter is {@link FishermanShelf}'s business, not this screen's.
     */
    protected void openSurvey() {
        if (FishermanShelf.getOffers(dialog.getInteractionTarget()).isEmpty()) {
            dialog.getOptionPanel().clearOptions();
            dialog.getTextPanel().addPara("\"Shelf's bare - what I had, you bought, and I don't"
                    + " chart new waters mid-trip.\"", Misc.getGrayColor());
            dialog.getOptionPanel().addOption("Back", Option.MAIN);
            return;
        }

        //options cleared before the swap - they'd stand under the panel otherwise, and the
        //hidden text panel drags them sideways with it
        dialog.getOptionPanel().clearOptions();

        FishermanSurveyDialog counter = new FishermanSurveyDialog(this::resume);
        dialog.setPlugin(counter);
        counter.init(dialog);
    }

    //---------------------------------------------------------------- the introduction

    /**
     * Being told what any of this is, in the same frame and handed back the same way as the shop.
     * <p>
     * A separate plugin rather than more states in this one: the shop sells things and the
     * introduction explains them, and mixing the two would put the tutorial's stages into the
     * middle of a shelf.
     */
    protected void openIntro() {
        dialog.getOptionPanel().clearOptions();

        FishingIntroDialog intro = new FishingIntroDialog(this::resume);

        dialog.setPlugin(intro);
        intro.init(dialog);
    }

    //---------------------------------------------------------------- outfitter

    /**
     * The shop the player already has, rebuilt inside this same dialog frame - and handed back
     * afterwards rather than closing on top of the conversation it was opened from.
     */
    protected void openOutfitter() {
        //options cleared before the swap - they'd stand under the shop otherwise, and the
        //hidden text panel drags them sideways with it
        dialog.getOptionPanel().clearOptions();

        FishShopDialog shop = new FishShopDialog(this::resume);

        dialog.setPlugin(shop);
        shop.init(dialog);
    }

    /**
     * Back to the boat, with the frame put back the way the shop found it.
     * <p>
     * Not {@link #init}: the greeting is a greeting, and hearing it again on the way out of the
     * outfitter would read as having walked up to them twice. The panels and the dim are restored by
     * hand because the shop hid and dimmed them and nothing reads back what they were - the dim is
     * the figure vanilla uses for its own comm screens, which is the screen this is.
     */
    protected void resume(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.setPlugin(this);
        dialog.setBackgroundDimAmount(FishermanConstants.DIALOG_DIM);

        dialog.showTextPanel();
        dialog.showVisualPanel();

        showFace();

        showMain();
    }

    //---------------------------------------------------------------- selling

    protected void showSell() {
        dialog.getOptionPanel().clearOptions();

        List<StackValue> held = readAllFish();

        boolean anyAtAll = !held.isEmpty();
        boolean anyMarked = false;
        for (StackValue stack : held) anyMarked |= stack.marked;

        if (!anyAtAll) {
            dialog.getTextPanel().addPara("\"Empty hold. I know the feeling.\"",
                    Misc.getGrayColor());
            dialog.getOptionPanel().addOption("Back", Option.MAIN);
            return;
        }

        dialog.getTextPanel().addPara("\"Market rate, no haggling. The market cannot hear"
                + " you out here.\"");

        //the door to the picker leads, in the one colour nothing else here wears
        dialog.getOptionPanel().addOption("Open the fish buyer - pick what sells",
                Option.SELL_PICK, Misc.getHighlightColor(), null);

        int listed = 0;

        for (FishRarity rarity : FishRarity.values()) {
            int upTo = 0;
            float value = 0f;

            //batch options leave the shopping list alone - marked fish are being saved for
            //something, and a bulk sale should not eat them
            for (StackValue stack : held) {
                if (stack.marked) continue;
                if (stack.rarity == null || stack.rarity.ordinal() > rarity.ordinal()) continue;

                upTo += stack.count;
                value += stack.value;
            }

            //a rung is only worth a row if it takes more than the rung below it did
            if (upTo <= listed) continue;
            listed = upTo;

            String rarityWord = Misc.ucFirst(rarity.name().toLowerCase());

            dialog.getOptionPanel().addOption("Sell all unmarked up to " + rarityWord
                    + " (" + upTo + " fish, " + Misc.getDGSCredits(value) + ")", rarity);

            //only the rarity's word in its colour, best-effort - a plain row still reads
            highlightOptionWord(rarity, rarityWord, rarity.color);
        }

        if (anyMarked) {
            dialog.getTextPanel().addPara("Marked fish stay aboard - the batch options leave"
                    + " the shopping list alone.", Misc.getGrayColor());
        }

        dialog.getOptionPanel().addOption("Back", Option.MAIN);
    }

    /**
     * Colours one word of an option's label, which the API cannot do - reached through the
     * option panel's button map instead, and quietly skipped the moment any link in that chain
     * is not where this build left it. A plain-coloured row is the graceful failure.
     */
    protected void highlightOptionWord(Object optionData, String word, java.awt.Color color) {
        try {
            Object panel = dialog.getOptionPanel();
            Object map = catchrelease.reflection.ReflectionUtils.invokeIfExists(
                    panel, "getButtonToItemMap");
            if (!(map instanceof Map)) return;

            for (Map.Entry<?, ?> pair : ((Map<?, ?>) map).entrySet()) {
                //the item wraps the option's data in one of its fields; identity is the match
                boolean ours = false;
                for (catchrelease.reflection.ReflectionUtils.ReflectedField field
                        : catchrelease.reflection.ReflectionUtils.getFieldsMatching(
                        pair.getValue().getClass(), null, null, null, null, false)) {

                    if (field.get(pair.getValue()) == optionData) {
                        ours = true;
                        break;
                    }
                }
                if (!ours) continue;

                highlightLabelIn(pair.getKey(), word, color, 0);
                return;
            }
        } catch (Throwable ignored) {
            //the row stays plain, which is what it was before this method existed
        }
    }

    /** Finds the label inside a button (or its renderer) and sets the highlight on it. */
    protected boolean highlightLabelIn(Object holder, String word, java.awt.Color color, int depth) {
        if (holder == null || depth > 2) return false;

        if (catchrelease.reflection.ReflectionUtils.hasMethodOfName(holder, "setHighlight")
                && catchrelease.reflection.ReflectionUtils.hasMethodOfName(holder, "setHighlightColors")) {

            catchrelease.reflection.ReflectionUtils.invoke(holder, "setHighlightColors",
                    (Object) new java.awt.Color[]{color});
            catchrelease.reflection.ReflectionUtils.invoke(holder, "setHighlight",
                    (Object) new String[]{word});
            return true;
        }

        for (catchrelease.reflection.ReflectionUtils.ReflectedField field
                : catchrelease.reflection.ReflectionUtils.getFieldsMatching(
                holder.getClass(), null, null, null, null, false)) {

            Object value = field.get(holder);
            if (value == null || value instanceof String || value instanceof Number) continue;

            if (highlightLabelIn(value, word, color, depth + 1)) return true;
        }

        return false;
    }

    /** One stack's worth of fish: how many, their shared rarity, what they are worth together. */
    protected static class StackValue {
        SpecialItemData data;
        int count;
        FishRarity rarity;
        float value;

        /** On the shopping list - the batch options step around it. */
        boolean marked;
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
                held.marked = catchrelease.campaign.fish.shop.ShopMarks.isMarked(entry);
            } else if (FishItems.isContainer(data)) {
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

                    //one marked fish marks the crate - a batch sale must not eat it unseen
                    held.marked |= catchrelease.campaign.fish.shop.ShopMarks.isMarked(entry);
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
            if (held.marked) continue;
            if (held.rarity == null || held.rarity.ordinal() > cap.ordinal()) continue;

            //a bundle is one item however many swim in it
            cargo.removeItems(CargoItemType.SPECIAL, held.data,
                    FishItems.isContainer(held.data) ? 1 : held.count);

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
                    FishItems.isContainer(held.data) ? 1 : held.count);
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
                        panel.addPara(dialog.getInteractionTarget().getName(),
                                Misc.getBasePlayerColor(), 0f);
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
            sold += FishItems.isContainer(data)
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

        if (FishItems.isContainer(data)) {
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
