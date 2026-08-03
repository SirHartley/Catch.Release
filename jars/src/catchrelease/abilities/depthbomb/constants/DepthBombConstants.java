package catchrelease.abilities.depthbomb.constants;

import java.awt.Color;

public class DepthBombConstants {

    public static final String ENTITY_ID = "catchrelease_DepthBomb";

    /** The charge pool, without a row in the upgrade sheet. */
    public static final float CHARGES_FALLBACK = 1f;
    public static final float RECHARGE_FALLBACK = 30f;

    /** How far it can be thrown, and how fast it gets there. */
    public static final float RANGE = 1400f;
    public static final float SPEED = 520f;

    /** How long a slowed mote stays slowed. The strength of it is the upgrade; this is the window. */
    public static final float SLOW_TIME = 6f;

    /** Seconds between landing and going off. Long enough to see where it went. */
    public static final float ARM_TIME = 0.8f;

    /** World units the break reaches, and how many specimens it shakes loose. */
    public static final float BLAST_RADIUS = 420f;
    public static final int MOTES_MIN = 2;
    public static final int MOTES_MAX = 5;

    /**
     * Seconds for the fabric to close again. The break retracts over this, and everything about it
     * is driven from the same number - nothing has a timer of its own.
     */
    public static final float HEAL_TIME = 10f;

    /**
     * The break opens faster than it closes, which is what makes it read as a break rather than as a
     * fade in and out. A share of HEAL_TIME.
     */
    public static final float OPEN_SHARE = 0.06f;

    /** The shape of the glass: spikes out from the middle, the polygon at the middle, the lit edge. */
    public static final float SHARDS = 11f;
    public static final float CORE_SIZE = 0.2f;
    public static final float EDGE_WIDTH = 0.025f;

    public static final Color RIM_COLOR = new Color(255, 185, 230);
    public static final float RIM_ALPHA = 1.5f;
    public static final Color DEEP_TINT = new Color(140, 145, 190);

    /**
     * The seismic shove, through GraphicsLib's distortion. Size is world units, intensity is bend.
     * <p>
     * Intensity is the one that reads: the renderer packs the offset field against whatever the
     * strongest distortion on screen is and unpacks it against the same number, so this is not a
     * share of anything - it is how far the screen actually moves, and it was set low enough that
     * a bomb going off looked like a ripple in a puddle.
     * <p>
     * GROW is short because the shove is supposed to arrive rather than swell, and FADE outlasts it
     * so the bend is still readable once the eye has found it.
     */
    public static final float SHOCK_SIZE = 850f;
    public static final float SHOCK_INTENSITY = 90f;
    public static final float SHOCK_GROW = 0.35f;
    public static final float SHOCK_FADE = 1.35f;

    /** The second, weaker shove that follows it out - one ring alone reads as a bubble. */
    public static final float SHOCK_ECHO_DELAY = 0.35f;
    public static final float SHOCK_ECHO_MULT = 0.55f;

    public static final float BOMB_SIZE = 22f;
    public static final Color BOMB_COLOR = new Color(210, 140, 255);

    /** Sound on detonation. Blank means nothing plays - a hook, not an id waiting to be found. */
    public static final String SOUND_DETONATE = "";
}
