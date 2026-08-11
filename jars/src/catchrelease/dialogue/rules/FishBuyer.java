package catchrelease.dialogue.rules;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.shop.ShopMarks;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomDialogDelegate.CustomDialogCallback;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Selling the catch at market price: the picker, the batch rungs, and the arithmetic.
 * <p>
 * Machinery, not dialogue - the sheet says who is buying and why, and calls in here for the part
 * that involves counting a hold and driving vanilla's cargo picker. Wanted fish are stepped around
 * by every batch route: whether a shop mark or an open errand put the yellow dot there, a bulk sale
 * should not eat them.
 */
public class FishBuyer {

    /** One stack's worth: how many, their shared rarity, what they are worth together. */
    protected static class Stack {
        SpecialItemData data;
        /** Number of special items represented by this cargo stack. */
        int items;
        int count;
        FishRarity rarity;
        float value;
        boolean wanted;
    }

    /**
     * One exact cargo removal in a bulk-sale preview. The item data and quantity are enough to
     * remove the same whole cargo stack later; containers are never opened or partly spent here.
     */
    protected static final class SaleEntry {
        final SpecialItemData data;
        final int items;
        final int count;
        final float value;

        SaleEntry(Stack held) {
            data = held.data;
            items = held.items;
            count = held.count;
            value = held.value;
        }
    }

    /**
     * An immutable snapshot of a bulk sale, including the exact whole stacks it will consume.
     * Its fingerprint lets the confirmation reject a hold that changed while the prompt was open.
     */
    protected static final class SalePreview {
        final List<SaleEntry> entries;
        final int count;
        final float value;
        final String fingerprint;

        SalePreview(List<SaleEntry> entries, int count, float value, String fingerprint) {
            this.entries = List.copyOf(entries);
            this.count = count;
            this.value = value;
            this.fingerprint = fingerprint;
        }

        boolean matches(SalePreview other) {
            return other != null && fingerprint.equals(other.fingerprint);
        }
    }

    /** One named species in a batch-sale preview, keyed by its stable data id. */
    protected static class DescriptionSpecies {
        String name;
        int count;
        FishRarity rarity;
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
            held.items = (int) stack.getSize();
            if (held.items <= 0) continue;

            if (FishItems.FISH.equals(data.getId())) {
                FishCatch entry = FishCatch.decode(data.getData());
                if (entry == null || entry.getSpec() == null) continue;

                held.count = held.items;
                held.rarity = entry.getSpec().rarity;
                held.value = entry.getValue() * held.count;
                held.wanted = ShopMarks.isWanted(entry);
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

                    held.wanted |= ShopMarks.isWanted(entry);
                }

                held.rarity = worst;
                held.count *= held.items;
                held.value *= held.items;
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

    /**
     * Opens a confirmation for everything unmarked at or below the rung. The actual removal is
     * deliberately delayed until confirmation, then verified against a fresh hold snapshot.
     */
    public static boolean sellUpTo(InteractionDialogAPI dialog, String rarityName) {
        FishRarity cap = CatchReleaseCMD.parseRarity(rarityName);
        if (cap == null) return false;

        SalePreview preview = previewUpTo(cap);
        if (preview.count <= 0) return false;

        showBulkSaleConfirm(dialog, cap, preview);
        return true;
    }

    /** What a batch option at this rung would take, so the sheet can price its own row. */
    public static int countUpTo(FishRarity cap) {
        return previewUpTo(cap).count;
    }

    public static float valueUpTo(FishRarity cap) {
        return previewUpTo(cap).value;
    }

    /**
     * The exact specimens the current batch-sale route would take, in hold order.
     * <p>
     * This deliberately walks the same preview as {@link #sellUpTo}, including every copy in an
     * identical-container stack. The old one-container assumption sold and described different
     * quantities whenever two identical crates stacked together in the cargo hold.
    */
    public static String describeUpTo(FishRarity cap) {
        Map<String, DescriptionSpecies> counts = describeSpeciesUpTo(cap);

        if (counts.isEmpty()) return "No matching unmarked fish.";

        StringBuilder description = new StringBuilder("Will sell:");
        for (DescriptionSpecies entry : counts.values()) {
            description.append("\n").append(entry.count).append(" x ").append(entry.name);
        }

        return description.toString();
    }

    /**
     * Adds the batch contents as real tooltip rows so every species name can carry its own rarity.
     * A plain option tooltip can only colour matched substrings, which makes overlapping names
     * ambiguous; one highlighted name per paragraph cannot colour the wrong span.
     */
    public static void addDescriptionTooltip(InteractionDialogAPI dialog, Object optionId,
                                             FishRarity cap) {
        if (dialog == null || dialog.getOptionPanel() == null) return;

        List<DescriptionSpecies> species = new ArrayList<>(describeSpeciesUpTo(cap).values());
        dialog.getOptionPanel().addOptionTooltipAppender(optionId,
                new OptionPanelAPI.OptionTooltipCreator() {
                    @Override
                    public void createTooltip(TooltipMakerAPI tooltip, boolean hadOtherText) {
                        tooltip.addPara("Will sell:", hadOtherText ? 10f : 0f);

                        for (DescriptionSpecies entry : species) {
                            tooltip.addPara(entry.count + " x %s", 3f, Misc.getTextColor(),
                                    entry.rarity.color, entry.name);
                        }
                    }
                });
    }

