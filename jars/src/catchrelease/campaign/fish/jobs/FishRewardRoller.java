package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.campaign.fish.colony.Backdrops;
import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class FishRewardRoller {

    public static final int VALUE_PER_FISH = 1200;
    public static final float CREDIT_PAYOUT_MULT = 5f;
    public static final float SPREAD = 0.35f;

    public static List<FishReward> roll(Random random, int worth, boolean allowCredits) {
        List<FishReward> rewards = new ArrayList<>();
        Set<String> reserved = getReservedSchematicKeys();
        if (random == null) random = new Random();

        int value = vary(random, worth);

        FishReward main = rollOne(random, value, allowCredits, reserved);
        if (main != null) {
            rewards.add(main);
            reserve(main, reserved);
        }

        if (value > VALUE_PER_FISH * 3 && random.nextFloat() > 0.45f) {
            FishReward extra = rollOne(random, value / 3, allowCredits, reserved);
            if (extra != null) {
                rewards.add(extra);
                reserve(extra, reserved);
            }
        }

        coalesceCredits(rewards);

        // Cash-enabled jobs still need a payout after every progression reward is exhausted.
        if (rewards.isEmpty() && allowCredits) {
            rewards.add(FishReward.credits(creditPayout(value)));
        }

        return rewards;
    }

    protected static void coalesceCredits(List<FishReward> rewards) {
        int first = -1;
        int total = 0;

        for (int i = rewards.size() - 1; i >= 0; i--) {
            FishReward reward = rewards.get(i);
            if (!(reward instanceof FishReward.Credits)) continue;

            first = i;
            total += ((FishReward.Credits) reward).amount;
            rewards.remove(i);
        }

        if (first >= 0) rewards.add(first, FishReward.credits(roundCreditReward(total)));
    }

    protected static FishReward rollOne(Random random, int value, boolean allowCredits,
                                        Set<String> reserved) {
        if (!allowCredits) return rollNonCredit(random, reserved);

        float roll = random.nextFloat();

        if (roll < 0.34f) return FishReward.credits(creditPayout(value));
        if (roll < 0.52f) return rollUpgrade(random, reserved);
        if (roll < 0.66f) return rollTackle(random, reserved);
        if (roll < 0.78f) return rollLocationData(random, value);
        if (roll < 0.86f) return rollBackdrop(random);
        if (roll < 0.93f) return FishReward.credits(creditPayout(value));

        return rollBlueprint(random);
    }

    protected static FishReward rollNonCredit(Random random, Set<String> reserved) {
        WeightedRandomPicker<FishReward> picker = new WeightedRandomPicker<>(random);

        addIfPresent(picker, rollUpgrade(random, reserved), 0.52f);
        addIfPresent(picker, rollTackle(random, reserved), 0.14f);
        addIfPresent(picker, rollBackdrop(random), 0.08f);
        addIfPresent(picker, rollBlueprint(random), 0.14f);

        return picker.pick();
    }

    protected static void addIfPresent(WeightedRandomPicker<FishReward> picker,
                                       FishReward reward, float weight) {
        if (reward != null) picker.add(reward, weight);
    }

    protected static FishReward rollUpgrade(Random random, Set<String> reserved) {
        if (UpgradeManager.getInstance() == null) return null;

        List<UpgradeStat> open = new ArrayList<>();
        for (UpgradeStat stat : UpgradeManager.getInstance().getAll().values()) {
            if (stat == null || stat.id == null) continue;
            if (stat.id.equalsIgnoreCase("example")) continue;

            int targetLevel = ShopSchematics.getNextRequiredLevel(stat);
            if (targetLevel < 0 || ShopSchematics.has(stat, targetLevel)) continue;
            if (reserved.contains(ShopSchematics.getKey(stat.id, targetLevel))) continue;

            // a plan for a rig the player does not hold is a reward for somebody else's boat; the ungrouped catch stats answer null and stay open, being the minigame's own
            String ability = StatIds.getAbilityId(stat.id);
            if (ability != null && !FishingIntro.hasGear(ability)) continue;

            open.add(stat);
        }

        if (open.isEmpty()) return null;

        UpgradeStat stat = open.get(random.nextInt(open.size()));

        return FishReward.upgradeSchematic(stat.id, ShopSchematics.getNextRequiredLevel(stat));
    }

    protected static FishReward rollTackle(Random random, Set<String> reserved) {
        List<Tackle> options = new ArrayList<>();
        for (Tackle tackle : Tackle.values()) {
            if (tackle != Tackle.NONE && tackle.stocked && TackleManager.isUnlocked(tackle)
                    && !ShopSchematics.has(tackle)
                    && !reserved.contains(ShopSchematics.getKey(tackle))
                    && ownsRigFor(tackle)) {
                options.add(tackle);
            }
        }

        if (options.isEmpty()) return null;

        return FishReward.tackleSchematic(options.get(random.nextInt(options.size())));
    }

    public static Set<String> getReservedSchematicKeys() {
        Set<String> reserved = new LinkedHashSet<>();
        if (Global.getSector() == null || Global.getSector().getIntelManager() == null) {
            return reserved;
        }

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
            if (!(intel instanceof FishJob)) continue;

            FishJob job = (FishJob) intel;
            if (job.isEnding() || job.isEnded()) continue;

            for (FishReward reward : job.getRewards()) reserve(reward, reserved);
        }

        return reserved;
    }

    public static boolean isSchematicReserved(String key) {
        return key != null && getReservedSchematicKeys().contains(key);
    }

    protected static void reserve(FishReward reward, Set<String> reserved) {
        if (reward == null || reserved == null) return;

        String key = reward.getSchematicKey();
        if (key != null) reserved.add(key);
    }

    protected static boolean ownsRigFor(Tackle tackle) {
        if (tackle == null || tackle.fit == null) return false;

        switch (tackle.fit) {
            case DRONE: return FishingIntro.hasGear(StatIds.ROD_ABILITY);
            case HARPOON: return FishingIntro.hasGear(StatIds.HARPOON_ABILITY);
            case SEARCHLIGHT: return FishingIntro.hasGear(StatIds.LAMPS_ABILITY);
            case BOTH: return FishingIntro.hasGear(StatIds.ROD_ABILITY)
                    || FishingIntro.hasGear(StatIds.HARPOON_ABILITY);
            default: return false;
        }
    }

    protected static FishReward rollLocationData(Random random, int fallbackCredits) {
        List<FishSpec> unknown = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (!spec.hasHabitat()) continue;
            if (spec.rarity == FishRarity.LEGENDARY) continue;
            if (FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id)) continue;

            unknown.add(spec);
        }

        if (unknown.isEmpty()) return null;

        return FishReward.locationData(unknown.get(random.nextInt(unknown.size())).id,
                fallbackCredits);
    }

    protected static FishReward rollBackdrop(Random random) {
        if (!CrabWares.hasConservatoryPlans()) return null;

        WeightedRandomPicker<Backdrop> picker = new WeightedRandomPicker<>(random);

        for (Backdrop backdrop : Backdrops.getUnowned()) {
            float weight = 1f / (1f + backdrop.rarity.rank * 2f);

            picker.add(backdrop, backdrop.crabStock ? weight * 0.5f : weight);
        }

        Backdrop picked = picker.pick();

        return picked == null ? null : FishReward.backdrop(picked.id);
    }

    protected static FishReward rollBlueprint(Random random) {
        boolean weapon = random.nextBoolean();

        List<String> options = new ArrayList<>();

        if (weapon) {
            for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
                if (!spec.hasTag(Items.TAG_RARE_BP) || spec.hasTag(Tags.NO_DROP)) continue;
                if (Global.getSector().getPlayerFaction().knowsWeapon(spec.getWeaponId())) continue;

                options.add(spec.getWeaponId());
            }
        } else {
            for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
                if (!spec.hasTag(Items.TAG_RARE_BP) || spec.hasTag(Tags.NO_DROP)) continue;
                if (Global.getSector().getPlayerFaction().knowsFighter(spec.getId())) continue;

                options.add(spec.getId());
            }
        }

        if (options.isEmpty()) return null;

        return FishReward.blueprint(weapon ? Items.WEAPON_BP : Items.FIGHTER_BP,
                options.get(random.nextInt(options.size())));
    }

    public static int creditPayout(int value) {
        int payout = Math.max(500, Math.round(Math.max(0, value) * CREDIT_PAYOUT_MULT));

        return roundCreditReward(payout);
    }

    public static int roundCreditReward(int amount) {
        int step = amount > 100_000 ? 10_000 : 1_000;

        return Math.round(amount / (float) step) * step;
    }

    protected static int vary(Random random, int worth) {
        float mult = 1f + (random.nextFloat() * 2f - 1f) * SPREAD;

        return Math.max(300, Math.round(worth * mult));
    }
}
