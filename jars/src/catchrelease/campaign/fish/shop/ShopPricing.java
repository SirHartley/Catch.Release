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


public class ShopPricing {

    public static final String SEED_KEY = "$catchrelease_shop_seed";


    public static final int CREDITS_BASE = 2500;
    public static final float CREDITS_PER_LEVEL = 1.7f;


    public static final int TACKLE_CREDITS_PER_TIER = 4000;


    public static final int PREMIUM_TIER_BUMP = 2;
    protected static final Set<String> PREMIUM_STATS = Set.of(StatIds.SEARCHLIGHT_SLOW);


    public static class Price {
        public final int credits;
        public final FishRequirement fish;

        public Price(int credits, FishRequirement fish) {
            this.credits = credits;
            this.fish = fish;
        }
    }


    public static long getSeed() {
        Object stored = Global.getSector().getMemoryWithoutUpdate().get(SEED_KEY);
        if (stored instanceof Long) return (Long) stored;

        long seed = new Random().nextLong();
        Global.getSector().getMemoryWithoutUpdate().set(SEED_KEY, seed);

        return seed;
    }


    public static Price getPrice(UpgradeStat stat) {
        if (stat == null || isMaxed(stat)) return null;

        return getPrice(stat, stat.level + 1);
    }


    public static Price getPrice(UpgradeStat stat, int targetLevel) {
        if (stat == null || targetLevel < 1 || targetLevel > stat.maxLevel) return null;

        int tier = targetLevel - 1
                + (PREMIUM_STATS.contains(stat.id) ? PREMIUM_TIER_BUMP : 0);
        boolean last = targetLevel == stat.maxLevel;

        int credits = round100((int) (CREDITS_BASE * Math.pow(CREDITS_PER_LEVEL, tier)));

        return new Price(credits, generate(rngFor(stat.id, tier), tier, last));
    }


    public static Price getPrice(Tackle tackle) {
        if (tackle == null || tackle == Tackle.NONE) return null;

        int tier = getTackleTier(tackle);

        return new Price(TACKLE_CREDITS_PER_TIER * (tier + 1),
                generate(rngFor("tackle_" + tackle.name(), tier), tier + 1, false));
    }


    protected static int getTackleTier(Tackle tackle) {
        if (tackle.breachCoupling) return 4;
        if (tackle.retrievesCharge) return 3;
        if (tackle.shipTackle) return 3;
        if (tackle.sonar || tackle.rarityBias > 1f || tackle.lockTime > 0f) return 2;

        if (tackle.deepStrike) return 2;
        if (tackle.fanBeam) return 2;
        if (tackle.qualityBias > 0f || tackle.treasureChanceMult > 1f) return 1;
        if (tackle.coherenceBonus > 0f) return 1;

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


    protected static FishRequirement generate(Random rng, int tier, boolean last) {
        FishRequirement req = new FishRequirement();

        req.count = 2 + Math.min(tier, 5) / 2;
        req.tag = pickTag(rng);

        if (last || tier >= 4) {
            req.speciesId = pickSpecies(rng);
            req.minGrade = FishGrade.FINE;
            req.count = Math.min(req.count, 3);

            if (req.speciesId != null) return req;
        }

        if (tier >= 1) {
            req.minGrade = rng.nextBoolean() ? FishGrade.FINE : FishGrade.AVERAGE;
        } else if (rng.nextBoolean()) {
            req.minGrade = FishGrade.AVERAGE;
        }

        if (tier >= 2) {
            req.minRarity = tier >= 3 ? FishRarity.RARE : FishRarity.UNCOMMON;

            // One hard ask, not both - stacking both is a lottery ticket, not a price.
            if (rng.nextBoolean()) {
                req.sameSpecies = true;
            } else {
                req.lowCoherence = true;

                if (tier < 3) req.minRarity = null;
            }
        }

        return req;
    }

    protected static String pickTag(Random rng) {
        String[] tags = {"fish", "fish", "crab", "mollusc"};

        return tags[rng.nextInt(tags.length)];
    }


    protected static String pickSpecies(Random rng) {
        List<FishSpec> pool = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (spec.rarity.rank < FishRarity.RARE.rank) continue;
            if (spec.tags.contains("special")) continue;

            pool.add(spec);
        }

        if (pool.isEmpty()) return null;

        pool.sort(Comparator.comparing(spec -> spec.id));

        return pool.get(rng.nextInt(pool.size())).id;
    }
}
