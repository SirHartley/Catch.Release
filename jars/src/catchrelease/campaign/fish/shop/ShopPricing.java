package catchrelease.campaign.fish.shop;

import catchrelease.config.Setting;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ShopPricing {

    public static final String SEED_KEY = "$catchrelease_shop_seed";
    public static final String PAYMENTS_KEY = "$catchrelease_shop_payments";

    protected static final Set<String> PREMIUM_STATS = Set.of(
            StatIds.SEARCHLIGHT_SLOW, StatIds.HARPOON_AIM_ASSIST);

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

        int credits = getCredits(stat, targetLevel);
        int band = credits / 5000 - 1 + (PREMIUM_STATS.contains(stat.id) ? 1 : 0);

        FishRequirement fish = getPayment("stat:" + stat.id + ":" + targetLevel, band);
        if (fish != null && Setting.UPGRADE_FISH.mult() != 1f) {
            fish = new FishRequirement(fish);
            fish.count = Math.max(1, (int) Math.ceil(fish.count * Setting.UPGRADE_FISH.mult()));
        }
        return new Price(Math.round(credits * Setting.UPGRADE_CREDITS.mult()), fish);
    }

    public static Price getPrice(Tackle tackle) {
        if (tackle == null || tackle == Tackle.NONE) return null;

        int credits = getCredits(tackle);
        return new Price(Math.round(credits * Setting.EQUIPMENT_COST.mult()),
                getPayment("tackle:" + tackle.name(), (credits - 20000) / 5000));
    }

    public static int getCredits(UpgradeStat stat, int targetLevel) {
        if (stat == null || targetLevel < 1 || targetLevel > stat.maxLevel) return 0;

        return 5000 * Math.max(1, Math.min(4, 4 - stat.maxLevel + targetLevel));
    }

    public static int getCredits(Tackle tackle) {
        if (tackle == null) return 0;

        return switch (tackle) {
            case NONE -> 0;
            // the lowest tier is 25000, so every module's fish payment reaches past Common-only band 0
            case NEEDLE_SENSOR, NEUTRINO_LENS, GLITTER_LENS, STASIS_CRADLE, SHORING_HEAD -> 25000;
            case CALIBRATED_GRADER, TRACKING_GIMBAL -> 30000;
            case SALVAGE_TRAWL, BAITED_RESONATOR, FANNED_ARRAY,
                    FATHOM_HEAD, RETRIEVAL_HEAD -> 35000;
            case SPOOL_GOVERNOR, INERTIAL_DAMPER, LIFTING_RIG -> 40000;
            case BARBED_HEAD, HOLDFAST_CLAMP, BREACH_COUPLER,
                    SONAR_HEAD, EXPLOSIVE_HEAD -> 45000;
        };
    }

    public static boolean isMaxed(UpgradeStat stat) {
        return stat != null && stat.maxLevel > 0 && stat.level >= stat.maxLevel;
    }

    @SuppressWarnings("unchecked")
    protected static FishRequirement getPayment(String key, int band) {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Map<String, FishRequirement> payments = (Map<String, FishRequirement>)
                data.computeIfAbsent(PAYMENTS_KEY, ignored -> new LinkedHashMap<>());
        FishRequirement saved = payments.get(key);
        if (saved != null) return saved;

        Random random = new Random(getSeed() ^ key.hashCode() * 1000003L);
        FishRequirement payment = ShopFishPayments.generate(random, band,
                ShopFishPayments.availableCatches(), payments.values());
        // No obtainable catch means no sale, not a credit-only purchase. Retry when gear is available.
        if (payment != null) payments.put(key, payment);
        return payment;
    }
}
