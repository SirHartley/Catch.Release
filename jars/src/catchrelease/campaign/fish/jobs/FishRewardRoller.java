package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    /** How far either side of the reckoned worth a roll may land, as a fraction. */
    public static final float SPREAD = 0.35f;

    /** Goods worth handing over, all of them things a working ship actually wants. */
    protected static final String[] GOODS = {
            Commodities.SUPPLIES, Commodities.FUEL, Commodities.METALS,
            Commodities.RARE_METALS, Commodities.ORGANICS, Commodities.DRUGS,
    };

    /**
     * One payment, or two when the job is worth enough to be interesting.
     *
     * @param worth        the job's reckoned value in credits, before it is turned into things
     * @param allowCredits false for givers who have no money to offer (e.g. children)
     */
    public static List<FishReward> roll(Random random, int worth, boolean allowCredits) {
        List<FishReward> rewards = new ArrayList<>();
        if (random == null) random = new Random();

        int value = vary(random, worth);

        FishReward main = rollOne(random, value, allowCredits);
        if (main != null) rewards.add(main);

        // A second, smaller reward on the better jobs.
        if (value > VALUE_PER_FISH * 3 && random.nextFloat() > 0.45f) {
            FishReward extra = rollOne(random, value / 3, allowCredits);
            if (extra != null) rewards.add(extra);
        }

        // Fallback if every other kind was filtered out empty (no upgrades left, no species left, etc).
        if (rewards.isEmpty()) rewards.add(FishReward.credits(Math.max(500, value)));

        return rewards;
    }

    protected static FishReward rollOne(Random random, int value, boolean allowCredits) {
        float roll = random.nextFloat();

        if (allowCredits && roll < 0.34f) return FishReward.credits(value);
        if (roll < 0.52f) return rollUpgrade(random);
        if (roll < 0.66f) return rollTackle(random);
        if (roll < 0.80f) return rollLocationData(random);
        if (roll < 0.90f) return rollGoods(random, value);

        return rollBlueprint(random);
    }

    /** A rung on something, chosen from the sheet rather than from a list kept here. */
    protected static FishReward rollUpgrade(Random random) {
        if (UpgradeManager.getInstance() == null) return null;

        List<UpgradeStat> open = new ArrayList<>();
        for (UpgradeStat stat : UpgradeManager.getInstance().getAll().values()) {
            if (stat == null || stat.id == null) continue;
            if (stat.id.equalsIgnoreCase("example")) continue;

            if (stat.maxLevel > 0 && stat.level >= stat.maxLevel) continue;

            open.add(stat);
        }

        if (open.isEmpty()) return null;

        return FishReward.upgrade(open.get(random.nextInt(open.size())).id, 1);
    }

    /** A module the player does not already own - owned once, fitted freely, so a duplicate is nothing. */
    protected static FishReward rollTackle(Random random) {
        List<Tackle> options = new ArrayList<>();
        for (Tackle tackle : Tackle.values()) {
            if (tackle != Tackle.NONE && !TackleManager.isOwned(tackle)) options.add(tackle);
        }

        if (options.isEmpty()) return null;

        return FishReward.tackle(options.get(random.nextInt(options.size())));
    }

    /** Location data for a species not yet caught or already unlocked - a reward that already applied does nothing. */
    protected static FishReward rollLocationData(Random random) {
        List<FishSpec> unknown = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (spec.regions.isEmpty()) continue;
            if (FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id)) continue;

            unknown.add(spec);
        }

        if (unknown.isEmpty()) return null;

        return FishReward.locationData(unknown.get(random.nextInt(unknown.size())).id);
    }

    protected static FishReward rollGoods(Random random, int value) {
        String commodity = GOODS[random.nextInt(GOODS.length)];

        int quantity = Math.max(5, value / 120);

        return FishReward.commodity(commodity, quantity);
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

    /** Same job, different day. */
    protected static int vary(Random random, int worth) {
        float mult = 1f + (random.nextFloat() * 2f - 1f) * SPREAD;

        return Math.max(300, Math.round(worth * mult));
    }
}
