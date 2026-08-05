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
 * What somebody pays, decided fresh each time they ask.
 * <p>
 * These are repeatable jobs, which is the whole reason this exists. A fixed payment turns a
 * repeatable job into a rate - the player works out what a chef is worth once and then either farms
 * it forever or never looks at one again. Rolled, the same chef is worth taking twice because the
 * second one might be the time they hand over something off the back of the workshop.
 * <p>
 * The pool is deliberately weighted away from credits without excluding them. Money is the reward
 * every other job in the game already pays, and a fishing mod paying only money would be asking why
 * anyone would fish rather than run cargo - but a person in a bar who has nothing else on them is
 * also a real person, and always producing a blueprint would be a stranger world than that.
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
     * @param allowCredits false for the givers who have no money to offer, which is its own
     *                     characterisation - a pair of children do not pay in cash
     */
    public static List<FishReward> roll(Random random, int worth, boolean allowCredits) {
        List<FishReward> rewards = new ArrayList<>();
        if (random == null) random = new Random();

        int value = vary(random, worth);

        FishReward main = rollOne(random, value, allowCredits);
        if (main != null) rewards.add(main);

        //a second, smaller thing on the better jobs. Two rewards read as somebody emptying their
        //pockets, which is what a person who badly wants something actually does
        if (value > VALUE_PER_FISH * 3 && random.nextFloat() > 0.45f) {
            FishReward extra = rollOne(random, value / 3, allowCredits);
            if (extra != null) rewards.add(extra);
        }

        //nobody hands over nothing. If every other kind declined - no upgrades left to buy, no
        //species left to survey - it falls back to the one payment that is always available
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

            //nothing is gained by being handed a rung that does not exist any more
            if (stat.maxLevel > 0 && stat.level >= stat.maxLevel) continue;

            open.add(stat);
        }

        if (open.isEmpty()) return null;

        return FishReward.upgrade(open.get(random.nextInt(open.size())).id, 1);
    }

    /**
     * A module the player does not already have.
     * <p>
     * Filtered like the other kinds here, and for the same reason: a module is owned once and fitted
     * as often as you like, so handing over a second copy of one is handing over nothing at all.
     */
    protected static FishReward rollTackle(Random random) {
        List<Tackle> options = new ArrayList<>();
        for (Tackle tackle : Tackle.values()) {
            if (tackle != Tackle.NONE && !TackleManager.isOwned(tackle)) options.add(tackle);
        }

        if (options.isEmpty()) return null;

        return FishReward.tackle(options.get(random.nextInt(options.size())));
    }

    /**
     * A word about where something lives, for a species the player does not already know.
     * <p>
     * Filtered rather than rolled blind, since survey data on a fish already in the log is a reward
     * that does nothing at all - and the one thing worse than a small payment is one the player can
     * see is empty.
     */
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

        //a rough count rather than a priced one, since the point is a crate of something useful
        int quantity = Math.max(5, value / 120);

        return FishReward.commodity(commodity, quantity);
    }

    /**
     * A weapon or fighter pattern the player does not already hold.
     * <p>
     * Named rather than blank, because a blueprint item is an id and a payload and the payload is
     * the whole of what it is - a weapon_bp carrying nothing is not a generic blueprint, it is an
     * item the game cannot draw a tooltip for. Filtered the way vanilla's own blueprint drops are:
     * only the patterns marked worth finding, nothing tagged undroppable, and nothing already known.
     */
    protected static FishReward rollBlueprint(Random random) {
        //weapons and fighters are the ones a stranger plausibly has a copy of lying about
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
