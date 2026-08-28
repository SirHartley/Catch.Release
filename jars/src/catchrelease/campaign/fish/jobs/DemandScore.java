package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;

import java.util.List;

/**
 * One number for how hard a demand is to fill, shared by every quest kind. The unit is
 * anchored so one unmodified common scores {@link #COMMON_BASE}; everything else is a
 * multiplier on that. Reward budgets are derived from this score, so the anchor also
 * fixes the economy: score 10 pays what one requested fish always paid.
 */
public final class DemandScore {

    public static final float COMMON_BASE = 10f;

    // per-specimen base by the rarity actually demanded
    public static final float UNCOMMON_BASE = 16f;
    public static final float RARE_BASE = 26f;
    public static final float EPIC_BASE = 42f;
    public static final float LEGENDARY_BASE = 70f;

    // a named species is harder to find than any-of-its-rarity; a type only narrows
    public static final float SPECIFIC_SPECIES_MULT = 1.3f;
    public static final float TYPE_MULT = 1.1f;

    public static final float LOW_COHERENCE_MULT = 1.35f;
    public static final float ORIGIN_MULT = 1.25f;
    public static final float METHOD_MULT = 1.15f;
    public static final float IMPLEMENT_MULT = 1.15f;
    // a weight floor scores by how much of the sheet it excludes, so a floor only the
    // heaviest species clear costs far more than one most fish stroll over
    public static final float WEIGHT_FLOOR_BASE = 1.1f;
    public static final float WEIGHT_FLOOR_SPAN = 0.7f;
    public static final float LENGTH_FLOOR_MULT = 1.25f;
    public static final float SAME_SPECIES_MULT = 1.2f;

    // each specimen past the first costs a fraction: two commons are one-and-a-bit
    public static final float EXTRA_SPECIMEN_FRACTION = 0.6f;

    public enum Tier {

        EASY, MEDIUM, HARD, SEVERE;

        public boolean atLeast(Tier other) {
            return ordinal() >= other.ordinal();
        }
    }

    public static final float MEDIUM_FROM = 18f;
    public static final float HARD_FROM = 32f;
    public static final float SEVERE_FROM = 55f;

    private DemandScore() {
    }

    public static float of(List<FishRequirement> asks) {
        float total = 0f;

        if (asks != null) {
            for (FishRequirement ask : asks) {
                total += of(ask);
            }
        }

        return total;
    }

    public static float of(FishRequirement ask) {
        if (ask == null) return 0f;

        // a choice of alternatives is only as hard as its easiest branch
        if (!ask.anyOf.isEmpty()) {
            float easiest = Float.MAX_VALUE;
            for (FishRequirement alternative : ask.anyOf) {
                if (alternative == null) continue;

                easiest = Math.min(easiest, perSpecimen(alternative));
            }

            if (easiest == Float.MAX_VALUE) return 0f;

            return withCount(easiest, ask);
        }

        return withCount(perSpecimen(ask), ask);
    }

    protected static float withCount(float perSpecimen, FishRequirement ask) {
        int count = Math.max(1, ask.count);
        float countMult = 1f + EXTRA_SPECIMEN_FRACTION * (count - 1);

        if (ask.sameSpecies && ask.speciesId == null && count > 1) {
            countMult *= SAME_SPECIES_MULT;
        }

        return perSpecimen * countMult;
    }

    protected static float perSpecimen(FishRequirement ask) {
        float score = rarityBase(demandedRarity(ask));

        if (ask.speciesId != null) score *= SPECIFIC_SPECIES_MULT;
        else if (ask.tag != null && !"fish".equals(ask.tag)) score *= TYPE_MULT;

        score *= gradeMult(ask.minGrade);

        if (ask.lowCoherence) score *= LOW_COHERENCE_MULT;
        if (ask.origin != null) score *= ORIGIN_MULT;
        if (ask.method != null) score *= METHOD_MULT;
        if (ask.implement != null) score *= IMPLEMENT_MULT;
        if (ask.minWeight > 0f) score *= weightFloorMult(ask.minWeight);
        if (ask.minLength > 0f) score *= LENGTH_FLOOR_MULT;

        return score;
    }

    protected static float weightFloorMult(float minWeight) {
        int total = 0;
        int excluded = 0;

        for (catchrelease.campaign.fish.data.FishSpec spec
                : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.tags.contains("abyssal")) continue;

            total++;
            if (spec.weightMax < minWeight) excluded++;
        }

        if (total == 0) return WEIGHT_FLOOR_BASE;

        return WEIGHT_FLOOR_BASE + WEIGHT_FLOOR_SPAN * (excluded / (float) total);
    }

    protected static FishRarity demandedRarity(FishRequirement ask) {
        if (ask.speciesId != null && FishSpecLoader.getFishSpec(ask.speciesId) != null) {
            return FishSpecLoader.getFishSpec(ask.speciesId).rarity;
        }

        return ask.minRarity == null ? FishRarity.COMMON : ask.minRarity;
    }

    public static float rarityBase(FishRarity rarity) {
        if (rarity == null) return COMMON_BASE;

        switch (rarity) {
            case UNCOMMON: return UNCOMMON_BASE;
            case RARE: return RARE_BASE;
            case EPIC: return EPIC_BASE;
            case LEGENDARY: return LEGENDARY_BASE;
            default: return COMMON_BASE;
        }
    }

    protected static float gradeMult(FishGrade grade) {
        if (grade == null) return 1f;

        // minGrade is a floor: demanding at-least-terrible costs nothing, at-least-
        // exceptional is a top-of-the-size-roll ask and doubles the specimen
        switch (grade) {
            case POOR: return 1.05f;
            case AVERAGE: return 1.2f;
            case FINE: return 1.5f;
            case EXCEPTIONAL: return 2.1f;
            default: return 1f;
        }
    }

    public static Tier tierOf(float score) {
        if (score >= SEVERE_FROM) return Tier.SEVERE;
        if (score >= HARD_FROM) return Tier.HARD;
        if (score >= MEDIUM_FROM) return Tier.MEDIUM;

        return Tier.EASY;
    }
}
