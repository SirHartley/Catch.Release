package catchrelease.campaign.fish.data;

import java.awt.Color;

/**
 * Rarity ladder: the mote colour that goes with it, and how the thing behaves before it is caught.
 * <p>
 * A rare fish is harder to get to as well as harder to land. SPEED is how fast its mote crosses the
 * gap and WANDER how far it strays from a straight line on the way - a common one drifts over and is
 * where you left it, a legendary one is quick and does not hold a course, so getting a drone or a
 * harpoon onto it is its own problem before the catch has even started.
 */
public enum FishRarity {

    COMMON(Color.GRAY, 1f, 1f),
    UNCOMMON(Color.GREEN, 1.15f, 1.3f),
    RARE(Color.BLUE, 1.35f, 1.7f),
    EPIC(new Color(163, 53, 238), 1.6f, 2.2f),
    LEGENDARY(new Color(255, 128, 0), 1.9f, 2.8f);

    public final Color color;

    /** Multipliers on the mote's base travel speed and on how far it wanders off course. */
    public final float speedMult;
    public final float wanderMult;

    FishRarity(Color color, float speedMult, float wanderMult) {
        this.color = color;
        this.speedMult = speedMult;
        this.wanderMult = wanderMult;
    }

    public static FishRarity parse(String name, FishRarity fallback) {
        if (name == null) return fallback;

        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
