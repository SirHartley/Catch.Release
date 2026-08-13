package catchrelease.campaign.fish.data;

import java.awt.Color;

/**
 * Rarity ladder: mote colour, plus how fast (SPEED) and erratically (WANDER) the mote moves before
 * it's caught - a rare fish is harder to get to as well as harder to land.
 * <p>
 * The top of the ladder is red rather than the orange it used to be. Orange is spoken for twice
 * over: it is what the mod marks quest-relevant things in, and vanilla's own {@code textEnemyColor}
 * - which every negative highlight in the game resolves to - is [255,100,0], near enough identical
 * to the old legendary. A swatch on the best fish in the table was reading as a warning.
 */
public enum FishRarity {

    COMMON(0, Color.GRAY, 1f, 1f),
    UNCOMMON(1, Color.GREEN, 1.15f, 1.3f),
    RARE(2, Color.BLUE, 1.35f, 1.7f),
    EPIC(3, new Color(163, 53, 238), 1.6f, 2.2f),
    LEGENDARY(4, new Color(235, 55, 50), 1.9f, 2.8f);

    /**
     * Where this sits on the ladder, said outright rather than read off `ordinal()` - every
     * "at least this rare" comparison and rarity-graded price in the mod goes through it, so
     * reordering or inserting into the enum cannot silently reshuffle all of them.
     */
    public final int rank;

    public final Color color;

    /** Multipliers on the mote's base travel speed and on how far it wanders off course. */
    public final float speedMult;
    public final float wanderMult;

    FishRarity(int rank, Color color, float speedMult, float wanderMult) {
        this.rank = rank;
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
