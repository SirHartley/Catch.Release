package catchrelease.campaign.fish.data;

import java.awt.Color;

/**
 * Rarity ladder: mote colour, plus how fast (SPEED) and erratically (WANDER) the mote moves before
 * it's caught - a rare fish is harder to get to as well as harder to land.
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
