package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * What things cost: credits, and a catch that has to be brought in alongside them.
 * <p>
 * The catch is the interesting half, and it is rolled once per campaign rather than written in a
 * table: a seed drawn on first ask and kept in the save decides what every rung of every ladder
 * wants, so this game the drone bay takes crabs of a good grade and the next it takes three of one
 * species, barely holding together. The asks climb the same way whatever they are - more of them,
 * better, rarer, more specific - ending in a named species for the last rung of a ladder.
 * <p>
 * Still one rule rather than a price list, for the same reason as before: a list would have to be
 * maintained alongside every new upgrade, and this does not.
 */
public class ShopPricing {

    public static final String SEED_KEY = "$catchrelease_shop_seed";

    /** Credits for a ladder's first rung, and how steeply the rungs climb. */
    public static final int CREDITS_BASE = 2500;
    public static final float CREDITS_PER_LEVEL = 1.7f;

    /** Credits per tier of tackle - a module is one purchase, so it is priced as one. */
    public static final int TACKLE_CREDITS_PER_TIER = 4000;

    /**
     * Stats priced rungs above where their ladder stands. The one rule stays the rule - these are
     * not a price list, they are the few upgrades that change what a rig does rather than how well
     * it does it, and they enter the ladder already this many rungs up.
     */
    public static final int PREMIUM_TIER_BUMP = 2;
    protected static final Set<String> PREMIUM_STATS = Set.of(StatIds.SEARCHLIGHT_SLOW);

    /** Credits and the catch beside them. A null requirement is credits alone. */
    public static class Price {
        public final int credits;
        public final FishRequirement fish;

        public Price(int credits, FishRequirement fish) {
            this.credits = credits;
            this.fish = fish;
        }
    }

    /**
     * This campaign's seed, drawn once and kept in the save - the whole point is that a new game
     * wants different catches for the same gear.
     */
    public static long getSeed() {
        Object stored = Global.getSector().getMemoryWithoutUpdate().get(SEED_KEY);
        if (stored instanceof Long) return (Long) stored;

        long seed = new Random().nextLong();
        Global.getSector().getMemoryWithoutUpdate().set(SEED_KEY, seed);

        return seed;
    }

    /** The next rung of a stat's ladder. Null once it is at its ceiling. */
    public static Price getPrice(UpgradeStat stat) {
        if (stat == null || isMaxed(stat)) return null;

        int tier = Math.max(0, stat.level)
                + (PREMIUM_STATS.contains(stat.id) ? PREMIUM_TIER_BUMP : 0);
        boolean last = stat.maxLevel > 0 && stat.level == stat.maxLevel - 1;

        int credits = round100((int) (CREDITS_BASE * Math.pow(CREDITS_PER_LEVEL, tier)));

        return new Price(credits, generate(rngFor(stat.id, tier), tier, last));
    }

    /** A module's one price. Emptying the slot is free. */
    public static Price getPrice(Tackle tackle) {
        if (tackle == null || tackle == Tackle.NONE) return null;

        int tier = getTackleTier(tackle);

        return new Price(TACKLE_CREDITS_PER_TIER * (tier + 1),
                generate(rngFor("tackle_" + tackle.name(), tier), tier + 1, false));
    }

    /** Tackle is tiered by what it does, and the ones that change what can come up cost the most. */
    protected static int getTackleTier(Tackle tackle) {
        if (tackle.shipTackle) return 3;
        if (tackle.sonar || tackle.rarityBias > 1f || tackle.lockTime > 0f) return 2;

        //reaching under the fabric is not a better catch, it is a catch that was not on offer
        if (tackle.deepStrike) return 2;
        if (tackle.fanBeam) return 2;
        if (tackle.qualityBias > 0f || tackle.treasureChanceMult > 1f) return 1;

        return 0;
    }

    public static boolean isMaxed(UpgradeStat stat) {
        return stat != null && stat.maxLevel > 0 && stat.level >= stat.maxLevel;
    }

    protected static Random rngFor(String key, int tier) {
        return new Random(getSeed() ^ (key.hashCode() * 1000003L + tier * 7919L));
    }

    protected static int round100(int credits) {
        return Math.max(100, (credits / 100) * 100);
    }

    /**
     * The ask for one rung. Difficulty climbs by stacking axes rather than only by raising the
     * count: a type first, then floors on grade and rarity, then the hard asks - one species,
     * low coherence - and the last rung of a ladder names the species outright.
     */
    protected static FishRequirement generate(Random rng, int tier, boolean last) {
        FishRequirement req = new FishRequirement();

        req.count = 2 + Math.min(tier, 5) / 2;
        req.tag = pickTag(rng);

        if (last || tier >= 4) {
            req.speciesId = pickSpecies(rng);
            req.minGrade = FishGrade.FINE;
            req.count = Math.min(req.count, 3);

            //a named species is ask enough on its own
            if (req.speciesId != null) return req;
        }

        if (tier >= 1) {
            req.minGrade = rng.nextBoolean() ? FishGrade.FINE : FishGrade.AVERAGE;
        } else if (rng.nextBoolean()) {
            req.minGrade = FishGrade.AVERAGE;
        }

        if (tier >= 2) {
            req.minRarity = tier >= 3 ? FishRarity.RARE : FishRarity.UNCOMMON;

            //one of the hard asks, not both - both together is a lottery ticket, not a price
            if (rng.nextBoolean()) {
                req.sameSpecies = true;
            } else {
                req.lowCoherence = true;

                //low coherence is the rarity here; stacking a rarity floor on it overshoots
                if (tier < 3) req.minRarity = null;
            }
        }

        return req;
    }

    protected static String pickTag(Random rng) {
        String[] tags = {"fish", "fish", "crab", "mollusc"};

        return tags[rng.nextInt(tags.length)];
    }

    /**
     * A species worth naming: rare or better, not the special ones. Sorted before the pick so the
     * same roll lands on the same species whatever order the table loaded in.
     */
    protected static String pickSpecies(Random rng) {
        List<FishSpec> pool = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (spec.rarity.ordinal() < FishRarity.RARE.ordinal()) continue;
            if (spec.tags.contains("special")) continue;

            pool.add(spec);
        }

        if (pool.isEmpty()) return null;

        pool.sort(Comparator.comparing(spec -> spec.id));

        return pool.get(rng.nextInt(pool.size())).id;
    }
}
