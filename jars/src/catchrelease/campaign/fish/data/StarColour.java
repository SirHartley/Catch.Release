package catchrelease.campaign.fish.data;

/**
 * What a system's sun looks like, as somebody standing under it would say it.
 * <p>
 * The table used to name star types outright, and every row that did was really reaching for a
 * colour - "red dwarf and brown dwarf" is dim and cold, "blue giant, blue supergiant and white" is
 * hot and bright. Naming the colour says the thing that was meant, in one word instead of three,
 * and a star type nobody thought of lands in the right group on its own.
 * <p>
 * {@link #NONE} is a real answer rather than a missing one: a system with no star is a place, and a
 * thing can live there and nowhere else.
 */
public enum StarColour {

    BLUE("a blue star"),
    WHITE("a white star"),
    YELLOW("a yellow star"),
    ORANGE("an orange star"),
    RED("a red star"),
    BROWN("a brown dwarf"),
    NEUTRON("a neutron star"),
    BLACK_HOLE("a black hole"),

    /** No sun at all - a nebula system, or deep space between the constellations. */
    NONE("no sun at all");

    /** How a person would finish "under …". */
    public final String name;

    StarColour(String name) {
        this.name = name;
    }

    /**
     * The colour of a star planet-type id. Size is dropped on purpose - a red supergiant and a red
     * dwarf are wildly different stars and the same colour of sky, and the sky is what is being
     * fished under.
     */
    public static StarColour of(String starType) {
        if (starType == null) return NONE;

        switch (starType.trim().toLowerCase()) {
            case "star_blue_giant":
            case "star_blue_supergiant":
                return BLUE;
            case "star_white":
                return WHITE;
            case "star_yellow":
                return YELLOW;
            case "star_orange":
            case "star_orange_giant":
                return ORANGE;
            case "star_red_dwarf":
            case "star_red_giant":
            case "star_red_supergiant":
                return RED;
            case "star_browndwarf":
                return BROWN;
            case "star_neutron":
                return NEUTRON;
            case "black_hole":
                return BLACK_HOLE;
            default:
                //an unrecognised star is still a star, and calling it NONE would put a fish that
                //wants an empty sky under one it has never seen
                return WHITE;
        }
    }

    /** Parses a name from the fish table, or null if it is not one of ours. */
    public static StarColour parse(String name) {
        if (name == null) return null;

        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
