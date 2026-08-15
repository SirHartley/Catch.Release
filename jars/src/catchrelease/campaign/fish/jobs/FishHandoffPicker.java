package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A cargo picker that exposes and validates the individual specimens a fishing job can take. */
public final class FishHandoffPicker {

    public interface Listener {
        void picked(Selection selection);

        void cancelled();
    }

    /** Optional stricter provenance gate for requests that want one particular specimen. */
    public interface Eligibility {
        boolean accepts(FishCatch fish);
    }

    /** An exact, non-overlapping assignment of selected specimens to every outstanding ask. */
    public static final class Selection {
        protected final List<SpecialItemData> items;
        protected final List<FishCatch> contents;
        protected final FishCatch bestForFirstAsk;

        /** Whether the fish may still be boxed - an auto-pick reaches into crates and the pile,
         *  where the picker's fish were all loose by construction. */
        protected final boolean boxed;

        protected Selection(List<SpecialItemData> items, List<FishCatch> contents,
                            FishCatch bestForFirstAsk, boolean boxed) {
            this.items = items;
            this.contents = contents;
            this.bestForFirstAsk = bestForFirstAsk;
            this.boxed = boxed;
        }

        public FishCatch getBestForFirstAsk() {
            return bestForFirstAsk;
        }

        /** The exact specimens this hand-in takes, for a confirmation to read out. */
        public List<FishCatch> getContents() {
            return contents;
        }

        /** Removes exactly the selected specimens, after confirming they are still aboard. */
        public boolean spend() {
            if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) {
                return false;
            }

            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

            return boxed ? spendBoxed(cargo) : spendLoose(cargo);
        }

        protected boolean spendLoose(CargoAPI cargo) {
            Map<SpecialItemData, Integer> quantities = new LinkedHashMap<>();

            for (SpecialItemData item : items) quantities.merge(item, 1, Integer::sum);

            for (Map.Entry<SpecialItemData, Integer> entry : quantities.entrySet()) {
                if (cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, entry.getKey())
                        < entry.getValue()) return false;
            }

