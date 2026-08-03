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
import java.util.List;
import java.util.Map;

/**
 * Fish as money.
 * <p>
 * Everything in the shop is priced in specimens of a rarity, so what is in the hold has to be
 * counted and spent by rarity rather than by species. Bundles count too - a crate of forty is forty,
 * and making the player unpack one before spending it would be busywork rather than a decision.
 * <p>
 * Spending takes the worst first. A player paying a common price should not have their legendary
 * taken because it happened to be at the front of the hold.
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
