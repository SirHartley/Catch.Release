package catchrelease.campaign.fish.data;

import com.fs.starfarer.api.impl.campaign.procgen.StarAge;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One row of data/campaign/fish.csv. Every criterion is "blank means anything" - a row with only an
 * id can be caught anywhere; narrow it with {@link SectorRegion}s, star colour, constellation age,
 * a coherence band, system tags, or which gear can reach it at all.
 * <p>
 * The axes stack, and that is the point of having several: a row that names four regions and
 * nothing else is a fish in half the sector, which is a fish with no home. Two narrow axes read as
 * a place somebody could describe - "old constellations out on the western rim, where the fabric is
 * thin" - where one wide one reads as noise.
 */
public class FishSpec {

    public String id;
    public String name;
    public String icon;
    public String desc;

    public Set<String> tags = new LinkedHashSet<>();
    public FishRarity rarity = FishRarity.COMMON;
    public float spawnWeight = 10f;

    //minigame behaviour
    public FishMotion motion = FishMotion.SMOOTH;
    public float motionSpeed = 1f;
    public float restlessness = 1f;

    /** Icon twitch multiplier; visual only. */
    public float jitter = 1f;

    /** World angle the art faces as drawn (0 = right, 90 = up, 180 = left). Anything that
     *  renders the sprite swimming subtracts this from the heading. Most of the art faces left. */
    public float spriteDirection = 180f;
    public float difficulty = 50f;
    public float progressRateMult = 1f;
    public float escapeRateMult = 1f;

    /** Length in metres, weight in kilograms; a catch rolls inside the range and value scales with it. */
    public float baseValue = 100f;
    public float lengthMin = 0.3f;
    public float lengthMax = 0.6f;
    public float weightMin = 0.5f;
    public float weightMax = 2f;

    //where it lives - all empty means "anywhere"
    public Set<StarColour> starColours = new LinkedHashSet<>();
    public Set<String> systemTags = new LinkedHashSet<>();
    public Set<SectorRegion> regions = new LinkedHashSet<>();

    /** Which ages of constellation it turns up in. Empty is any age, and so is a lone system. */
    public Set<StarAge> constellationAges = new LinkedHashSet<>();

    /**
     * The band of {@link Aberration} it will live in, 0 to 1. The default is the whole range, so a
     * row that says nothing is a fish that does not care how well reality is holding.
     */
    public float minAberration = 0f;
    public float maxAberration = 1f;

    /**
     * Which gear can reach it at all, or empty for either.
     * <p>
     * The same axis a buyer asks about - a specimen records what made it reachable, so a species
     * that only ever comes up out of a rupture and a buyer who only wants pond-caught are two halves
     * of one vocabulary rather than two systems that happen to agree.
     */
    public Set<CatchImplement> reachedBy = new LinkedHashSet<>();

    /** Falls back to id if unnamed. */
    public String getDisplayName() {
        return name == null || name.isEmpty() ? id : name;
    }

    /** Base type from tags (crab/mollusc/fish/other); "abyssal" is a qualifier, not its own type. */
    public String getTypeName() {
        String base = tags.contains("crab") ? "Crab"
                : tags.contains("mollusc") ? "Mollusc"
                : tags.contains("fish") ? "Fish" : "Other";

        return tags.contains("abyssal") ? "Abyssal " + base.toLowerCase() : base;
    }

    /**
     * Whether the row says anything at all about where this lives. What "location data" means: a
     * species with no criterion is everywhere, and there is nothing to sell, shade or write down.
     */
    public boolean hasHabitat() {
        return !regions.isEmpty() || !starColours.isEmpty() || !constellationAges.isEmpty()
                || !systemTags.isEmpty() || !reachedBy.isEmpty()
                || minAberration > 0f || maxAberration < 1f;
    }

    /** Whether this fish lives here at all, leaving aside what is being fished with. */
    public boolean matches(FishHabitat where) {
        return matches(where, null);
    }

    /**
     * Whether this fish can turn up here, on this gear.
     * <p>
     * One question with one answer, asked by the spawner deciding what a pond produces and by the
     * map deciding what to shade. They used to be two: the map tested the region alone and shaded
     * systems where the spawner would never have offered the fish.
     *
     * @param how what would be reaching it, or null to ignore the question - which is what the map
     *            wants, since a species lives where it lives whether or not the right rig is fitted
     */
    public boolean matches(FishHabitat where, CatchImplement how) {
        if (where == null) return false;

        //the abyss is the one place blank does not mean anything. Every other criterion left empty
        //widens the range; this one has to be asked for, because a species that says nothing about
        //where it lives is a species somebody could describe, and nothing anybody can describe
        //lives down there. Without it the deepest water in the game offers the same roach as a
        //core world does
        if (where.region == SectorRegion.ABYSSAL) {
            if (!regions.contains(SectorRegion.ABYSSAL)) return false;
        } else if (!regions.isEmpty()
                && (where.region == null || !regions.contains(where.region))) {
            return false;
        }
        if (!starColours.isEmpty() && !starColours.contains(where.star)) return false;

        //a system in no constellation has no age, which is not the same as being the wrong one -
        //but a row that asked for an age is asking for something this place cannot answer
        if (!constellationAges.isEmpty()
                && (where.age == null || !constellationAges.contains(where.age))) {
            return false;
        }

        if (where.aberration < minAberration || where.aberration > maxAberration) return false;

        if (how != null && !reachedBy.isEmpty() && !reachedBy.contains(how)) return false;

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
}
