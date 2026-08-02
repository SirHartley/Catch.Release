package catchrelease.campaign.fish.data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One row of data/campaign/fish.csv: what a fish is, where it turns up, and how it behaves once it is
 * on the hook.
 * <p>
 * Every criterion is "blank means anything", so a row that fills in nothing but its id can be caught
 * anywhere. Narrow a fish down by listing the star types, system tags or {@link SectorRegion}s it
 * belongs to.
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

    /** Multiplier on how much the fish's icon twitches. Visual only - it never moves the real fish. */
    public float jitter = 1f;
    public float difficulty = 50f;
    public float progressRateMult = 1f;
    public float escapeRateMult = 1f;

    /**
     * What one is worth before anything is known about the individual, and the range a specimen of
     * this species comes in. Length is metres and weight kilograms; a catch rolls inside the range
     * and its value moves with where in that range it landed.
     */
    public float baseValue = 100f;
    public float lengthMin = 0.3f;
    public float lengthMax = 0.6f;
    public float weightMin = 0.5f;
    public float weightMax = 2f;

    //where it lives - all empty means "anywhere"
    public Set<String> starTypes = new LinkedHashSet<>();
    public Set<String> systemTags = new LinkedHashSet<>();
    public Set<SectorRegion> regions = new LinkedHashSet<>();

    /** The name to show, falling back to the id for rows that have not been named yet. */
    public String getDisplayName() {
        return name == null || name.isEmpty() ? id : name;
    }

    /**
     * Whether this fish can turn up in the given system.
     *
     * @param starType   the star's planet type id, e.g. "star_red" - null for a system without one
     * @param systemTags tags on the system
     * @param region     which part of the sector the system is in
     */
    public boolean matches(String starType, Set<String> systemTags, SectorRegion region) {
        if (!starTypes.isEmpty() && (starType == null || !starTypes.contains(starType))) return false;
        if (!regions.isEmpty() && (region == null || !regions.contains(region))) return false;

        if (!this.systemTags.isEmpty()) {
            if (systemTags == null) return false;

            //any one of the listed tags is enough
            boolean found = false;
            for (String tag : this.systemTags) {
                if (systemTags.contains(tag)) {
                    found = true;
                    break;
                }
            }

            if (!found) return false;
        }

        return true;
    }
}
