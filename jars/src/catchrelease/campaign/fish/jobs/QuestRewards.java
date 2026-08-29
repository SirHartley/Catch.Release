package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.helper.loading.BackdropLoader;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.shop.ShopPricing;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The one place quest rewards come from. A quest states what it demands (scored by
 * {@link DemandScore}), what always fires, and what may never appear; this rolls the
 * rest against a credit budget derived from the score, so hard demands pay well by
 * construction and a windfall or a stingy day is a die roll, not an authoring bug.
 * Good rewards gate on the tier: schematics and backdrops need a medium ask, rare
 * blueprints a hard one, and expensive range data prices itself out of small budgets.
 */
public class QuestRewards {

    /** Credits owed per point of demand score: one common (score 10) pays 6000. */
    public static final float CREDITS_PER_POINT = 600f;
    /** Worth units per score point, for the legacy value-multiplier maths. */
    public static final float WORTH_PER_POINT = 120f;

    public static final float ROLL_MIN = 0.75f;
    public static final float ROLL_MAX = 1.35f;
    /** One non-credit item is rolled; a second is a lucky day, never the norm. */
    public static final float SECOND_ITEM_CHANCE = 0.25f;
    /** A pick may overshoot the remaining budget by this much - generosity, bounded. */
    public static final float OVERSHOOT = 1.3f;
    public static final int MIN_CREDIT_REMAINDER = 1000;
    /** The least money a chart arrives with when it cannot bring a second chart. */
    public static final int LONE_CHART_CREDITS = 2000;

    /** What a species' range data is worth in credits: the fish's value, times ten. */
    public static final float RANGE_DATA_VALUE_MULT = 10f;
    public static final float SCHEMATIC_VALUE_MULT = 0.5f;
    public static final int SCHEMATIC_VALUE_FLOOR = 3000;
    public static final int BACKDROP_VALUE_BASE = 3000;
    public static final int BACKDROP_VALUE_PER_RANK = 4000;
    public static final int BLUEPRINT_VALUE = 25000;

    public enum Kind {

        CREDITS(DemandScore.Tier.EASY),
        RANGE_DATA(DemandScore.Tier.EASY),
        UPGRADE_SCHEMATIC(DemandScore.Tier.MEDIUM),
        TACKLE_SCHEMATIC(DemandScore.Tier.MEDIUM),
        BACKDROP(DemandScore.Tier.MEDIUM),
        BLUEPRINT(DemandScore.Tier.HARD);

        public final DemandScore.Tier gate;

        Kind(DemandScore.Tier gate) {
            this.gate = gate;
        }
    }

    public static class Request {

        public List<FishRequirement> asks;
        public float scoreOverride = -1f;
        public float budgetMult = 1f;
        public DemandScore.Tier tierFloor = null;
        public List<FishReward> fixed = new ArrayList<>();
        public Set<Kind> excluded = EnumSet.noneOf(Kind.class);
        public boolean allowCredits = true;
        public Random random;

        public Request(List<FishRequirement> asks) {
            this.asks = asks;
        }

        public Request score(float score) {
            scoreOverride = score;
            return this;
        }

        public Request budgetMult(float mult) {
            budgetMult = mult;
            return this;
        }

        public Request fix(FishReward reward) {
            if (reward != null) fixed.add(reward);
            return this;
        }

        public Request fixAll(List<FishReward> rewards) {
            if (rewards != null) {
                for (FishReward reward : rewards) fix(reward);
            }
            return this;
        }

        public Request exclude(Kind... kinds) {
            for (Kind kind : kinds) excluded.add(kind);
            return this;
        }

        public Request noCredits() {
            allowCredits = false;
            excluded.add(Kind.CREDITS);
            return this;
        }

        /** Treat the quest as at least this tier for reward gating - for quests whose
         *  whole character is a good prize regardless of how light the ask is. */
        public Request tierFloor(DemandScore.Tier floor) {
            tierFloor = floor;
            return this;
        }

        public Request random(Random random) {
            this.random = random;
            return this;
        }
    }

    public static class Result {

        public final float score;
        public final DemandScore.Tier tier;
        public final int budgetCredits;
        public final List<FishReward> rewards;

        protected Result(float score, DemandScore.Tier tier, int budgetCredits,
                         List<FishReward> rewards) {
            this.score = score;
            this.tier = tier;
            this.budgetCredits = budgetCredits;
            this.rewards = rewards;
        }
    }

