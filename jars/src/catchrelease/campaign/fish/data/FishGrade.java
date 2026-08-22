package catchrelease.campaign.fish.data;

import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

public enum FishGrade {
    TERRIBLE("Terrible", 0, 0.15f, 0.55f),
    POOR("Poor", 1, 0.33f, 0.8f),
    AVERAGE("Average", 2, 0.67f, 1f),
    FINE("Fine", 3, 0.85f, 1.45f),
    EXCEPTIONAL("Exceptional", 4, 1f, 2.2f);

    public final int rank;

    public final String name;
    public final float ceiling;
    public final float valueMult;

    FishGrade(String name, int rank, float ceiling, float valueMult) {
        this.name = name;
        this.rank = rank;
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
