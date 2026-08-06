package catchrelease.campaign.fish.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a species' region/star-type/system-tag id sets (the same ones the spawner reads) into a
 * readable sentence. A blank set means "anything", matching the spawner's own interpretation.
 */
public class FishLocationSummary {

    /** The direction words, in the order a sentence wants them rather than the enum's. */
    protected static final String[] QUADRANTS = {"NE", "NW", "SE", "SW"};

    /** Star id to readable name; ids don't decompose consistently, so most are spelled out by hand. */
    public static String getStarName(String starType) {
        if (starType == null) return null;

        switch (starType.trim().toLowerCase()) {
            case "star_blue_giant": return "a blue giant";
            case "star_blue_supergiant": return "a blue supergiant";
            case "star_white": return "a white star";
            case "star_yellow": return "a yellow star";
            case "star_orange": return "an orange star";
            case "star_red_dwarf": return "a red dwarf";
            case "star_red_giant": return "a red giant";
            case "star_red_supergiant": return "a red supergiant";
            case "star_browndwarf": return "a brown dwarf";
            case "star_neutron": return "a neutron star";
            case "black_hole": return "a black hole";
            default: return "a " + starType.trim().toLowerCase()
                    .replace("star_", "").replace('_', ' ');
        }
    }

    /** The one-line answer to where this swims. */
    public static String describe(FishSpec spec) {
        if (spec == null) return "Nowhere anyone has written down.";

        List<String> clauses = new ArrayList<>();

        String where = describeRegions(spec.regions);
        clauses.add(where);

        //omitted entirely when unconstrained, rather than saying "under any star"
        String star = describeStars(spec.starTypes);
        if (star != null) clauses.add(star);

        String conditions = describeTags(spec.systemTags);
        if (conditions != null) clauses.add(conditions);

        return join(clauses, ", ") + ".";
    }

    /**
     * Regions collapsed into core/rim bands with quadrants, plus abyssal named separately (it's a
     * system property, not a quadrant).
     */
    protected static String describeRegions(Set<SectorRegion> regions) {
        if (regions == null || regions.isEmpty()) return "Anywhere in the sector";

        boolean abyssal = regions.contains(SectorRegion.ABYSSAL);

        Set<String> core = new LinkedHashSet<>();
        Set<String> rim = new LinkedHashSet<>();

        for (SectorRegion region : regions) {
            if (region == SectorRegion.ABYSSAL) continue;

            String quadrant = region.name().substring(region.name().length() - 2);
            if (region.isCore()) core.add(quadrant);
            else rim.add(quadrant);
        }

        if (core.isEmpty() && rim.isEmpty()) return abyssal ? "In the Abyss" : "Anywhere in the sector";

        List<String> parts = new ArrayList<>();

        if (core.size() == QUADRANTS.length && rim.size() == QUADRANTS.length) {
            parts.add("Anywhere in the sector");
        } else {
            if (!core.isEmpty()) parts.add(describeBand("the core", core));
            if (!rim.isEmpty()) parts.add(describeBand("the far reaches", rim));
        }

        if (abyssal) parts.add("in the Abyss");

        String joined = joinNatural(parts, "and");

        return Character.toUpperCase(joined.charAt(0)) + joined.substring(1);
    }

    /** One band, with its directions only if it is not the whole of it. */
    protected static String describeBand(String band, Set<String> quadrants) {
        if (quadrants.size() == QUADRANTS.length) return "in " + band;

        List<String> directions = new ArrayList<>();
        for (String quadrant : QUADRANTS) {
            if (quadrants.contains(quadrant)) directions.add(getDirectionName(quadrant));
        }

        return "in " + band + " of the " + joinNatural(directions, "and");
    }

    public static String getDirectionName(String quadrant) {
        switch (quadrant) {
            case "NE": return "north-east";
            case "NW": return "north-west";
            case "SE": return "south-east";
            default: return "south-west";
        }
    }

    /** The sky half. Nothing listed means it does not care what it is swimming under. */
    protected static String describeStars(Set<String> starTypes) {
        if (starTypes == null || starTypes.isEmpty()) return null;

        List<String> names = new ArrayList<>();
        for (String starType : starTypes) {
            String name = getStarName(starType);
            if (name != null) names.add(name);
        }

        if (names.isEmpty()) return null;

        return "under " + joinNatural(names, "or");
    }

    /** Unknown tags are tidied into words rather than dropped - dropping would understate constraints. */
    protected static String describeTags(Set<String> systemTags) {
        if (systemTags == null || systemTags.isEmpty()) return null;

        List<String> names = new ArrayList<>();
        for (String tag : systemTags) {
            if (tag == null || tag.trim().isEmpty()) continue;
            names.add(getTagName(tag));
        }

        if (names.isEmpty()) return null;

        return "where the system is " + joinNatural(names, "and");
    }

    protected static String getTagName(String tag) {
        switch (tag.trim().toLowerCase()) {
            case "theme_ruins": return "built over ruins";
            case "theme_derelict": return "long abandoned";
            case "theme_remnant": return "held by remnants";
            case "has_slipstreams": return "crossed by slipstreams";
            case "nebula": return "sunk in nebula";
            default: return tag.trim().toLowerCase().replace("theme_", "").replace('_', ' ');
        }
    }

    /** Natural-language list join: "a and b", or "a, b, and c" for more than two. */
    protected static String joinNatural(List<String> parts, String word) {
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        //comma before the word if either half already contains "word", to avoid ambiguous runs
        if (parts.size() == 2) {
            boolean nested = parts.get(0).contains(" " + word + " ") || parts.get(1).contains(" " + word + " ");

            return parts.get(0) + (nested ? ", " : " ") + word + " " + parts.get(1);
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(i == parts.size() - 1 ? ", " + word + " " : ", ");
            out.append(parts.get(i));
        }

        return out.toString();
    }

    protected static String join(List<String> parts, String separator) {
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(separator);
            out.append(parts.get(i));
        }

        return out.toString();
    }
}
