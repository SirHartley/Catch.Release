package catchrelease.dialogue.rules;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.shop.ShopMarks;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

/**
 * Selling the catch at market price: the picker, the batch rungs, and the arithmetic.
 * <p>
 * Machinery, not dialogue - the sheet says who is buying and why, and calls in here for the part
 * that involves counting a hold and driving vanilla's cargo picker. Marked fish are stepped around
 * by every batch route: they are being saved for something, and a bulk sale should not eat them.
 */
public class FishBuyer {

    /** One stack's worth: how many, their shared rarity, what they are worth together. */
    protected static class Stack {
        SpecialItemData data;
        int count;
        FishRarity rarity;
        float value;
        boolean marked;
    }

    public static boolean hasAnything() {
        return !read().isEmpty();
    }

    /** Every fish aboard, stack by stack - containers valued by their contents. */
    protected static List<Stack> read() {
        List<Stack> out = new ArrayList<>();

        if (Global.getSector().getPlayerFleet() == null) return out;

        for (CargoStackAPI stack : Global.getSector().getPlayerFleet().getCargo().getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            Stack held = new Stack();
            held.data = data;

            if (FishItems.FISH.equals(data.getId())) {
                FishCatch entry = FishCatch.decode(data.getData());
                if (entry == null || entry.getSpec() == null) continue;

                held.count = (int) stack.getSize();
                held.rarity = entry.getSpec().rarity;
                held.value = entry.getValue() * held.count;
                held.marked = ShopMarks.isMarked(entry);
            } else if (FishItems.isContainer(data)) {
                FishRarity worst = null;

                for (FishCatch entry : FishItems.decodeBundle(data.getData())) {
                    if (entry.getSpec() == null) continue;

                    held.count++;
                    held.value += entry.getValue();

                    //a crate sells as its rarest content, so a mixed one is never quietly sold
                    //below what is in it
                    if (worst == null || entry.getSpec().rarity.ordinal() > worst.ordinal()) {
                        worst = entry.getSpec().rarity;
                    }

                    held.marked |= ShopMarks.isMarked(entry);
                }

                held.rarity = worst;
            } else {
                continue;
            }

            if (held.count > 0) out.add(held);
        }

        return out;
    }

    /** The vanilla cargo picker over a copy of the hold that only carries fish. */
    public static boolean show(final InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        FishItems.unbox(Global.getSector().getPlayerFleet().getCargo());

        CargoAPI offer = Global.getFactory().createCargo(true);

        for (Stack held : read()) {
            offer.addSpecial(held.data, FishItems.isContainer(held.data) ? 1 : held.count);
        }

        dialog.showCargoPickerDialog("Select specimens to sell", "Sell", "Never mind",
                false, 330f, offer, new CargoPickerListener() {

                    @Override
                    public void pickedCargo(CargoAPI picked) {
                        sellPicked(dialog, picked);
                    }

                    @Override
                    public void cancelledCargoSelection() {
                    }

                    @Override
                    public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo,
                                                  CargoStackAPI pickedUp,
                                                  boolean pickedUpFromSource, CargoAPI combined) {

                        panel.setParaFontOrbitron();
                        panel.addPara(dialog.getInteractionTarget().getName(),
                                Misc.getBasePlayerColor(), 0f);
                        panel.setParaFontDefault();

                        panel.addPara("Sold at market price - what each specimen would fetch from a"
                                + " buyer who wanted it.", Misc.getGrayColor(), 10f);

                        panel.addPara("Total: %s", 10f, Misc.getHighlightColor(),
                                Misc.getDGSCredits(valueOf(combined)));
                    }
                });

        return true;
    }

    protected static void sellPicked(InteractionDialogAPI dialog, CargoAPI picked) {
        if (picked == null) return;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        int sold = 0;
        float credits = 0f;

        for (CargoStackAPI stack : picked.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            float value = FishItems.getStackValue(stack);
            if (value <= 0f) continue;

            cargo.removeItems(CargoItemType.SPECIAL, data, stack.getSize());

            credits += value;
            sold += FishItems.isContainer(data)
                    ? FishItems.decodeBundle(data.getData()).size() : (int) stack.getSize();
        }

        finish(dialog, sold, credits);
    }

    /** Everything unmarked at or below the rung goes, for the market rate of each specimen. */
    public static boolean sellUpTo(InteractionDialogAPI dialog, String rarityName) {
        FishRarity cap = CatchReleaseCMD.parseRarity(rarityName);
        if (cap == null) return false;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        int sold = 0;
        float credits = 0f;

        for (Stack held : read()) {
            if (held.marked) continue;
            if (held.rarity == null || held.rarity.ordinal() > cap.ordinal()) continue;

            //a container is one item however many swim in it
            cargo.removeItems(CargoItemType.SPECIAL, held.data,
                    FishItems.isContainer(held.data) ? 1 : held.count);

            sold += held.count;
            credits += held.value;
        }

        finish(dialog, sold, credits);

        return sold > 0;
    }

    /** What a batch option at this rung would take, so the sheet can price its own row. */
    public static int countUpTo(FishRarity cap) {
        int total = 0;

        for (Stack held : read()) {
            if (held.marked) continue;
            if (held.rarity == null || held.rarity.ordinal() > cap.ordinal()) continue;

            total += held.count;
        }

        return total;
    }

    public static float valueUpTo(FishRarity cap) {
        float value = 0f;

        for (Stack held : read()) {
            if (held.marked) continue;
            if (held.rarity == null || held.rarity.ordinal() > cap.ordinal()) continue;

            value += held.value;
        }

        return value;
    }

    protected static void finish(InteractionDialogAPI dialog, int sold, float credits) {
        if (sold <= 0) return;

        Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);

        if (dialog == null) return;

        dialog.getTextPanel().addPara("Sold " + sold
                        + (sold == 1 ? " specimen" : " specimens") + " for %s.",
                Misc.getPositiveHighlightColor(), Misc.getHighlightColor(),
                Misc.getDGSCredits(credits));
    }

    protected static float valueOf(CargoAPI cargo) {
        float total = 0f;
        for (CargoStackAPI stack : cargo.getStacksCopy()) total += FishItems.getStackValue(stack);

        return total;
    }
}