    public static Result roll(Request request) {
        Random random = request.random == null ? new Random() : request.random;

        float score = request.scoreOverride >= 0f
                ? request.scoreOverride : DemandScore.of(request.asks);
        DemandScore.Tier tier = DemandScore.tierOf(score);
        if (request.tierFloor != null && request.tierFloor.atLeast(tier)) {
            tier = request.tierFloor;
        }

        float factor = ROLL_MIN + random.nextFloat() * (ROLL_MAX - ROLL_MIN);
        int budget = Math.round(score * CREDITS_PER_POINT * request.budgetMult * factor);

        List<FishReward> rewards = new ArrayList<>();
        Set<String> reservedSchematics = FishRewardRoller.getReservedSchematicKeys();
        Set<String> reservedData = new LinkedHashSet<>();

        int left = budget;
        for (FishReward fixed : request.fixed) {
            rewards.add(fixed);
            reserve(fixed, reservedSchematics, reservedData);
            left -= valueOf(fixed);
        }
        left = Math.max(0, left);

        float multiplier = FishRewardRoller.valueMultiplier(
                Math.round(score * WORTH_PER_POINT), requestedFish(request.asks));

        int itemRolls = random.nextFloat() < SECOND_ITEM_CHANCE ? 2 : 1;
        for (int i = 0; i < itemRolls && left > MIN_CREDIT_REMAINDER; i++) {
            FishReward pick = rollOne(random, request, tier, left,
                    reservedSchematics, reservedData);
            if (pick == null) break;

            rewards.add(pick);
            reserve(pick, reservedSchematics, reservedData);
            left = Math.max(0, left - valueOf(pick));
        }

        if (request.allowCredits) {
            int amount = left >= MIN_CREDIT_REMAINDER
                    ? FishRewardRoller.roundCreditReward(left) : 0;

            // a chart is too cheap a reward to come alone: it brings real money
            if (amount < LONE_CHART_CREDITS && countCharts(rewards) == 1) {
                amount = LONE_CHART_CREDITS;
            }

            // the guaranteed sum answers the budget; the multiplier rides on top so
            // the hand-in itself is always worth money - the player's way to offload
            // a prize specimen at a better price than any market pays
            rewards.add(FishReward.questCredits(amount, multiplier));
        } else if (countCharts(rewards) == 1
                && !request.excluded.contains(Kind.RANGE_DATA)) {
            // no money allowed, so a lone chart brings a second chart instead
            FishReward pair = rollFittingLocationData(random, Integer.MAX_VALUE / 2,
                    reservedData);
            if (pair != null) {
                rewards.add(pair);
                reserve(pair, reservedSchematics, reservedData);
            }
        }

        // a no-credit quest whose every pick failed still owes something for the work
        if (rewards.isEmpty()) {
            FishReward fallback = FishRewardRoller.rollNonCredit(random, reservedSchematics);
            if (fallback != null) rewards.add(fallback);
        }

        FishRewardRoller.coalesceCredits(rewards);

        return new Result(score, tier, budget, rewards);
    }

    protected static FishReward rollOne(Random random, Request request, DemandScore.Tier tier,
                                        int budgetLeft, Set<String> reservedSchematics,
                                        Set<String> reservedData) {
        WeightedRandomPicker<Kind> picker = new WeightedRandomPicker<>(random);

        for (Kind kind : Kind.values()) {
            if (kind == Kind.CREDITS) continue;
            if (request.excluded.contains(kind)) continue;
            if (!tier.atLeast(kind.gate)) continue;

            picker.add(kind, weightOf(kind));
        }

        while (!picker.isEmpty()) {
            Kind kind = picker.pickAndRemove();
            FishReward pick = rollKind(random, kind, budgetLeft, reservedSchematics,
                    reservedData);

            if (pick != null && valueOf(pick) <= budgetLeft * OVERSHOOT) return pick;
        }

        return null;
    }

    protected static float weightOf(Kind kind) {
        switch (kind) {
            case RANGE_DATA: return 0.30f;
            case UPGRADE_SCHEMATIC: return 0.30f;
            case TACKLE_SCHEMATIC: return 0.18f;
            case BACKDROP: return 0.12f;
            case BLUEPRINT: return 0.10f;
            default: return 0f;
        }
    }

