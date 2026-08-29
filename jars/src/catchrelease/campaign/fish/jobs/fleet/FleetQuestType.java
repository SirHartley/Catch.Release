package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.jobs.DemandScore;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.jobs.FishRewardRoller;
import catchrelease.campaign.fish.jobs.QuestDuration;
import catchrelease.campaign.fish.jobs.QuestRewards;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public enum FleetQuestType {

    LAST_ENTRY(FleetTypes.SCAVENGER_SMALL, 1f),
    ESCROW(FleetTypes.SCAVENGER_SMALL, 1.3f),
    INTERMENT(FleetTypes.TRADE_SMALL, 1f),
    CALIBRATION_PAIR(FleetTypes.SCAVENGER_SMALL, 1f),
    MUTINY_POT(FleetTypes.TRADE_SMALL, 1.1f),
    TRIBUTE(FleetTypes.SCAVENGER_SMALL, 1.25f),
    REFERENCE_SPECIMEN(FleetTypes.SCAVENGER_SMALL, 1f),
    QUIET_SHIP(FleetTypes.SCAVENGER_SMALL, 0.9f),
    EXHIBIT(FleetTypes.TRADE_SMALL, 1.15f),
    HEADLINER(FleetTypes.SCAVENGER_SMALL, 1.15f),
    FOLLOWER(FleetTypes.SUPPLY_FLEET, 1.2f),
    STATE_DINNER(FleetTypes.TRADE_LINER, 1.15f),
    CLAIM_ASSAY(FleetTypes.SCAVENGER_MEDIUM, 1.25f),
    MANDATE(FleetTypes.ACADEMY_FLEET, 1.1f),
    PARLEY_FISH(FleetTypes.PATROL_MEDIUM, 1.2f),
    STRANDED(FleetTypes.TRADE_SMALL, 1.15f),
    SEEKER(FleetTypes.SCAVENGER_SMALL, 1.25f),
    QUOTA(FleetTypes.TRADE_SMALL, 1f),
    STARVING(FleetTypes.TRADE_SMALL, 1.1f),
    SCAVENGER_ENGINE(FleetTypes.SCAVENGER_SMALL, 1.2f),
    COLLECTOR(FleetTypes.TRADE_SMALL, 1.3f),
    WAGER(FleetTypes.SCAVENGER_SMALL, 1.15f);

    private static final FleetQuestType[] LOCAL_OFFERS = {
            LAST_ENTRY,
            ESCROW,
            INTERMENT,
            CALIBRATION_PAIR,
            MUTINY_POT,
            TRIBUTE,
            REFERENCE_SPECIMEN,
            QUIET_SHIP,
            EXHIBIT,
            HEADLINER,
            SEEKER,
            QUOTA,
            STARVING,
            COLLECTOR,
            WAGER
    };

    public final String fleetType;
    public final float rewardBudgetMult;

    FleetQuestType(String fleetType, float rewardBudgetMult) {
        this.fleetType = fleetType;
        this.rewardBudgetMult = rewardBudgetMult;
    }

    public static final float HOME_SPECIES_WEIGHT = 4f;
    public static final float LAST_ENTRY_MAX_LY = 75f;
    public static final float QUIET_SHIP_COOLDOWN_DAYS = 120f;
    public static final List<String> BODY_TYPE_TAGS = List.of("fish", "crab", "mollusc");

    /** Picks from the home system or its nearest neighbour, preferring home. */
    protected static String pickNearbySpecies(Random random, StarSystemAPI home,
                                              FishRarity maximum) {
        if (home == null) return pickSpecies(random, null, maximum);

        StarSystemAPI adjacent = nearestSystemTo(home);
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>(random);

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (maximum != null && spec.rarity.rank > maximum.rank) continue;

            if (FishRanges.matches(spec, home, null)) {
                picker.add(spec, HOME_SPECIES_WEIGHT);
            } else if (adjacent != null && FishRanges.matches(spec, adjacent, null)) {
                picker.add(spec, 1f);
            }
        }

        FishSpec pick = picker.pick();

        return pick == null ? null : pick.id;
    }

    protected static StarSystemAPI nearestSystemTo(StarSystemAPI home) {
        StarSystemAPI best = null;
        float bestLY = Float.MAX_VALUE;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system == null || system == home) continue;

            float ly = Misc.getDistanceLY(home.getLocation(), system.getLocation());
            if (ly < bestLY) {
                bestLY = ly;
                best = system;
            }
        }

        return best;
    }

    public static boolean isNearAbyssal(StarSystemAPI home) {
        if (home == null || Global.getSector() == null) return false;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system == null || !system.hasTag(Tags.SYSTEM_ABYSSAL)) continue;
            if (Misc.getDistanceLY(home.getLocation(), system.getLocation())
                    <= QuestDuration.MAX_SENSIBLE_LY) return true;
        }

        return false;
    }

    protected static String pickSpecies(Random random, FishRarity minimum, FishRarity maximum) {
        return pickSpecies(random, minimum, maximum, null);
    }

    protected static String pickSpecies(Random random, FishRarity minimum, FishRarity maximum,
                                        CatchImplement implement) {
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>(random);

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (minimum != null && spec.rarity.rank < minimum.rank) continue;
            if (maximum != null && spec.rarity.rank > maximum.rank) continue;
            if (!spec.canBeReachedBy(implement)) continue;

            picker.add(spec, 1f);
        }

        FishSpec pick = picker.pick();

        return pick == null ? null : pick.id;
    }

    protected static String pickBodyType(Random random, StarSystemAPI home) {
        List<String> available = bodyTypesIn(home);
        if (available.isEmpty()) available = bodyTypesIn(nearestSystemTo(home));
        if (available.isEmpty()) available = BODY_TYPE_TAGS;

        return available.get(random.nextInt(available.size()));
    }

    protected static List<String> bodyTypesIn(StarSystemAPI system) {
        if (system == null) return List.of();

        List<String> available = new java.util.ArrayList<>();
        for (String tag : BODY_TYPE_TAGS) {
            for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
                if (spec == null || !spec.hasHabitat() || !spec.tags.contains(tag)) continue;
                if (!FishRanges.matches(spec, system, null)) continue;

                available.add(tag);
                break;
            }
        }

        return available;
    }

    protected static boolean canBeSatisfiedIn(FishRequirement ask, StarSystemAPI system) {
        if (ask == null || system == null) return false;

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || !spec.hasHabitat() || !ask.couldBeSatisfiedBy(spec)) continue;
            if (FishRanges.matches(spec, system, ask.implement)) return true;
        }

        return false;
    }

    /** Shapes this quest type around the target score, or returns null if it cannot. */
    public FishRequirement rollAsk(Random random, float target, StarSystemAPI home, int attempt) {
        FishRequirement ask = new FishRequirement();

        switch (this) {
            case LAST_ENTRY: {
                FishRarity shelf = target >= 60f ? FishRarity.EPIC
                        : target >= 35f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) return null;
                ask.minGrade = FishGrade.AVERAGE;
                break;
            }

            case ESCROW: {
                float rarityTarget = attempt == 1 ? target / FleetQuest.ASK_BACKOFF : target;
                FishRarity shelf = rarityTarget >= 55f ? FishRarity.EPIC
                        : rarityTarget >= 30f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) return null;
                if (attempt == 0 && target >= 30f) ask.minGrade = FishGrade.FINE;
                break;
            }

            case INTERMENT: {
                FishRarity shelf = target >= 52f ? FishRarity.EPIC
                        : target >= 28f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) return null;
                break;
            }

            case CALIBRATION_PAIR:
                ask.count = 2;
                ask.sameSpecies = true;
                if (target >= 30f) ask.lowCoherence = true;
                break;

            case MUTINY_POT: {
                float rarityTarget = attempt == 1 ? target / FleetQuest.ASK_BACKOFF : target;
                FishRarity shelf = rarityTarget >= 58f ? FishRarity.EPIC
                        : rarityTarget >= 32f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                FishSpec spec = FishSpecLoader.getFishSpec(ask.speciesId);
                if (spec == null) return null;

                if (attempt == 0 && target >= 25f) {
                    float fraction = 0.55f + Math.min(0.3f, (target - 25f) / 100f);
                    float floor = Math.round(spec.weightMax * fraction * 10f) / 10f;
                    ask.minWeight = Math.max(spec.weightMin,
                            Math.min(spec.weightMax, floor));
                }
                break;
            }

            case TRIBUTE: {
                FishRarity shelf = target >= 55f ? FishRarity.EPIC
                        : target >= 30f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) return null;
                if (target >= 22f) ask.minGrade = FishGrade.FINE;
                break;
            }

            case REFERENCE_SPECIMEN: {
                FishRarity shelf = target >= 30f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf, CatchImplement.POND);
                if (ask.speciesId == null) return null;
                ask.implement = CatchImplement.POND;
                break;
            }

            case QUIET_SHIP:
                ask.tag = pickBodyType(random, home);
                if (target >= 24f) ask.minGrade = FishGrade.AVERAGE;
                break;

            case EXHIBIT: {
                ask.speciesId = pickSpecies(random, FishRarity.UNCOMMON, FishRarity.UNCOMMON);
                FishSpec spec = FishSpecLoader.getFishSpec(ask.speciesId);
                if (spec == null) return null;

                if (target >= 26f) {
                    float fraction = 0.65f + Math.min(0.2f, (target - 26f) / 100f);
                    float floor = Math.round(spec.lengthMax * fraction * 10f) / 10f;
                    ask.minLength = Math.max(spec.lengthMin,
                            Math.min(spec.lengthMax, floor));
                }
                break;
            }

            case HEADLINER:
                ask.minRarity = attempt == 0 ? FishRarity.RARE : FishRarity.UNCOMMON;
                if (attempt == 0 && target >= 45f) ask.minGrade = FishGrade.FINE;
                break;

            case FOLLOWER: {
                ask.speciesId = pickNearbySpecies(random, home, FishRarity.RARE);
                FishSpec spec = FishSpecLoader.getFishSpec(ask.speciesId);
                if (spec == null) return null;

                if (target >= 28f) {
                    float fraction = 0.55f + Math.min(0.3f, (target - 28f) / 100f);
                    float floor = Math.round(spec.weightMax * fraction * 10f) / 10f;
                    ask.minWeight = Math.max(spec.weightMin,
                            Math.min(spec.weightMax, floor));
                }
                break;
            }

            case STATE_DINNER:
                ask.speciesId = pickNearbySpecies(random, home, FishRarity.COMMON);
                if (ask.speciesId == null) return null;
                ask.count = DemandScore.countFor(target, DemandScore.UNCOMMON_BASE, 2, 6);
                if (target >= 35f) ask.minGrade = FishGrade.FINE;
                break;

            case CLAIM_ASSAY:
                if (home == null) return null;
                ask.caughtSystemId = home.getId();
                if (target >= 30f) ask.minRarity = FishRarity.UNCOMMON;
                if (!canBeSatisfiedIn(ask, home)) return null;
                break;

            case MANDATE:
                ask.origin = SectorRegion.ABYSSAL;
                if (target >= 40f) ask.minRarity = FishRarity.UNCOMMON;
                break;

            case PARLEY_FISH:
                if (home == null) return null;
                if (target >= 26f) ask.minGrade = FishGrade.AVERAGE;
                break;

            case STRANDED:
            case SCAVENGER_ENGINE:
                // Distress jobs add quantity instead of asking for distant or rarer fish.
                ask.count = target >= 24f ? 2 : 1;
                ask.speciesId = pickNearbySpecies(random, home, FishRarity.UNCOMMON);
                if (ask.speciesId == null) return null;
                break;

            case STARVING:
                ask.count = DemandScore.countFor(target, DemandScore.COMMON_BASE, 3, 8);
                break;

            case QUOTA:
                ask.minGrade = FishGrade.FINE;
                ask.count = DemandScore.countFor(target, DemandScore.COMMON_BASE * 1.5f, 2, 6);
                break;

            case COLLECTOR: {
                FishRarity shelf = target >= 55f ? FishRarity.EPIC
                        : target >= 30f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.count = 1;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) {
                    ask.speciesId = pickSpecies(random, FishRarity.UNCOMMON, null);
                }
                if (target >= 70f) ask.minGrade = FishGrade.FINE;
                break;
            }

            case SEEKER: {
                FishRarity shelf = target >= 45f ? FishRarity.EPIC
                        : target >= 25f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) return null;
                if (target >= 60f) ask.minGrade = FishGrade.FINE;
                break;
            }

            case WAGER:
                ask.count = 2;
                ask.sameSpecies = true;
                if (target >= 25f) ask.minGrade = FishGrade.FINE;
                if (target >= 40f) ask.minRarity = FishRarity.UNCOMMON;
                break;

            default:
                ask.count = 1;
        }

        return ask;
    }

    public List<FishReward> rollFixedRewards(Random random, int round) {
        if (this == HEADLINER) return FishRewardRoller.rollBackdropReward(random);
        if (this == QUIET_SHIP || this == MANDATE) {
            return FishRewardRoller.rollSchematic(random);
        }
        if (this != LAST_ENTRY && !(this == CALIBRATION_PAIR && round == 0)) return List.of();

        return FishRewardRoller.rollLocationData(random, 1, FishRewardRoller.VALUE_PER_FISH);
    }

    public QuestRewards.Request createRewardRequest(List<FishRequirement> asks, Random random) {
        return createRewardRequest(asks, random, 0);
    }

    public QuestRewards.Request createRewardRequest(List<FishRequirement> asks, Random random,
                                                    int round) {
        QuestRewards.Request request = new QuestRewards.Request(asks)
                .fixAll(rollFixedRewards(random, round))
                .budgetMult(rewardBudgetMult)
                .random(random);

        if (this == ESCROW) {
            request.exclude(QuestRewards.Kind.RANGE_DATA, QuestRewards.Kind.BACKDROP);
        }
        if (this == REFERENCE_SPECIMEN) {
            request.exclude(QuestRewards.Kind.BACKDROP, QuestRewards.Kind.BLUEPRINT);
        }
        if (this == EXHIBIT) request.exclude(QuestRewards.Kind.BACKDROP);
        if (this == HEADLINER) {
            request.exclude(QuestRewards.Kind.BACKDROP);
            request.tierFloor(DemandScore.Tier.HARD);
        }
        if (this == FOLLOWER) {
            request.exclude(QuestRewards.Kind.BACKDROP);
            request.tierFloor(DemandScore.Tier.MEDIUM);
        }
        if (this == STATE_DINNER) {
            request.exclude(QuestRewards.Kind.BLUEPRINT);
            request.tierFloor(DemandScore.Tier.MEDIUM);
        }
        if (this == QUOTA) request.exclude(QuestRewards.Kind.BLUEPRINT);
        if (this == CLAIM_ASSAY) {
            request.exclude(QuestRewards.Kind.RANGE_DATA, QuestRewards.Kind.BACKDROP);
        }
        if (this == MANDATE) request.tierFloor(DemandScore.Tier.MEDIUM);
        if (this == CALIBRATION_PAIR && round > 0) request.budgetMult(0.5f);
        if (this == CALIBRATION_PAIR && round == 0 && !asks.isEmpty()
                && asks.get(0).lowCoherence) {
            request.tierFloor(DemandScore.Tier.HARD);
        }

        return request;
    }

    public QuestRewards.Request createCounterRewardRequest(List<FishRequirement> asks,
                                                           Random random) {
        return new QuestRewards.Request(asks).budgetMult(0.7f)
                .exclude(QuestRewards.Kind.BLUEPRINT).random(random);
    }

    public boolean usesTradeConvoy() {
        return this == INTERMENT || this == EXHIBIT || hasCounteroffer();
    }

    public boolean usesBosunContact() {
        return hasCounteroffer();
    }

    public boolean hasCounteroffer() {
        return this == MUTINY_POT;
    }

    public boolean hasFollowup() {
        return this == CALIBRATION_PAIR;
    }

    public boolean requiresIndependentFleet() {
        return this == LAST_ENTRY || this == ESCROW || this == INTERMENT
                || this == CALIBRATION_PAIR || this == MUTINY_POT || this == TRIBUTE
                || this == REFERENCE_SPECIMEN || this == QUIET_SHIP || this == EXHIBIT
                || this == HEADLINER;
    }

    public float getMaximumTravelLY() {
        return this == LAST_ENTRY ? LAST_ENTRY_MAX_LY
                : this == CALIBRATION_PAIR ? Float.MAX_VALUE
                : QuestDuration.MAX_SENSIBLE_LY;
    }

    public float getOfferCooldownDays() {
        return this == QUIET_SHIP ? QUIET_SHIP_COOLDOWN_DAYS
                : FleetQuestSpawner.COOLDOWN_DAYS;
    }

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<FleetQuestType> getLocalOffers() {
        return List.of(LOCAL_OFFERS);
    }

    public static FleetQuestType getLocalOffer(String id) {
        if (id == null) return null;

        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (FleetQuestType type : LOCAL_OFFERS) {
            if (type.getId().equals(wanted)) return type;
        }

        return null;
    }

    public static FleetQuestType rollAny(Random random) {
        return LOCAL_OFFERS[random.nextInt(LOCAL_OFFERS.length)];
    }
}
