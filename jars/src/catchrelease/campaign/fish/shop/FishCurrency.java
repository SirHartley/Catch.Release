package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fish as money: shop prices are in specimens of a rarity, counted/spent by rarity rather than
 * species. Bundles count as their contents. Spending always takes the worst specimens first.
 */
public class FishCurrency {

    /** How many of each rarity are aboard, bundles included. */
    public static Map<FishRarity, Integer> count() {
        Map<FishRarity, Integer> counts = new EnumMap<>(FishRarity.class);
        for (FishRarity rarity : FishRarity.values()) counts.put(rarity, 0);

        CargoAPI cargo = getCargo();
        if (cargo == null) return counts;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            for (FishCatch entry : read(stack)) {
                FishRarity rarity = getRarity(entry);
                if (rarity == null) continue;

                counts.put(rarity, counts.get(rarity) + 1);
            }
        }

        return counts;
    }

    public static int count(FishRarity rarity) {
        Integer held = count().get(rarity);

        return held == null ? 0 : held;
    }

    /**
     * How many aboard could go towards a requirement. For an all-of-one-species ask this is the
     * best single species' count, since that is the most the requirement could actually take.
     */
    public static int count(FishRequirement req) {
        if (req == null) return Integer.MAX_VALUE;

        CargoAPI cargo = getCargo();
        if (cargo == null) return 0;

        int total = 0;
        Map<String, Integer> bySpecies = new HashMap<>();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            for (FishCatch entry : read(stack)) {
                if (!req.matches(entry)) continue;

                total++;
                bySpecies.merge(entry.speciesId, 1, Integer::sum);
            }
        }

        if (!req.sameSpecies || req.speciesId != null) return total;

        int best = 0;
        for (Integer perSpecies : bySpecies.values()) best = Math.max(best, perSpecies);

        return best;
    }

    /**
     * Takes payment against a requirement, if it can be paid in full. An all-of-one-species ask is
     * paid from whichever species has the most matching aboard, so the ask never breaks into a
     * second species when one could cover it.
     *
     * @return false if the hold could not cover it, in which case nothing was taken
     */
    public static boolean spend(FishRequirement req) {
        if (req == null) return true;
        if (count(req) < req.count) return false;

        CargoAPI cargo = getCargo();
        if (cargo == null) return false;

        String species = req.speciesId;
        if (req.sameSpecies && species == null) species = pickBestSpecies(req);

        final String chosen = species;
        int left = spendMatching(cargo, entry -> req.matches(entry)
                && (chosen == null || chosen.equals(entry.speciesId)), req.count, false);
        if (left > 0) left = spendMatching(cargo, entry -> req.matches(entry)
                && (chosen == null || chosen.equals(entry.speciesId)), left, true);

        return left <= 0;
    }

    /** The species with the most matching specimens aboard, for an all-of-one-species ask. */
    protected static String pickBestSpecies(FishRequirement req) {
        CargoAPI cargo = getCargo();
        if (cargo == null) return null;

        Map<String, Integer> bySpecies = new HashMap<>();
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            for (FishCatch entry : read(stack)) {
                if (req.matches(entry)) bySpecies.merge(entry.speciesId, 1, Integer::sum);
            }
        }

        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> candidate : bySpecies.entrySet()) {
            if (candidate.getValue() > bestCount) {
                best = candidate.getKey();
                bestCount = candidate.getValue();
            }
        }

        return best;
    }

    /**
     * Removes matching specimens, loose stacks or bundles as asked. A bundle that is partly taken
     * from is put back with what is left in it, since a bundle's contents are its identity.
     */
    protected static int spendMatching(CargoAPI cargo, java.util.function.Predicate<FishCatch> matches,
                                       int amount, boolean bundles) {

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (amount <= 0) break;

            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            boolean isBundle = FishItems.BUNDLE.equals(data.getId());
            if (isBundle != bundles) continue;

            if (!isBundle) {
                FishCatch entry = FishCatch.decode(data.getData());
                if (entry == null || !matches.test(entry)) continue;

                int take = (int) Math.min(amount, stack.getSize());
                cargo.removeItems(CargoItemType.SPECIAL, data, take);
                amount -= take;
                continue;
            }

            List<FishCatch> contents = FishItems.decodeBundle(data.getData());

            List<FishCatch> kept = new ArrayList<>();
            for (FishCatch entry : contents) {
                if (amount > 0 && matches.test(entry)) {
                    amount--;
                } else {
                    kept.add(entry);
                }
            }

            if (kept.size() == contents.size()) continue;

            cargo.removeItems(CargoItemType.SPECIAL, data, 1);
            if (!kept.isEmpty()) cargo.addSpecial(FishItems.toBundle(kept), 1);
        }

        return amount;
    }

    /**
     * Takes payment, if it can be paid in full.
     *
     * @return false if there were not enough aboard, in which case nothing was taken
     */
    public static boolean spend(FishRarity rarity, int amount) {
        if (rarity == null || amount <= 0) return true;
        if (count(rarity) < amount) return false;

        CargoAPI cargo = getCargo();
        if (cargo == null) return false;

        int left = amount;

        //loose specimens first, so a bundle is only broken into when it has to be
        left = spendFromStacks(cargo, rarity, left, false);
        if (left > 0) left = spendFromStacks(cargo, rarity, left, true);

        return left <= 0;
    }

    /**
     * @param bundles whether to take from crates rather than from loose specimens. A crate that is
     *                partly spent is put back with what is left in it, since a bundle's contents are
     *                its identity and a smaller one is a different item
     */
    protected static int spendFromStacks(CargoAPI cargo, FishRarity rarity, int amount, boolean bundles) {
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (amount <= 0) break;

            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            boolean isBundle = FishItems.BUNDLE.equals(data.getId());
            if (isBundle != bundles) continue;

            List<FishCatch> contents = read(stack);
            if (contents.isEmpty()) continue;
            if (getRarity(contents.get(0)) != rarity) continue;

            if (!isBundle) {
                int take = (int) Math.min(amount, stack.getSize());
                cargo.removeItems(CargoItemType.SPECIAL, data, take);
                amount -= take;
                continue;
            }

            //a crate: take what is needed out of it and put the rest back
            int take = Math.min(amount, contents.size());
            cargo.removeItems(CargoItemType.SPECIAL, data, 1);
            amount -= take;

            List<FishCatch> left = new ArrayList<>(contents.subList(take, contents.size()));
            if (!left.isEmpty()) {
                cargo.addSpecial(FishItems.toBundle(left), 1);
            }
        }

        return amount;
    }

    /**
     * Best specimen aboard matching a requirement (null if none), for buyers who pay on quality
     * rather than count. Judged by where it sits in its own species' size range, not raw weight.
     */
    public static FishCatch findBest(FishRequirement req) {
        CargoAPI cargo = getCargo();
        if (req == null || cargo == null) return null;

        FishCatch best = null;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            for (FishCatch entry : read(stack)) {
                if (!req.matches(entry)) continue;

                if (best == null || entry.getSizeFraction() > best.getSizeFraction()) best = entry;
            }
        }

        return best;
    }

    /** Everything a stack holds, whether it is one specimen or a crate of them. */
    protected static List<FishCatch> read(CargoStackAPI stack) {
        List<FishCatch> out = new ArrayList<>();

        SpecialItemData data = stack.getSpecialDataIfSpecial();
        if (data == null) return out;

        if (FishItems.BUNDLE.equals(data.getId())) {
            out.addAll(FishItems.decodeBundle(data.getData()));
            return out;
        }

        if (!FishItems.FISH.equals(data.getId())) return out;

        FishCatch entry = FishCatch.decode(data.getData());
        if (entry == null) return out;

        //a loose stack can be several of the same specimen
        for (int i = 0; i < (int) stack.getSize(); i++) out.add(entry);

        return out;
    }

    protected static FishRarity getRarity(FishCatch entry) {
        if (entry == null) return null;

        FishSpec spec = entry.getSpec();

        return spec == null ? null : spec.rarity;
    }

    protected static CargoAPI getCargo() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return null;

        return Global.getSector().getPlayerFleet().getCargo();
    }
}
