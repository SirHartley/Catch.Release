package catchrelease.campaign.fish.data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One row of data/campaign/fish.csv. Every criterion is "blank means anything" - a row with only an
 * id can be caught anywhere; narrow it with star types, system tags, or {@link SectorRegion}s.
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
    public Set<String> starTypes = new LinkedHashSet<>();
    public Set<String> systemTags = new LinkedHashSet<>();
    public Set<SectorRegion> regions = new LinkedHashSet<>();

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
     * Whether this fish can turn up in the given system.
     *
     * @param starType   star's planet type id (e.g. "star_red"), null if none
     * @param systemTags tags on the system
     * @param region     part of the sector the system is in
     */
    public boolean matches(String starType, Set<String> systemTags, SectorRegion region) {
        if (!starTypes.isEmpty() && (starType == null || !starTypes.contains(starType))) return false;
        if (!regions.isEmpty() && (region == null || !regions.contains(region))) return false;

        if (!this.systemTags.isEmpty()) {
            if (systemTags == null) return false;

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
