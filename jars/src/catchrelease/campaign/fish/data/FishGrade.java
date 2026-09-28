package catchrelease.campaign.fish.data;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

public enum FishGrade {

    // Colours run dark red to green, one step per grade. Poor and Exceptional are vanilla's textEnemyColor and
    // textFriendColor; Average is an amber kept apart from the highlight yellow used for plain values.
    TERRIBLE("Terrible", 0, 0.15f, 0.55f, new Color(215, 40, 25)),
    POOR("Poor", 1, 0.33f, 0.8f, new Color(255, 100, 0)),
    AVERAGE("Average", 2, 0.67f, 1f, new Color(245, 180, 60)),
    FINE("Fine", 3, 0.85f, 1.45f, new Color(215, 230, 50)),
    EXCEPTIONAL("Exceptional", 4, 1f, 2.2f, new Color(155, 255, 0));

    public final int rank;
    public final String name;
    public final float ceiling;
    public final float valueMult;
    private final Color color;

    FishGrade(String name, int rank, float ceiling, float valueMult, Color color) {
        this.name = name;
        this.rank = rank;
        this.ceiling = ceiling;
        this.valueMult = valueMult;
        this.color = color;
    }

    public static FishGrade of(float sizeFraction) {
        for (FishGrade grade : values()) {
            if (sizeFraction <= grade.ceiling) return grade;
        }

        return EXCEPTIONAL;
    }

    // Vanilla swaps its negative colour for blue in colour-blind mode; the red steps follow it.
    public Color getColor() {
        if (rank <= POOR.rank && Global.getSettings().getBoolean("colorblindMode")) {
            return Misc.getNegativeHighlightColor();
        }

        return color;
    }
}
