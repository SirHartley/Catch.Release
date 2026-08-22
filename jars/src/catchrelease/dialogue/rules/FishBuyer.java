package catchrelease.dialogue.rules;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomDialogDelegate.CustomDialogCallback;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FishBuyer {

    protected static class Stack {

        SpecialItemData data;
        int items;
        int count;
        FishRarity rarity;
        float value;
        boolean wanted;
    }

    protected static final class SaleEntry {

        final SpecialItemData data;
        final int items;
        final int count;
        final float value;
        final List<FishCatch> sell;
        final List<FishCatch> keep;

        SaleEntry(Stack held) {
            data = held.data;
            items = held.items;
            count = held.count;
            value = held.value;
            sell = null;
            keep = null;
        }

        SaleEntry(Stack held, List<FishCatch> sell, List<FishCatch> keep) {
            this.data = held.data;
            this.items = held.items;
            this.sell = List.copyOf(sell);
            this.keep = List.copyOf(keep);

            float each = 0f;
            for (FishCatch entry : sell) each += entry.getValue();

            count = sell.size() * items;
            value = each * items;
        }
    }

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

    protected static class DescriptionSpecies {

        String name;
        int count;
        FishRarity rarity;
    }

    protected static final class PickerPackingSession {

        protected static final float BUTTON_WIDTH = 250f;
        protected static final float BUTTON_HEIGHT = 24f;

        protected final CargoAPI offer;
        protected boolean packed;

        PickerPackingSession(CargoAPI offer) {
            this.offer = offer;
        }

        void addButton(TooltipMakerAPI panel, CargoAPI selected) {
            if (packed || panel == null) return;

            PackButton plugin = new PackButton(this, selected);
            CustomPanelAPI custom = Global.getSettings().createCustom(
                    BUTTON_WIDTH, BUTTON_HEIGHT + 10f, plugin);
            plugin.panel = custom;
            TooltipMakerAPI element = custom.createUIElement(
                    BUTTON_WIDTH, BUTTON_HEIGHT + 10f, false);
            plugin.button = element.addButton("Pack into crates", plugin.buttonId,
                    BUTTON_WIDTH, BUTTON_HEIGHT, 10f);
            custom.addUIElement(element).inTL(0f, 0f);
            panel.addCustom(custom, 0f);
        }

        boolean pack(CargoAPI selected) {
            if (packed) return false;
            packed = true;

            // Selection lives in a second cargo object. Put it back before rebuilding the source so nothing remains stranded under the old loose-item identity.
            if (selected != null && !selected.isEmpty()) {
                offer.addAll(selected);
                selected.clear();
            }

            FishItems.packIntoCrates(offer);
            FishItems.packIntoCrates(Global.getSector().getPlayerFleet().getCargo());
            offer.sort();
            return true;
        }
    }

    protected static final class PackButton extends BaseCustomUIPanelPlugin {

        protected final Object buttonId = new Object();
        protected final PickerPackingSession session;
        protected final CargoAPI selected;

        protected CustomPanelAPI panel;
        protected ButtonAPI button;

        PackButton(PickerPackingSession session, CargoAPI selected) {
            this.session = session;
            this.selected = selected;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (buttonId != this.buttonId || !session.pack(selected)) return;

            if (button != null) button.setEnabled(false);
            PickerCargoRefresh.refreshFrom(panel);
        }
    }

    protected static final class PickerCargoRefresh {

        protected static final int MAX_PARENT_DEPTH = 16;

        static void refreshFrom(CustomPanelAPI panel) {
            Object current = panel;

            for (int depth = 0; current != null && depth < MAX_PARENT_DEPTH; depth++) {
                if (invokeRefresh(current)) return;

                for (ReflectionUtils.ReflectedField field : ReflectionUtils.getFieldsMatching(
                        current, null, null, null, null, false)) {
                    try {
                        Object value = field.get(current);
                        if (value != null && invokeRefresh(value)) return;
                    } catch (Throwable ignored) {
                        // One inaccessible or invalid field does not invalidate the capability crawl.
                    }
                }

                try {
                    current = ReflectionUtils.invokeIfExists(current, "getParent");
                } catch (Throwable ignored) {
                    return;
                }
            }
        }

        protected static boolean invokeRefresh(Object candidate) {
            try {
                List<ReflectionUtils.ReflectedMethod> methods = ReflectionUtils.getMethodsMatching(
                        candidate, "updateCargoViews", null, 0, null);
                if (methods.size() != 1) return false;

                methods.get(0).invoke(candidate);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    protected static final class ContainerSnapshot {

        final String id;
        final List<FishCatch> contents;
        final int items;

        ContainerSnapshot(String id, List<FishCatch> contents, int items) {
            this.id = id;
            this.contents = List.copyOf(contents);
            this.items = items;
        }
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

    public static boolean hasAnything() {
        return !read().isEmpty();
    }

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

                    // a crate sells as its rarest content, so a mixed one is never quietly sold below what is in it
                    if (worst == null || entry.getSpec().rarity.rank > worst.rank) {
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

    public static boolean show(final InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        final List<ContainerSnapshot> boxed =
                snapshotContainers(Global.getSector().getPlayerFleet().getCargo());

        FishItems.unbox(Global.getSector().getPlayerFleet().getCargo());

        CargoAPI offer = Global.getFactory().createCargo(true);

        for (Stack held : read()) {
            offer.addSpecial(held.data, FishItems.isContainer(held.data) ? 1 : held.count);
        }

        final PickerPackingSession packing = new PickerPackingSession(offer);

        dialog.showCargoPickerDialog("Select specimens to sell", "Sell", "Never mind",
                false, 330f, offer, new CargoPickerListener() {
                    @Override
                    public void pickedCargo(CargoAPI picked) {
                        sellPicked(dialog, picked);
                        restoreContainers(boxed);
                    }

                    @Override
                    public void cancelledCargoSelection() {
                        restoreContainers(boxed);
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

                        packing.addButton(panel, cargo);
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

    protected static List<ContainerSnapshot> snapshotContainers(CargoAPI cargo) {
        List<ContainerSnapshot> out = new ArrayList<>();
        if (cargo == null) return out;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isContainer(data)) continue;

            out.add(new ContainerSnapshot(data.getId(),
                    FishItems.decodeBundle(data.getData()), (int) stack.getSize()));
        }

        return out;
    }

    protected static void restoreContainers(List<ContainerSnapshot> boxed) {
        if (Global.getSector().getPlayerFleet() == null) return;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        FishItems.unbox(cargo);
        if (boxed == null || boxed.isEmpty()) return;

        Map<String, Integer> loose = new HashMap<>();
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null || !FishItems.FISH.equals(data.getId())) continue;

            loose.merge(data.getData(), (int) stack.getSize(), Integer::sum);
        }

        for (ContainerSnapshot box : boxed) {
            for (int i = 0; i < box.items; i++) {
                List<FishCatch> reclaimed = new ArrayList<>();

                for (FishCatch fish : box.contents) {
                    String key = fish.encode();
                    Integer held = loose.get(key);
                    if (held == null || held <= 0) continue;

                    loose.put(key, held - 1);
                    reclaimed.add(fish);
                }

                if (reclaimed.isEmpty()) continue;

                for (FishCatch fish : reclaimed) {
                    cargo.removeItems(CargoItemType.SPECIAL, FishItems.toItem(fish), 1);
                }

                cargo.addSpecial(FishItems.repack(box.id, reclaimed), 1);
            }
        }
    }

    public static boolean sellUpTo(InteractionDialogAPI dialog, String rarityName) {
        FishRarity cap = CatchReleaseCMD.parseRarity(rarityName);
        if (cap == null) return false;

        SalePreview preview = previewUpTo(cap);
        if (preview.count <= 0) return false;

        showBulkSaleConfirm(dialog, cap, preview);
        return true;
    }

    public static int countUpTo(FishRarity cap) {
        return previewUpTo(cap).count;
    }

    public static float valueUpTo(FishRarity cap) {
        return previewUpTo(cap).value;
    }

    public static String describeUpTo(FishRarity cap) {
        Map<String, DescriptionSpecies> counts = describeSpeciesUpTo(cap);

        if (counts.isEmpty()) return "No matching unmarked fish.";

        StringBuilder description = new StringBuilder("Will sell:");
        for (DescriptionSpecies entry : counts.values()) {
            description.append("\n").append(entry.count).append(" x ").append(entry.name);
        }

        return description.toString();
    }

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

    protected static Map<String, DescriptionSpecies> describeSpeciesUpTo(FishRarity cap) {
        Map<String, DescriptionSpecies> counts = new LinkedHashMap<>();

        for (SaleEntry held : previewUpTo(cap).entries) {
            if (held.sell != null) {
                for (FishCatch entry : held.sell) {
                    addDescriptionCount(counts, entry, held.items);
                }
            } else if (FishItems.isContainer(held.data)) {
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

    protected static SalePreview previewUpTo(FishRarity cap) {
        List<SaleEntry> entries = new ArrayList<>();
        int count = 0;
        float value = 0f;
        StringBuilder fingerprint = new StringBuilder();

        for (Stack held : read()) {
            SaleEntry entry;

            if (FishItems.isContainer(held.data)) {
                List<FishCatch> sell = new ArrayList<>();
                List<FishCatch> keep = new ArrayList<>();

                for (FishCatch fish : FishItems.decodeBundle(held.data.getData())) {
                    boolean eligible = fish.getSpec() != null
                            && !ShopMarks.isWanted(fish)
                            && fish.getSpec().rarity.rank <= cap.rank;

                    (eligible ? sell : keep).add(fish);
                }

                if (sell.isEmpty()) continue;

                entry = keep.isEmpty() ? new SaleEntry(held) : new SaleEntry(held, sell, keep);
            } else {
                if (held.wanted) continue;
                if (held.rarity == null || held.rarity.rank > cap.rank) continue;

                entry = new SaleEntry(held);
            }

            entries.add(entry);
            count += entry.count;
            value += entry.value;
            appendFingerprint(fingerprint, entry);
        }

        return new SalePreview(entries, count, value, fingerprint.toString());
    }

    protected static void appendFingerprint(StringBuilder out, SaleEntry entry) {
        String id = entry.data.getId();
        String data = entry.data.getData();
        out.append(id == null ? -1 : id.length()).append(':').append(id)
                .append(data == null ? -1 : data.length()).append(':').append(data)
                .append(':').append(entry.items)
                .append(':').append(entry.count).append(':').append(entry.value).append(';');
    }

    protected static void showBulkSaleConfirm(final InteractionDialogAPI dialog, final FishRarity cap,
                                              final SalePreview expected) {
        if (dialog == null) return;

        dialog.showCustomDialog(360f, 100f, new BaseCustomDialogDelegate() {
            @Override
            public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
                TooltipMakerAPI text = panel.createUIElement(360f, 100f, false);
                String credits = Misc.getDGSCredits(expected.value) + " credits";

                // Vanilla confirmation prompts explicitly opt into the large Insignia paragraph face; a bare custom UI element otherwise reads like small tooltip copy.
                text.setParaInsigniaLarge();
                text.addPara("Sell " + expected.count + " fish for %s?", 0f,
                        Misc.getHighlightColor(), credits);
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

                    // The hold (or a protection mark) changed under the prompt. Show the player the new exact sale rather than silently spending a different set of fish.
                    reopenBulkSaleConfirm(dialog, cap, current);
                    return;
                }

                execute(dialog, current);
            }
        });
    }

    protected static void reopenBulkSaleConfirm(InteractionDialogAPI dialog, FishRarity cap,
                                                SalePreview preview) {
        Global.getSector().addTransientScript(new ReopenBulkSaleConfirm(dialog, cap, preview));
    }

    protected static void execute(InteractionDialogAPI dialog, SalePreview preview) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        for (SaleEntry entry : preview.entries) {
            cargo.removeItems(CargoItemType.SPECIAL, entry.data, entry.items);

            if (entry.keep != null) {
                SpecialItemData rest = FishItems.repack(entry.data.getId(), entry.keep);
                for (int i = 0; i < entry.items; i++) cargo.addSpecial(rest, 1);
            }
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
