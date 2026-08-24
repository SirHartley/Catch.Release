package catchrelease.campaign.fish.data;

import com.fs.starfarer.api.impl.campaign.procgen.StarAge;

import java.util.LinkedHashSet;
import java.util.Set;

public class FishSpec {

    // cumulative relaxation rungs for FishRanges: each level also grants everything below it
    public static final int RELAX_NONE = 0;
    public static final int RELAX_AGES = 1;
    public static final int RELAX_ABERRATION = 2;
    public static final int RELAX_STARS = 3;
    public static final int RELAX_REGIONS = 4;
    public static final float RELAX_ABERRATION_WIDTH = 0.25f;

    public String id;
    public String name;
    public String icon;
    public String desc;
    public Set<String> tags = new LinkedHashSet<>();
    public FishRarity rarity = FishRarity.COMMON;
    public float spawnWeight = 10f;

    public FishMotion motion = FishMotion.SMOOTH;
    public float motionSpeed = 1f;

    public float restlessness = 1f;
    public float jitter = 1f;
    public float spriteDirection = 180f;

    public float difficulty = 50f;
    public float progressRateMult = 1f;
    public float escapeRateMult = 1f;
    public float baseValue = 100f;

    public float lengthMin = 0.3f;
    public float lengthMax = 0.6f;

    public float weightMin = 0.5f;
    public float weightMax = 2f;

    public Set<StarColour> starColours = new LinkedHashSet<>();
    public Set<String> systemTags = new LinkedHashSet<>();
    public Set<SectorRegion> regions = new LinkedHashSet<>();
    public Set<StarAge> constellationAges = new LinkedHashSet<>();
    public float minAberration = 0f;
    public float maxAberration = 1f;
    public Set<CatchImplement> reachedBy = new LinkedHashSet<>();

    public String getDisplayName() {
        return name == null || name.isEmpty() ? id : name;
    }

    public String getTypeName() {
        String base = tags.contains("crab") ? "Crab"
                : tags.contains("mollusc") ? "Mollusc"
                : tags.contains("fish") ? "Fish" : "Other";

        return tags.contains("abyssal") ? "Abyssal " + base.toLowerCase() : base;
    }

    public boolean hasHabitat() {
        return !regions.isEmpty() || !starColours.isEmpty() || !constellationAges.isEmpty()
                || !systemTags.isEmpty() || !reachedBy.isEmpty()
                || minAberration > 0f || maxAberration < 1f;
    }

    public boolean matches(FishHabitat where) {
        return matches(where, null);
    }

    public boolean matches(FishHabitat where, CatchImplement how) {
        return matches(where, how, RELAX_NONE);
    }

    /** Relaxed matching for FishRanges - the abyss boundary never relaxes, in either direction. */
    public boolean matches(FishHabitat where, CatchImplement how, int relax) {
        if (where == null) return false;

        if (where.region == SectorRegion.ABYSSAL) {
            if (!regions.contains(SectorRegion.ABYSSAL)) return false;
        } else if (!regions.isEmpty()
                && (where.region == null || !regions.contains(where.region))) {
            if (relax < RELAX_REGIONS || regions.contains(SectorRegion.ABYSSAL)) return false;
        }
        if (relax < RELAX_STARS
                && !starColours.isEmpty() && !starColours.contains(where.star)) {
            return false;
        }

        if (relax < RELAX_AGES && !constellationAges.isEmpty()
                && (where.age == null || !constellationAges.contains(where.age))) {
            return false;
        }

        float slack = relax >= RELAX_ABERRATION ? RELAX_ABERRATION_WIDTH : 0f;
        if (where.aberration < minAberration - slack
                || where.aberration > maxAberration + slack) {
            return false;
        }

        if (!canBeReachedBy(how)) return false;

        if (!systemTags.isEmpty()) {
            if (where.tags == null) return false;

            boolean found = false;
            for (String tag : systemTags) {
                if (where.tags.contains(tag)) {
                    found = true;
                    break;
                }
            }

            if (!found) return false;
        }

        return true;
    }

    public boolean canBeReachedBy(CatchImplement how) {
        return how == null || reachedBy.isEmpty() || reachedBy.contains(how);
    }
}
