package catchrelease.campaign.fish.data;

import com.fs.starfarer.api.impl.campaign.procgen.StarAge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class FishLocationSummary {


    protected static final String[] QUADRANTS = {"NE", "NW", "SE", "SW"};


    public static String describe(FishSpec spec) {
        if (spec == null) return "Nowhere anyone has written down.";

        List<String> clauses = new ArrayList<>();

        String where = describeRegions(spec.regions);
        clauses.add(where);

        // each omitted entirely when unconstrained, rather than saying "under any star"
        addIfAny(clauses, describeStars(spec.starColours));
        addIfAny(clauses, describeAges(spec.constellationAges));
        addIfAny(clauses, describeCoherence(spec.minAberration, spec.maxAberration));
        addIfAny(clauses, describeTags(spec.systemTags));
        addIfAny(clauses, describeReach(spec.reachedBy));

        return join(clauses, ", ") + ".";
    }

    protected static void addIfAny(List<String> clauses, String clause) {
        if (clause != null) clauses.add(clause);
    }


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


    protected static String describeStars(Set<StarColour> starColours) {
        if (starColours == null || starColours.isEmpty()) return null;

        List<String> names = new ArrayList<>();
        for (StarColour colour : starColours) {
            if (colour != null) names.add(colour.name);
        }

        if (names.isEmpty()) return null;

        return "under " + joinNatural(names, "or");
    }


    protected static String describeAges(Set<StarAge> ages) {
        if (ages == null || ages.isEmpty() || ages.size() >= 3) return null;

        List<String> names = new ArrayList<>();
        for (StarAge age : ages) {
            switch (age) {
                case YOUNG: names.add("young"); break;
                case OLD: names.add("old"); break;
                default: names.add("middle-aged"); break;
            }
        }

        return "in " + joinNatural(names, "or") + " constellations";
    }


    protected static String describeCoherence(float minAberration, float maxAberration) {
        boolean floor = minAberration > 0f;
        boolean ceiling = maxAberration < 1f;

        if (!floor && !ceiling) return null;

        if (floor && ceiling) return "where coherence is unsettled but not gone";
        if (floor) return "only where coherence is failing";

        return "only where coherence holds";
    }


    protected static String describeReach(Set<CatchImplement> reachedBy) {
        if (reachedBy == null || reachedBy.isEmpty() || reachedBy.size() > 1) return null;

        CatchImplement only = reachedBy.iterator().next();

        if (only == CatchImplement.POND) return "and only ever out of a rupture";

        return "and only ever loose in the dark, under a breach lamp";
    }


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


    protected static String joinNatural(List<String> parts, String word) {
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        // comma before the word if either half already contains "word", to avoid ambiguous runs
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
