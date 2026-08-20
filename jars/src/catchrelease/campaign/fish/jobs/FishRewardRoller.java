package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.campaign.fish.colony.Backdrops;
import catchrelease.campaign.fish.data.FishLog;
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

/**
 * Rolls a job's payment fresh each time, rather than a fixed rate - jobs repeat, and a fixed payment
 * would turn a repeatable job into either a grind or something never taken twice.
 * <p>
 * Weighted away from credits without excluding them: money alone would beg the question of why fish
 * instead of running cargo, but some givers genuinely have nothing else to offer.
 */
public class FishRewardRoller {

    /** Roughly what a job is worth, before it is decided what shape that takes. */
    public static final int VALUE_PER_FISH = 1200;

    /** Cash has to compete with actual Starsector work; barter values remain the reward budget. */
    public static final float CREDIT_PAYOUT_MULT = 5f;

    /** How far either side of the reckoned worth a roll may land, as a fraction. */
    public static final float SPREAD = 0.35f;

    /**
     * One payment, or two when the job is worth enough to be interesting.
     *
     * @param worth        the job's reckoned value in credits, before it is turned into things
     * @param allowCredits false for givers who have no money to offer (e.g. children)
     */
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

        // A second, smaller reward on the better jobs.
        if (value > VALUE_PER_FISH * 3 && random.nextFloat() > 0.45f) {
            FishReward extra = rollOne(random, value / 3, allowCredits, reserved);
            if (extra != null) {
                rewards.add(extra);
                reserve(extra, reserved);
            }
        }

        coalesceCredits(rewards);

        // Fallback if every other kind was filtered out empty (no upgrades left, no species left, etc).
        if (rewards.isEmpty()) rewards.add(FishReward.credits(creditPayout(value)));

        return rewards;
    }

    /** One payment should name cash once, even when both independently rolled slots land on it. */
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

        if (first >= 0) rewards.add(first, FishReward.credits(total));
    }

    protected static FishReward rollOne(Random random, int value, boolean allowCredits,
                                        Set<String> reserved) {
        float roll = random.nextFloat();

        if (allowCredits && roll < 0.34f) return FishReward.credits(creditPayout(value));
        if (roll < 0.52f) return rollUpgrade(random, reserved);
        if (roll < 0.66f) return rollTackle(random, reserved);
        if (roll < 0.78f) return rollLocationData(random, value);
        if (roll < 0.86f) return rollBackdrop(random);
        if (allowCredits && roll < 0.93f) return FishReward.credits(creditPayout(value));

        return rollBlueprint(random);
    }

    /** A purchase plan for the next gated rung, chosen from the sheet rather than a fixed list. */
    protected static FishReward rollUpgrade(Random random, Set<String> reserved) {
        if (UpgradeManager.getInstance() == null) return null;

        List<UpgradeStat> open = new ArrayList<>();
        for (UpgradeStat stat : UpgradeManager.getInstance().getAll().values()) {
            if (stat == null || stat.id == null) continue;
            if (stat.id.equalsIgnoreCase("example")) continue;

            int targetLevel = ShopSchematics.getNextRequiredLevel(stat);
            if (targetLevel < 0 || ShopSchematics.has(stat, targetLevel)) continue;
            if (reserved.contains(ShopSchematics.getKey(stat.id, targetLevel))) continue;

            //a plan for a rig the player does not hold is a reward for somebody else's boat;
            //the ungrouped catch stats answer null and stay open, being the minigame's own
            String ability = StatIds.getAbilityId(stat.id);
            if (ability != null && !FishingIntro.hasGear(ability)) continue;

            open.add(stat);
        }

        if (open.isEmpty()) return null;

        UpgradeStat stat = open.get(random.nextInt(open.size()));

        return FishReward.upgradeSchematic(stat.id, ShopSchematics.getNextRequiredLevel(stat));
    }

    /** A stocked module whose purchase schematic is not yet known. Equipment prerequisites still
     * apply, so a reward cannot advertise a rig the introduction has not handed over. */
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

    /** Schematic rewards on accepted jobs are promises already made and stay out of new offers. */
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

    /** Location data for a species not yet caught or already unlocked - a reward that already applied does nothing. */
    /** Whether the player holds the rig this module bolts to - a plan for a slot they do not
     *  have is a reward for somebody else's boat. BOTH fits either the drones or the line. */
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
            if (FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id)) continue;

            unknown.add(spec);
        }

        if (unknown.isEmpty()) return null;

        return FishReward.locationData(unknown.get(random.nextInt(unknown.size())).id,
                fallbackCredits);
    }

    /**
     * A scene for an aquarium, weighted towards the plain ones.
     * <p>
     * The one payment on the list that is worth nothing - which is what makes it a good one to
     * offer. Everything else here moves the campaign along, and a table of rewards that only ever
     * moves the campaign along is a table with no room in it for a thing somebody just wanted.
     * <p>
     * Anything the player does not have. {@code crabStock} is not a gate here and must not become
     * one: it says whether the man with the coat may carry a scene, and twelve of the nineteen
     * rows say no - which is the table's way of saying those are the ones a job is <i>for</i>.
     * Reading it as "only what he sells" left two thirds of the art unobtainable by any route in
     * the game, which is also what the office's own tooltip promises against: scenes come from
     * quests or from him.
     */
    protected static FishReward rollBackdrop(Random random) {
        WeightedRandomPicker<Backdrop> picker = new WeightedRandomPicker<>(random);

        for (Backdrop backdrop : Backdrops.getUnowned()) {
            //the rarity ladder read backwards: a job is a plausible way to come by a reef and an
            //implausible way to come by the abyss. One he also sells is likelier still, since a
            //job is not the only way to that one and the pool should lean on what it alone gives
            float weight = 1f / (1f + backdrop.rarity.rank * 2f);

            picker.add(backdrop, backdrop.crabStock ? weight * 0.5f : weight);
        }

        Backdrop picked = picker.pick();

        return picked == null ? null : FishReward.backdrop(picked.id);
    }

    /**
     * A weapon or fighter blueprint the player doesn't already know, named rather than blank - a
     * blueprint item's payload is its id, and one carrying nothing breaks its tooltip. Filtered the
     * way vanilla's own drops are: {@code TAG_RARE_BP} only, nothing {@code NO_DROP}.
     */
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

    /** Turns the roller's internal barter value into a cash payout without inflating item rewards. */
    public static int creditPayout(int value) {
        return Math.max(500, Math.round(Math.max(0, value) * CREDIT_PAYOUT_MULT));
    }

    /** Same job, different day. */
    protected static int vary(Random random, int worth) {
        float mult = 1f + (random.nextFloat() * 2f - 1f) * SPREAD;

        return Math.max(300, Math.round(worth * mult));
    }
}
