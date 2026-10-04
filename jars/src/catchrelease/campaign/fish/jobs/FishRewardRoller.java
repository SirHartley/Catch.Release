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
    public static final int CREDIT_BASE = 6000;
    public static final float CREDIT_PAYOUT_MULT = 5f;

    protected static void coalesceCredits(List<FishReward> rewards) {
        int first = -1;
        int total = 0;
        float valueMultiplier = 0f;

        for (int i = rewards.size() - 1; i >= 0; i--) {
            FishReward reward = rewards.get(i);
            if (!(reward instanceof FishReward.Credits)) continue;

            first = i;
            FishReward.Credits credits = (FishReward.Credits) reward;
            total += credits.amount;
            valueMultiplier = Math.max(valueMultiplier, credits.valueMultiplier);
            rewards.remove(i);
        }

        if (first >= 0) {
            rewards.add(first, FishReward.questCredits(roundCreditReward(total), valueMultiplier));
        }
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

            int targetLevel = ShopSchematics.getNextRequiredLevel(stat);
            if (targetLevel < 0 || ShopSchematics.has(stat, targetLevel)) continue;
            if (reserved.contains(ShopSchematics.getKey(stat.id, targetLevel))) continue;

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

    public static List<FishReward> rollSchematic(Random random) {
        Set<String> reserved = getReservedSchematicKeys();
        WeightedRandomPicker<FishReward> picker = new WeightedRandomPicker<>(random);
        addIfPresent(picker, rollUpgrade(random, reserved), 1f);
        addIfPresent(picker, rollTackle(random, reserved), 1f);

        FishReward reward = picker.pick();
        return reward == null ? List.of() : List.of(reward);
    }

    public static List<FishReward> rollBackdropReward(Random random) {
        FishReward reward = rollBackdrop(random);
        return reward == null ? List.of() : List.of(reward);
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

    protected static void reserveLocationData(FishReward reward, Set<String> reserved) {
        if (!(reward instanceof FishReward.LocationData) || reserved == null) return;

        reserved.add(((FishReward.LocationData) reward).speciesId);
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

    public static List<FishReward> rollLocationData(Random random, int count,
                                                    int fallbackCredits) {
        List<FishReward> rewards = new ArrayList<>();
        List<FishSpec> unknown = getUnknownLocationData(null);
        if (random == null) random = new Random();

        while (rewards.size() < count && !unknown.isEmpty()) {
            FishSpec spec = unknown.remove(random.nextInt(unknown.size()));
            rewards.add(FishReward.locationData(spec.id, fallbackCredits));
        }

        return rewards;
    }

    protected static List<FishSpec> getUnknownLocationData(Set<String> reserved) {
        List<FishSpec> unknown = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (!spec.hasHabitat()) continue;
            if (spec.rarity == FishRarity.LEGENDARY) continue;
            if (FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id)) continue;
            if (reserved != null && reserved.contains(spec.id)) continue;

            unknown.add(spec);
        }

        return unknown;
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

    public static int roundCreditRewardUp(int amount) {
        int step = amount > 100_000 ? 10_000 : 1_000;

        return (Math.max(0, amount) + step - 1) / step * step;
    }

    public static float valueMultiplier(Random random) {
        WeightedRandomPicker<Float> picker = new WeightedRandomPicker<>(
                random == null ? new Random() : random);
        picker.add(3f, 55f);
        picker.add(4f, 21f);
        picker.add(5f, 11f);
        picker.add(6f, 6f);
        picker.add(7f, 3.5f);
        picker.add(8f, 2f);
        picker.add(9f, 1f);
        picker.add(10f, 0.5f);

        return picker.pick();
    }

}
