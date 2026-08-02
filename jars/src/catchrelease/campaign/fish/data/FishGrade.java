package catchrelease.campaign.fish.data;

import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

/**
 * How good a specimen is for its species, in five steps.
 * <p>
 * Judged against the species' own range rather than against fish in general, so a fine prawn is
 * graded as a prawn and not thrown out for weighing less than a poor tuna. The bands are uneven on
 * purpose: most specimens should come out average, and EXCEPTIONAL should be worth saying out loud.
 */
public enum FishGrade {

    TERRIBLE("Terrible", 0.15f, 0.55f),
    POOR("Poor", 0.33f, 0.8f),
    AVERAGE("Average", 0.67f, 1f),
    FINE("Fine", 0.85f, 1.45f),
    EXCEPTIONAL("Exceptional", 1f, 2.2f);

    /** Top of this grade's share of the range, and what it does to the price. */
    public final String name;
    public final float ceiling;
    public final float valueMult;

    FishGrade(String name, float ceiling, float valueMult) {
        this.name = name;
        this.ceiling = ceiling;
        this.valueMult = valueMult;
    }

    public static FishGrade of(float sizeFraction) {
        for (FishGrade grade : values()) {
            if (sizeFraction <= grade.ceiling) return grade;
        }

        return EXCEPTIONAL;
    }

    public Color getColor() {
        switch (this) {
            case TERRIBLE: return Misc.getNegativeHighlightColor();
            case POOR: return Misc.getGrayColor();
            case FINE: return Misc.getHighlightColor();
            case EXCEPTIONAL: return Misc.getPositiveHighlightColor();
            default: return Misc.getTextColor();
        }
    }
}
