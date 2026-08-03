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

    /** The shape of the glass: how many panes the sheet breaks into, the hole, the lit edge. */
    public static final float SHARDS = 9f;
    public static final float CORE_SIZE = 0.2f;
    public static final float EDGE_WIDTH = 0.025f;

    public static final Color RIM_COLOR = new Color(255, 195, 225);
    public static final float RIM_ALPHA = 1f;

    /** Dark, so the hole reads as depth against the lifted panes rather than as a lighter patch. */
    public static final Color DEEP_TINT = new Color(100, 100, 150);

    /** The panes themselves: pale glass catching the light. */
    public static final Color PANE_COLOR = new Color(255, 212, 222);
    public static final float PANE_ALPHA = 0.55f;

    /** The pieces that fly: how many, how hard, how big, and how long they last. */
    public static final int SHARD_COUNT_MIN = 10;
    public static final int SHARD_COUNT_MAX = 16;
    public static final float SHARD_SPEED_MIN = 140f;
    public static final float SHARD_SPEED_MAX = 340f;
    public static final float SHARD_SIZE_MIN = 8f;
    public static final float SHARD_SIZE_MAX = 22f;
    public static final float SHARD_LIFE_MIN = 0.9f;
    public static final float SHARD_LIFE_MAX = 1.8f;

    /** The seismic shove, through GraphicsLib's distortion. Size is world units, intensity is bend. */
    public static final float SHOCK_SIZE = 700f;
    public static final float SHOCK_INTENSITY = 42f;
    public static final float SHOCK_GROW = 0.55f;
    public static final float SHOCK_FADE = 1.1f;

    /** The second, weaker shove that follows it out - one ring alone reads as a bubble. */
    public static final float SHOCK_ECHO_DELAY = 0.35f;
    public static final float SHOCK_ECHO_MULT = 0.55f;

    public static final float BOMB_SIZE = 22f;
    public static final Color BOMB_COLOR = new Color(210, 140, 255);

    /** Sound on detonation. Blank means nothing plays - a hook, not an id waiting to be found. */
    public static final String SOUND_DETONATE = "";
}
