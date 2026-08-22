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

public class FishCurrency {

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

    public static int spendMatching(CargoAPI cargo, java.util.function.Predicate<FishCatch> matches,
                                    int amount, boolean bundles) {
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (amount <= 0) break;

            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            boolean isBundle = FishItems.isContainer(data);
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
            if (!kept.isEmpty()) cargo.addSpecial(FishItems.repack(data.getId(), kept), 1);
        }

        return amount;
    }

    public static boolean spend(FishRarity rarity, int amount) {
        if (rarity == null || amount <= 0) return true;
        if (count(rarity) < amount) return false;

        CargoAPI cargo = getCargo();
        if (cargo == null) return false;

        int left = amount;

        // loose specimens first, so a bundle is only broken into when it has to be
        left = spendFromStacks(cargo, rarity, left, false);
        if (left > 0) left = spendFromStacks(cargo, rarity, left, true);

        return left <= 0;
    }

    protected static int spendFromStacks(CargoAPI cargo, FishRarity rarity, int amount, boolean bundles) {
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (amount <= 0) break;

            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            boolean isBundle = FishItems.isContainer(data);
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

            int take = Math.min(amount, contents.size());
            cargo.removeItems(CargoItemType.SPECIAL, data, 1);
            amount -= take;

            List<FishCatch> left = new ArrayList<>(contents.subList(take, contents.size()));
            if (!left.isEmpty()) {
                cargo.addSpecial(FishItems.repack(data.getId(), left), 1);
            }
        }

        return amount;
    }

    public static int seizeAll() {
        CargoAPI cargo = getCargo();
        if (cargo == null) return 0;

        int taken = 0;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;

            List<FishCatch> contents = read(stack);
            if (contents.isEmpty()) continue;

            taken += FishItems.isContainer(data)
                    ? contents.size() * (int) stack.getSize() : contents.size();

            cargo.removeItems(CargoItemType.SPECIAL, data, stack.getSize());
        }

        return taken;
    }

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

    protected static List<FishCatch> read(CargoStackAPI stack) {
        List<FishCatch> out = new ArrayList<>();

        SpecialItemData data = stack.getSpecialDataIfSpecial();
        if (data == null) return out;

        if (FishItems.isContainer(data)) {
            out.addAll(FishItems.decodeBundle(data.getData()));
            return out;
        }

        if (!FishItems.FISH.equals(data.getId())) return out;

        FishCatch entry = FishCatch.decode(data.getData());
        if (entry == null) return out;

        // a loose stack can be several of the same specimen
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