    /** The exact named contents behind both the plain description and the coloured tooltip. */
    protected static Map<String, DescriptionSpecies> describeSpeciesUpTo(FishRarity cap) {
        Map<String, DescriptionSpecies> counts = new LinkedHashMap<>();

        for (SaleEntry held : previewUpTo(cap).entries) {

            if (FishItems.isContainer(held.data)) {
                for (FishCatch entry : FishItems.decodeBundle(held.data.getData())) {
                    if (entry.getSpec() == null) continue;
                    addDescriptionCount(counts, entry, held.items);
                }
            } else {
                FishCatch entry = FishCatch.decode(held.data.getData());
                if (entry != null && entry.getSpec() != null) {
                    addDescriptionCount(counts, entry, held.count);
                }
            }
        }

        return counts;
    }

    /** Builds the one source of truth used by the label, tooltip, confirmation and sale. */
    protected static SalePreview previewUpTo(FishRarity cap) {
        List<SaleEntry> entries = new ArrayList<>();
        int count = 0;
        float value = 0f;
        StringBuilder fingerprint = new StringBuilder();

        for (Stack held : read()) {
            if (held.wanted) continue;
            if (held.rarity == null || held.rarity.ordinal() > cap.ordinal()) continue;

            SaleEntry entry = new SaleEntry(held);
            entries.add(entry);
            count += entry.count;
            value += entry.value;
            appendFingerprint(fingerprint, entry);
        }

        return new SalePreview(entries, count, value, fingerprint.toString());
    }

    /** Length prefixes make two distinct payloads unable to blur together in the snapshot key. */
    protected static void appendFingerprint(StringBuilder out, SaleEntry entry) {
        String id = entry.data.getId();
        String data = entry.data.getData();
        out.append(id == null ? -1 : id.length()).append(':').append(id)
                .append(data == null ? -1 : data.length()).append(':').append(data)
                .append(':').append(entry.items).append(';');
    }

    protected static void showBulkSaleConfirm(final InteractionDialogAPI dialog, final FishRarity cap,
                                              final SalePreview expected) {
        if (dialog == null) return;

        dialog.showCustomDialog(360f, 100f, new BaseCustomDialogDelegate() {
            @Override
            public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
                TooltipMakerAPI text = panel.createUIElement(360f, 100f, false);
                text.addPara("Sell " + expected.count + " fish for "
                        + Misc.getDGSCredits(expected.value) + " credits?", 0f);
                panel.addUIElement(text).inTL(0f, 0f);
            }

            @Override
            public boolean hasCancelButton() {
                return true;
            }

            @Override
            public String getConfirmText() {
                return "Sell";
            }

            @Override
            public String getCancelText() {
                return "Never mind";
            }

            @Override
            public void customDialogConfirm() {
                SalePreview current = previewUpTo(cap);
                if (!expected.matches(current)) {
                    if (current.count <= 0) {
                        dialog.getTextPanel().addPara("No matching unmarked fish remain.",
                                Misc.getNegativeHighlightColor());
                        return;
                    }

                    //The hold (or a protection mark) changed under the prompt. Show the player
                    //the new exact sale rather than silently spending a different set of fish.
                    reopenBulkSaleConfirm(dialog, cap, current);
                    return;
                }

                execute(dialog, current);
            }
        });
    }

    /**
     * A custom dialog is still installed while its confirm callback runs, so Starsector ignores a
     * second {@code showCustomDialog()} from that callback. RC8 invokes the callback before it
     * dismisses the current dialog, so queue the fresh prompt for the following campaign update.
     */
    protected static void reopenBulkSaleConfirm(InteractionDialogAPI dialog, FishRarity cap,
                                                SalePreview preview) {
        Global.getSector().addTransientScript(new ReopenBulkSaleConfirm(dialog, cap, preview));
    }

    protected static final class ReopenBulkSaleConfirm implements EveryFrameScript {
        private final InteractionDialogAPI dialog;
        private final FishRarity cap;
        private final SalePreview preview;
        private boolean done;

        ReopenBulkSaleConfirm(InteractionDialogAPI dialog, FishRarity cap, SalePreview preview) {
            this.dialog = dialog;
            this.cap = cap;
            this.preview = preview;
        }

        @Override
        public void advance(float amount) {
            if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() == dialog) {
                showBulkSaleConfirm(dialog, cap, preview);
            }
            done = true;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }
    }

    /** Performs only the already-confirmed, freshly recomputed transaction. */
    protected static void execute(InteractionDialogAPI dialog, SalePreview preview) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        for (SaleEntry entry : preview.entries) {
            cargo.removeItems(CargoItemType.SPECIAL, entry.data, entry.items);
        }

        finish(dialog, preview.count, preview.value);
    }

    protected static void addDescriptionCount(Map<String, DescriptionSpecies> counts,
                                              FishCatch fish, int count) {
        DescriptionSpecies listed = counts.get(fish.speciesId);
        if (listed == null) {
            listed = new DescriptionSpecies();
            listed.name = fish.getDisplayName();
            listed.rarity = fish.getSpec().rarity;
            counts.put(fish.speciesId, listed);
        }

        listed.count += count;
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
