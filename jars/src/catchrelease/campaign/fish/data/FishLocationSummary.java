package catchrelease.campaign.fish.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a species can be caught, said out loud.
 * <p>
 * The table states this as three sets of ids - the regions it lives in, the star types it will
 * come out under, and whatever tags its system has to carry - which is the right shape for deciding
 * whether a fish belongs somewhere and a poor one for telling somebody where to go looking. Nobody
 * reads CORE_NE, RIM_SW, star_browndwarf and works out that it means the inner north-east or the
 * outer south-west.
 * <p>
 * So this reads the same sets the spawner does and says the sentence they add up to. Every criterion
 * is "blank means anything", exactly as it is when the spawner asks - a row that names nothing is
 * caught anywhere, and this says so rather than listing all nine regions back.
 */
public class FishLocationSummary {

    /** The direction words, in the order a sentence wants them rather than the enum's. */
    protected static final String[] QUADRANTS = {"NE", "NW", "SE", "SW"};

    /**
     * What a star id is called by somebody standing under it.
     * <p>
     * Written out rather than prettified from the id, because the ids do not all decompose the same
     * way - star_browndwarf has no underscore where the others do, and star_white is a white star
     * rather than a white. Anything not named here falls back to a tidied id, so a new row in the
     * table shows up as words rather than as nothing.
     */
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

        //nothing said about the sky when the sky does not matter. "Under any star" is a clause that
        //takes a line to read and rules nothing out, and a sentence made of those is one nobody
        //finishes - the absence of a condition is already the answer
        String star = describeStars(spec.starTypes);
        if (star != null) clauses.add(star);

        String conditions = describeTags(spec.systemTags);
        if (conditions != null) clauses.add(conditions);

        return join(clauses, ", ") + ".";
    }

    /**
     * The place half.
     * <p>
     * The abyss is not a corner of the map but a property of a system, so it is named on its own
     * terms and never given a direction. Everything else collapses: all of the inner band is "the
     * core" rather than four quadrants listed out, and a band the fish covers entirely does not
     * need its directions saying at all.
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

        //said last, because a fish that is in the abyss as well as somewhere else is really a fish
        //from somewhere else that also turns up down there
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

    protected static String getDirectionName(String quadrant) {
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

    /**
     * Whatever else the system has to be, for the rows that ask for it.
     * <p>
     * Left generic on purpose: the tags come from the game's own vocabulary and the table is free to
     * name any of them, so an unknown one is tidied into words rather than dropped. A dropped
     * condition would be worse than an ugly one - it would say a fish is easier to find than it is.
     */
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

    /**
     * A list the way a person would say it: two joined by the word, more than two separated by
     * commas with the word before the last. Written out because "the south-east and south-west and
     * in the Abyss" is what the naive version produces, and it reads as a mistake.
     */
    protected static String joinNatural(List<String> parts, String word) {
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        //a comma before the word when either half already contains one, or "the south-east and
        //south-west and in the Abyss" comes out sounding like somebody lost their place
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