    protected static FishReward rollKind(Random random, Kind kind, int budgetLeft,
                                         Set<String> reservedSchematics,
                                         Set<String> reservedData) {
        switch (kind) {
            case RANGE_DATA: return rollFittingLocationData(random, budgetLeft, reservedData);
            case UPGRADE_SCHEMATIC: return FishRewardRoller.rollUpgrade(random,
                    reservedSchematics);
            case TACKLE_SCHEMATIC: return FishRewardRoller.rollTackle(random,
                    reservedSchematics);
            case BACKDROP: return FishRewardRoller.rollBackdrop(random);
            case BLUEPRINT: return FishRewardRoller.rollBlueprint(random);
            default: return null;
        }
    }

    /** Range data whose own price fits the budget - epic charts need an epic ask. */
    protected static FishReward rollFittingLocationData(Random random, int budgetLeft,
                                                        Set<String> reservedData) {
        List<FishSpec> unknown = FishRewardRoller.getUnknownLocationData(reservedData);

        List<FishSpec> fitting = new ArrayList<>();
        for (FishSpec spec : unknown) {
            if (rangeDataValue(spec) <= budgetLeft * OVERSHOOT) fitting.add(spec);
        }

        if (fitting.isEmpty()) return null;

        return FishReward.locationData(fitting.get(random.nextInt(fitting.size())).id, 0);
    }

    /** Every reward kind priced in credits, so budgets and picks share one currency. */
    public static int valueOf(FishReward reward) {
        if (reward == null) return 0;

        if (reward instanceof FishReward.Credits) {
            return ((FishReward.Credits) reward).amount;
        }
        if (reward instanceof FishReward.LocationData) {
            FishSpec spec = FishSpecLoader.getFishSpec(
                    ((FishReward.LocationData) reward).speciesId);
            return spec == null ? Math.round(FishRewardRoller.VALUE_PER_FISH
                    * RANGE_DATA_VALUE_MULT / 4f) : rangeDataValue(spec);
        }
        if (reward instanceof FishReward.UpgradeSchematic) {
            FishReward.UpgradeSchematic schematic = (FishReward.UpgradeSchematic) reward;
            UpgradeStat stat = UpgradeManager.getInstance() == null
                    ? null : UpgradeManager.getInstance().getAll().get(schematic.statId);
            if (stat == null) return SCHEMATIC_VALUE_FLOOR;

            return schematicValue(ShopPricing.getPrice(stat, schematic.targetLevel));
        }
        if (reward instanceof FishReward.TackleSchematic) {
            return schematicValue(ShopPricing.getPrice(
                    ((FishReward.TackleSchematic) reward).tackle));
        }
        if (reward instanceof FishReward.TackleReward) {
            ShopPricing.Price price = ShopPricing.getPrice(
                    ((FishReward.TackleReward) reward).tackle);
            return price == null ? SCHEMATIC_VALUE_FLOOR : Math.max(SCHEMATIC_VALUE_FLOOR,
                    price.credits);
        }
        if (reward instanceof FishReward.BackdropReward) {
            Backdrop backdrop = BackdropLoader.get(
                    ((FishReward.BackdropReward) reward).backdropId);
            int rank = backdrop == null ? 1 : backdrop.rarity.rank;
            return BACKDROP_VALUE_BASE + rank * BACKDROP_VALUE_PER_RANK;
        }
        if (reward instanceof FishReward.Blueprint) {
            return BLUEPRINT_VALUE;
        }
        if (reward instanceof FishReward.Commodity) {
            return FishRewardRoller.creditPayout(
                    ((FishReward.Commodity) reward).quantity * 120);
        }

        return SCHEMATIC_VALUE_FLOOR;
    }

    public static int rangeDataValue(FishSpec spec) {
        return Math.round(Math.max(60f, spec.baseValue) * RANGE_DATA_VALUE_MULT);
    }

    protected static int schematicValue(ShopPricing.Price price) {
        if (price == null) return SCHEMATIC_VALUE_FLOOR;

        return Math.max(SCHEMATIC_VALUE_FLOOR,
                Math.round(price.credits * SCHEMATIC_VALUE_MULT));
    }

    protected static void reserve(FishReward reward, Set<String> reservedSchematics,
                                  Set<String> reservedData) {
        FishRewardRoller.reserve(reward, reservedSchematics);
        FishRewardRoller.reserveLocationData(reward, reservedData);
    }

    protected static int countCharts(List<FishReward> rewards) {
        int count = 0;
        for (FishReward reward : rewards) {
            if (reward instanceof FishReward.LocationData) count++;
        }

        return count;
    }

    protected static int requestedFish(List<FishRequirement> asks) {
        int total = 0;

        if (asks != null) {
            for (FishRequirement ask : asks) {
                if (ask != null) total += Math.max(1, ask.count);
            }
        }

        return Math.max(1, total);
    }
}
