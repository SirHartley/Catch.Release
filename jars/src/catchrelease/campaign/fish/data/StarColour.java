package catchrelease.campaign.fish.data;

public enum StarColour {
    BLUE("a blue star"),
    WHITE("a white star"),
    YELLOW("a yellow star"),
    ORANGE("an orange star"),
    RED("a red star"),
    BROWN("a brown dwarf"),
    NEUTRON("a neutron star"),
    BLACK_HOLE("a black hole"),
    NONE("no sun at all");

    public final String name;

    StarColour(String name) {
        this.name = name;
    }

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
                // an unrecognised star is still a star, and calling it NONE would put a fish that wants an empty sky under one it has never seen
                return WHITE;
        }
    }

    public static StarColour parse(String name) {
        if (name == null) return null;

        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
