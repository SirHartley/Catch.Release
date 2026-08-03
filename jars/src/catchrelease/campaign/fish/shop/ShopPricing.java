package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.memory.upgrades.UpgradeStat;

/**
 * What things cost, in fish.
 * <p>
 * One rule rather than a price list: the rarity a thing is paid in comes from how far up its own
 * ladder it is, and the number of them comes from how many have already been bought. A price list
 * would have to be maintained alongside every new upgrade; this does not.
 * <p>
 * Levels are paid in a rarity that steps up as the levels do, so the last level of anything costs
 * something you had to go looking for rather than a larger pile of the same thing you catch by
 * accident.
 */
public class ShopPricing {

    /** How many of the base rarity the first level costs. */
    public static final int BASE_COST = 3;

    /** Added per level already bought, so the fourth costs more than the first. */
    public static final int COST_PER_LEVEL = 2;

    /** How many levels are paid in one rarity before the price moves up a tier. */
    public static final int LEVELS_PER_TIER = 2;

    /** A tackle module is a one-off rather than a ladder, so it has a flat price. */
    public static final int TACKLE_COST = 6;

    /** What the next level of a stat is paid in. Null for a stat that is already at its ceiling. */
    public static FishRarity getRarity(UpgradeStat stat) {
        if (stat == null || isMaxed(stat)) return null;

        int tier = stat.level / LEVELS_PER_TIER;

        FishRarity[] ladder = FishRarity.values();

        return ladder[Math.min(tier, ladder.length - 1)];
    }

    public static int getCost(UpgradeStat stat) {
        if (stat == null || isMaxed(stat)) return 0;

        return BASE_COST + COST_PER_LEVEL * stat.level;
    }

    public static boolean isMaxed(UpgradeStat stat) {
        return stat != null && stat.maxLevel > 0 && stat.level >= stat.maxLevel;
    }

    /**
     * Tackle is priced by what it does rather than by a ladder, and the ones that change what can
     * come up out of a catch cost the most.
     */
    public static FishRarity getRarity(Tackle tackle) {
        if (tackle == null || tackle == Tackle.NONE) return null;

        if (tackle.shipTackle) return FishRarity.EPIC;
        if (tackle.sonar || tackle.rarityBias > 1f) return FishRarity.RARE;
        if (tackle.qualityBias > 0f || tackle.treasureChanceMult > 1f) return FishRarity.UNCOMMON;

        return FishRarity.COMMON;
    }

    public static int getCost(Tackle tackle) {
        return tackle == null || tackle == Tackle.NONE ? 0 : TACKLE_COST;
    }

    public static boolean canAfford(FishRarity rarity, int cost) {
        return rarity == null || FishCurrency.count(rarity) >= cost;
    }
}
