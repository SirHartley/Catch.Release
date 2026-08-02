package catchrelease.abilities.harpoon.constants;

import java.awt.Color;

public class HarpoonConstants {

    public static final String ENTITY_ID = "catchrelease_Harpoon";

    /** Line and head colour, and the hotter core the energy in it reads as. */
    public static final Color LINE_COLOR = new Color(120, 220, 255);
    public static final Color CORE_COLOR = new Color(230, 250, 255);

    //flight
    /** World units per second on the way out, and how far it will go before giving up. */
    public static final float SPEED = 900f;
    public static final float RANGE = 2200f;

    /** How close the head has to pass a mote to take it. */
    public static final float CATCH_RADIUS = 30f;

    //the shove the mote takes, and how long the two of them carry it
    public static final float PUSH_SPEED = 420f;
    public static final float PUSH_TIME = 0.35f;

    /** Seconds the line spends snapping straight after the push, before the catch begins. */
    public static final float TAUT_TIME = 0.3f;

    //coming home: hard on a landed specimen, unhurried on an empty line
    public static final float REEL_SPEED = 1400f;
    public static final float RETURN_SPEED = 700f;

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
    public static final float SLACK_BOW = 0.06f;
    public static final int LINE_SEGMENTS = 24;

    public static final float HEAD_SIZE = 14f;
    public static final float TRAIL_SIZE = 12f;
}
