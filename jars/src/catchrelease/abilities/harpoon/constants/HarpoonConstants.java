package catchrelease.abilities.harpoon.constants;

import java.awt.Color;

public class HarpoonConstants {

    public static final String ENTITY_ID = "catchrelease_Harpoon";

    /** Line and head colour, and the hotter core the energy in it reads as. */
    public static final Color LINE_COLOR = new Color(120, 220, 255);
    public static final Color CORE_COLOR = new Color(230, 250, 255);

    //flight
    /**
     * World units per second on the way out, and how far it will go before giving up.
     * <p>
     * The range is what a miss costs: the line runs all of it before it turns round, so a long one
     * is a long wait watching nothing happen. This is about a second and a half out.
     */
    public static final float SPEED = 900f;
    public static final float RANGE = 1300f;

    /** How close the head has to pass a mote to take it. */
    public static final float CATCH_RADIUS = 30f;

    //the shove the mote takes, and how long the two of them carry it
    public static final float PUSH_SPEED = 420f;
    public static final float PUSH_TIME = 0.35f;

    /** Seconds the line spends snapping straight after the push, before the catch begins. */
    public static final float TAUT_TIME = 0.3f;

    /**
     * Coming home. Both hard: a miss is dead time, and there is nothing to be gained by making the
     * player watch a slow line come back before they can fire the next one.
     */
    public static final float REEL_SPEED = 1400f;
    public static final float RETURN_SPEED = 1400f;

    /** How close to the fleet counts as home. */
    public static final float ARRIVAL_DISTANCE = 30f;

    /**
     * The line itself: a hairline core with a soft wider pass under it for the glow. Both are screen
     * pixels, so the cable stays the same weight however far the camera is pulled back.
     */
    public static final float LINE_WIDTH = 1f;
    public static final float LINE_GLOW_WIDTH = 4f;
    public static final float LINE_ALPHA = 0.9f;
    public static final float LINE_GLOW_ALPHA = 0.25f;

    /**
     * How far the slack line bows away from straight before it is pulled taut, as a share of its own
     * length. A line under tension has none of it.
     */
    public static final float SLACK_BOW = 0.035f;
    public static final int LINE_SEGMENTS = 32;

    /**
     * The whip in a freshly thrown line - more rope in the air than there is distance to cover, being
     * dragged straight by something faster than it.
     * <p>
     * WAVE_COUNT is how many bends are in the rope at once and SPEED is how fast they run down it,
     * from the fleet towards the head. AMPLITUDE is a share of the line's own length, weighted
     * towards the fleet end where the slack actually is - the head end is being pulled and has none.
     * DAMPING is the seconds it takes for the whip to fall to a third of itself, so the throw
     * settles into a bow on its own rather than waving all the way out.
     */
    public static final float WAVE_COUNT = 2.5f;
    public static final float WAVE_SPEED = 9f;
    public static final float WAVE_AMPLITUDE = 0.07f;
    public static final float WAVE_DAMPING = 0.5f;

    public static final float HEAD_SIZE = 14f;
    public static final float TRAIL_SIZE = 12f;
}