            for (Map.Entry<SpecialItemData, Integer> entry : quantities.entrySet()) {
                cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, entry.getKey(), entry.getValue());
            }

            return true;
        }

        /** By encoded identity, loose stacks before containers, all verified before any removal. */
        protected boolean spendBoxed(CargoAPI cargo) {
            Map<String, Integer> need = new LinkedHashMap<>();
            for (FishCatch fish : contents) need.merge(fish.encode(), 1, Integer::sum);

            Map<String, Integer> aboard = new LinkedHashMap<>();
            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                if (data == null) continue;

                if (FishItems.FISH.equals(data.getId())) {
                    aboard.merge(data.getData(), (int) stack.getSize(), Integer::sum);
                } else if (FishItems.isContainer(data)) {
                    for (FishCatch fish : FishItems.decodeBundle(data.getData())) {
                        aboard.merge(fish.encode(), (int) stack.getSize(), Integer::sum);
                    }
                }
            }

            for (Map.Entry<String, Integer> entry : need.entrySet()) {
                Integer have = aboard.get(entry.getKey());
                if (have == null || have < entry.getValue()) return false;
            }

            for (Map.Entry<String, Integer> entry : need.entrySet()) {
                final String key = entry.getKey();

                int left = FishCurrency.spendMatching(cargo,
                        fish -> key.equals(fish.encode()), entry.getValue(), false);
                if (left > 0) {
                    left = FishCurrency.spendMatching(cargo,
                            fish -> key.equals(fish.encode()), left, true);
                }

                if (left > 0) return false;
            }

            return true;
        }
    }

    protected static final class Candidate {
        protected final SpecialItemData item;
        protected final FishCatch fish;

        protected Candidate(SpecialItemData item, FishCatch fish) {
            this.item = item;
            this.fish = fish;
        }
    }

    private FishHandoffPicker() {
    }

    public static boolean show(InteractionDialogAPI dialog, String title,
                               final List<FishRequirement> asks, final Listener listener) {

        return show(dialog, title, asks, null, listener);
    }

    public static boolean show(InteractionDialogAPI dialog, String title,
                               final List<FishRequirement> asks, final Eligibility eligibility,
                               final Listener listener) {

        if (dialog == null || listener == null || asks == null || asks.isEmpty()) return false;
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return false;

        CargoAPI player = Global.getSector().getPlayerFleet().getCargo();
        FishItems.unbox(player);

        CargoAPI offer = Global.getFactory().createCargo(true);
        for (CargoStackAPI stack : player.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null || !FishItems.FISH.equals(data.getId())) continue;

            FishCatch fish = FishCatch.decode(data.getData());
            if (fish == null || !matchesAny(fish, asks)
                    || eligibility != null && !eligibility.accepts(fish)) continue;

            offer.addItems(CargoAPI.CargoItemType.SPECIAL, data, stack.getSize());
        }
        offer.sort();

        final int required = requiredCount(asks);

        dialog.showCargoPickerDialog(title, "Hand over", "Never mind", false, 350f, offer,
                new CargoPickerListener() {
                    @Override
                    public void pickedCargo(CargoAPI picked) {
                        Selection selection = match(picked, asks);

                        if (selection == null) {
                            listener.cancelled();
                        } else {
                            listener.picked(selection);
                        }
                    }

                    @Override
                    public void cancelledCargoSelection() {
                        listener.cancelled();
                    }

                    @Override
                    public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo,
                                                  CargoStackAPI pickedUp,
                                                  boolean pickedUpFromSource,
                                                  CargoAPI combined) {

                        int selected = countLoose(combined);
                        boolean ready = match(combined, asks) != null;

                        String request = describe(asks);
                        LabelAPI requiredLine = panel.addPara("Required: %s", 0f,
                                Misc.getHighlightColor(), request);
                        FishRequirement.highlight(requiredLine, asks, request);
                        panel.addPara("Selected: %s of %s specimens.", 10f,
                                ready ? Misc.getPositiveHighlightColor() : Misc.getGrayColor(),
                                String.valueOf(selected), String.valueOf(required));

                        if (!ready) {
                            panel.addPara("The selection does not yet cover the full request.",
                                    Misc.getGrayColor(), 5f);
                        }
                    }
                });

        return true;
    }

    /**
     * Picks the minimum hand-in without a picker: every matching specimen aboard - loose or
     * boxed - offered worst-first to the same slot assignment the manual picker validates
     * with, so the auto path never takes a better fish than the order needs and never takes
     * one it does not. Null when the hold cannot cover the asks.
     */
    public static Selection autoSelect(List<FishRequirement> asks, Eligibility eligibility) {
        if (asks == null || asks.isEmpty()) return null;
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return null;

        List<Candidate> candidates = new ArrayList<>();
        for (CargoStackAPI stack
                : Global.getSector().getPlayerFleet().getCargo().getStacksCopy()) {

            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            List<FishCatch> held = new ArrayList<>();
            if (FishItems.FISH.equals(data.getId())) {
                FishCatch fish = FishCatch.decode(data.getData());
                if (fish != null) held.add(fish);
            } else if (FishItems.isContainer(data)) {
                held.addAll(FishItems.decodeBundle(data.getData()));
            }

            for (FishCatch fish : held) {
                if (fish.getSpec() == null || !matchesAny(fish, asks)) continue;
                if (eligibility != null && !eligibility.accepts(fish)) continue;

                for (int i = 0; i < (int) stack.getSize(); i++) {
                    candidates.add(new Candidate(FishItems.toItem(fish), fish));
                }
            }
        }

        //worst-first, so the assignment reaches for the cheapest fish that still qualifies
        candidates.sort(java.util.Comparator.comparingDouble(c -> c.fish.getValue()));

        int required = requiredCount(asks);
        if (candidates.size() < required) return null;

        int[] slots = new int[required];
        int at = 0;
        for (int ask = 0; ask < asks.size(); ask++) {
            for (int i = 0; i < Math.max(0, asks.get(ask).count); i++) slots[at++] = ask;
        }

        int[] assignment = new int[required];
        boolean[] used = new boolean[candidates.size()];
        int[] assignedPerAsk = new int[asks.size()];
        String[] speciesPerAsk = new String[asks.size()];

        if (!assign(0, slots, assignment, used, assignedPerAsk, speciesPerAsk,
                candidates, asks)) return null;

        List<SpecialItemData> items = new ArrayList<>();
        List<FishCatch> contents = new ArrayList<>();
        FishCatch best = null;

        for (int slot = 0; slot < assignment.length; slot++) {
            Candidate candidate = candidates.get(assignment[slot]);
            items.add(candidate.item);
            contents.add(candidate.fish);

            if (slots[slot] == 0 && (best == null
                    || candidate.fish.getSizeFraction() > best.getSizeFraction())) {
                best = candidate.fish;
            }
        }

        return new Selection(items, contents, best, true);
    }

    protected static Selection match(CargoAPI picked, List<FishRequirement> asks) {
        List<Candidate> candidates = readLoose(picked);
        int required = requiredCount(asks);
        if (candidates.size() != required) return null;

        int[] slots = new int[required];
        int at = 0;
        for (int ask = 0; ask < asks.size(); ask++) {
            for (int i = 0; i < Math.max(0, asks.get(ask).count); i++) slots[at++] = ask;
        }

        int[] assignment = new int[required];
        boolean[] used = new boolean[candidates.size()];
        int[] assignedPerAsk = new int[asks.size()];
        String[] speciesPerAsk = new String[asks.size()];

        if (!assign(0, slots, assignment, used, assignedPerAsk, speciesPerAsk,
                candidates, asks)) return null;

        List<SpecialItemData> items = new ArrayList<>();
        List<FishCatch> contents = new ArrayList<>();
        FishCatch best = null;

        for (int slot = 0; slot < assignment.length; slot++) {
            Candidate candidate = candidates.get(assignment[slot]);
            items.add(candidate.item);
            contents.add(candidate.fish);

            if (slots[slot] == 0 && (best == null
                    || candidate.fish.getSizeFraction() > best.getSizeFraction())) {
                best = candidate.fish;
            }
        }

        return new Selection(items, contents, best, false);
    }

    protected static boolean assign(int slot, int[] slots, int[] assignment, boolean[] used,
                                    int[] assignedPerAsk, String[] speciesPerAsk,
                                    List<Candidate> candidates, List<FishRequirement> asks) {

        if (slot >= slots.length) return true;

        int askIndex = slots[slot];
        FishRequirement ask = asks.get(askIndex);

        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) continue;

            FishCatch fish = candidates.get(i).fish;
            if (!ask.matches(fish)) continue;

            String chosenSpecies = speciesPerAsk[askIndex];
            if (ask.sameSpecies && ask.speciesId == null && chosenSpecies != null
                    && !chosenSpecies.equals(fish.speciesId)) continue;

            boolean firstForAsk = assignedPerAsk[askIndex] == 0;
            if (firstForAsk && ask.sameSpecies && ask.speciesId == null) {
                speciesPerAsk[askIndex] = fish.speciesId;
            }

            used[i] = true;
            assignment[slot] = i;
            assignedPerAsk[askIndex]++;

            if (assign(slot + 1, slots, assignment, used, assignedPerAsk, speciesPerAsk,
                    candidates, asks)) return true;

            assignedPerAsk[askIndex]--;
            used[i] = false;
            if (firstForAsk) speciesPerAsk[askIndex] = null;
        }

        return false;
    }

    protected static List<Candidate> readLoose(CargoAPI cargo) {
        List<Candidate> out = new ArrayList<>();
        if (cargo == null) return out;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null || !FishItems.FISH.equals(data.getId())) continue;

            FishCatch fish = FishCatch.decode(data.getData());
            if (fish == null) continue;

            for (int i = 0; i < (int) stack.getSize(); i++) out.add(new Candidate(data, fish));
        }

        return out;
    }

    protected static int countLoose(CargoAPI cargo) {
        return readLoose(cargo).size();
    }

    protected static int requiredCount(List<FishRequirement> asks) {
        int total = 0;
        for (FishRequirement ask : asks) total += Math.max(0, ask.count);

        return total;
    }

    protected static boolean matchesAny(FishCatch fish, List<FishRequirement> asks) {
        for (FishRequirement ask : asks) if (ask.matches(fish)) return true;

        return false;
    }

    protected static String describe(List<FishRequirement> asks) {
        List<String> parts = new ArrayList<>();
        for (FishRequirement ask : asks) parts.add(ask.describe());

        if (parts.size() == 1) return parts.get(0);

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(i == parts.size() - 1 ? " and " : ", ");
            out.append(parts.get(i));
        }

        return out.toString();
    }
}
