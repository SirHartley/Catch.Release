package catchrelease.abilities.harpoon.constants;

import java.awt.Color;

public class HarpoonConstants {

    public static final String ENTITY_ID = "catchrelease_Harpoon";

    /**
     * The charge pool, without a row in the upgrade sheet. Charges rather than a cooldown, so a pass
     * can be spent all at once or held back - which is a more interesting question than whether the
     * timer is up.
     */
    public static final float CHARGES_FALLBACK = 2f;
    public static final float RECHARGE_FALLBACK = 12f;

    /** Line and head colour, and the hotter core the energy in it reads as. */
    public static final Color LINE_COLOR = new Color(120, 220, 255);
    public static final Color CORE_COLOR = new Color(230, 250, 255);

    //flight
    /** World units per second on the way out, and how far it will go before giving up. */
    public static final float SPEED = 900f;
    public static final float RANGE = 1200f;

    /** How close the head has to pass a mote to take it. */
    public static final float CATCH_RADIUS = 15f;

    //the shove the mote takes, and how long the two of them carry it
    public static final float PUSH_SPEED = 420f;
    public static final float PUSH_TIME = 0.15f;

    /** Seconds the line spends snapping straight after the push, before the catch begins. */
    public static final float TAUT_TIME = 0.2f;

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
    public static final float LINE_GLOW_WIDTH = 2f;
    public static final float LINE_ALPHA = 0.6f;
    public static final float LINE_GLOW_ALPHA = 0.1f;

    public static final int LINE_SEGMENTS = 32;

    /**
     * The rope has weight. Its middle is a mass on a spring, pulled towards where a straight line
     * between the fleet and the head would put it, and it never quite gets there while either end is
     * moving - which is the whole of the behaviour. The bow on the way out, the swing across when the
     * head turns round, the wobble on the way back and the settle when everything stops all come out
     * of these two numbers rather than being written down anywhere.
     * <p>
     * SPRING is stiffness and DRAG is how quickly the swinging dies. Together they are underdamped on
     * purpose: a rope that returns to straight without overshooting reads as elastic rather than
     * heavy. MAX_STEP is the largest slice the spring is integrated over - campaign time arrives in
     * chunks large enough to make a spring this stiff fly apart, so a long frame is walked through in
     * pieces.
     */
    public static final float LINE_SPRING = 150f;
    public static final float LINE_DRAG = 6f;
    public static final float LINE_MAX_STEP = 1f / 60f;

    /**
     * How much rope is in the air, which is what decides how far the middle of it hangs off the
     * straight line between its ends.
     * <p>
     * PAYOUT is how much more line goes out than the distance being covered - a launcher throws rope
     * rather than measuring it. TAKEUP is how fast the slack is hauled in when the line is pulled
     * taut, and REEL_IN how fast it comes in on the way home: slower than the head closes, on
     * purpose, so a returning harpoon runs ahead of its own rope and there is spare line behind it.
     * <p>
     * The excess does not hang to one side. It goes into the waves instead, which are symmetric
     * about the straight line, so the rope stays centred on the shot however much of it is loose.
     */
    public static final float LINE_PAYOUT = 1.06f;
    public static final float LINE_TAKEUP = 2500f;
    public static final float LINE_REEL_IN = 850f;

    /**
     * The shiver on top of the swing - the small stuff a heavy rope does that one smooth curve
     * cannot say on its own.
     * <p>
     * WAVE_COUNT is how many bends are in the rope at once, SPEED how fast they run down it, and
     * AMPLITUDE a share of the line's own length. Three things feed it, and the strongest wins: the
     * throw itself dying off over DAMPING seconds, how hard the middle of the rope is being swung
     * about at the time, and how much spare rope is in the air. REFERENCE_SPEED is the swing speed
     * that counts as being thrown about as hard as it gets, and EXCESS_FULL the share of spare rope
     * that counts as fully loose.
     */
    public static final float WAVE_COUNT = 2.5f;
    public static final float WAVE_SPEED = 9f;
    public static final float WAVE_AMPLITUDE = 0.06f;
    public static final float WAVE_DAMPING = 0.5f;
    public static final float WAVE_REFERENCE_SPEED = 500f;
    public static final float WAVE_EXCESS_FULL = 0.12f;

    public static final float HEAD_SIZE = 14f;
    public static final float TRAIL_SIZE = 5f;
}
