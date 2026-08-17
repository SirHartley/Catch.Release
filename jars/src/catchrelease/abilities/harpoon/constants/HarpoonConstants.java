package catchrelease.abilities.harpoon.constants;

import java.awt.Color;

public class HarpoonConstants {

    public static final String ENTITY_ID = "catchrelease_Harpoon";

    /** Stereo report played only after a charged shot has successfully created its head. */
    public static final String SOUND_FIRE = "catchrelease_harpoon_fire";

    /** Stereo mote-impact report; fleet collisions deliberately use no part of this hook. */
    public static final String SOUND_MOTE_HIT = "catchrelease_harpoon_hit";

    /** UI report when the pool restores a whole harpoon charge. */
    public static final String SOUND_CHARGE_RELOAD = "catchrelease_harpoon_charge_reload";

    public static final String RELOAD_SOUND_SETTING = "catchrelease_harpoonChargeReloadSound";
    public static final String RELOAD_SOUND_NEVER = "Never play";
    public static final String RELOAD_SOUND_RELEVANT = "Play when relevant";
    public static final String RELOAD_SOUND_ALWAYS = "Always play";

    /** Tag on every in-flight harpoon, so active ones can be found without walking the location. */
    public static final String TAG = "catchrelease_harpoon_line";

    /** Charge pool fallback (no upgrade-sheet row). */
    public static final float CHARGES_FALLBACK = 2f;
    public static final float RECHARGE_FALLBACK = 12f;

    /** Line and head colour, and the hotter core the energy in it reads as. */
    public static final Color LINE_COLOR = new Color(120, 220, 255);
    public static final Color CORE_COLOR = new Color(230, 250, 255);

    /**
     * A fitted charge must read before impact, not only once its fireball exists. Three nested
     * passes give it a blood-red warning halo, a saturated head and a hot ignition point. The two
     * incommensurate pulse rates keep it electrically uneasy rather than breathing like a beacon.
     */
    public static final Color EXPLOSIVE_HALO_COLOR = new Color(255, 20, 10);
    public static final Color EXPLOSIVE_HEAD_COLOR = new Color(255, 55, 20);
    public static final Color EXPLOSIVE_CORE_COLOR = new Color(255, 220, 155);
    public static final float EXPLOSIVE_HALO_SIZE = 34f;
    public static final float EXPLOSIVE_HEAD_SIZE = 19f;
    public static final float EXPLOSIVE_CORE_SIZE = 8f;
    public static final float EXPLOSIVE_HALO_ALPHA = 0.32f;
    public static final float EXPLOSIVE_HEAD_ALPHA = 0.82f;
    public static final float EXPLOSIVE_PULSE = 0.16f;
    public static final float EXPLOSIVE_PULSE_RATE = 13f;
    public static final float EXPLOSIVE_FLICKER = 0.07f;
    public static final float EXPLOSIVE_FLICKER_RATE = 31f;

    /** Outbound speed and max range before giving up. */
    public static final float SPEED = 900f;
    public static final float RANGE = 1200f;

    /** How close the head has to pass a mote to take it. */
    public static final float CATCH_RADIUS = 15f;

    //speed and duration of the shove the caught mote takes
    public static final float PUSH_SPEED = 420f;
    public static final float PUSH_TIME = 0.25f;

    /** Seconds the line spends snapping straight after the push, before the catch begins. */
    public static final float TAUT_TIME = 0.2f;

    /** Fast, so a miss doesn't cost dead time waiting for the line to come home. */
    public static final float REEL_SPEED = 1400f;
    public static final float RETURN_SPEED = 1400f;

    /** How close to the fleet counts as home. */
    public static final float ARRIVAL_DISTANCE = 30f;

    /**
     * Hauling a fleet stuck by the harpoon. HAUL_SPEED writes velocity directly (not a fair tow).
     * HAUL_DELAY is matched to PUSH_TIME so both ends of the ability share a rhythm. HAUL_TIME caps
     * the haul so an immovable target eventually releases. HAUL_DONE_DISTANCE is clearance past
     * both hulls that counts as arrived.
     */
    public static final float HAUL_SPEED = 850f;
    public static final float HAUL_DELAY = PUSH_TIME;
    public static final float HAUL_TIME = 6f;
    public static final float HAUL_DONE_DISTANCE = 60f;

    /** Rope shiver while towing a hull - scaled down rather than off, so swing on direction changes survives. */
    public static final float HAUL_SHIVER = 0.3f;

    /**
     * Distance the head must clear before it can hit a fleet. Fleets are reticule-sized (unlike a
     * mote), and the head starts inside the player's own - without this, any cast near a hull
     * (e.g. at a market) sticks on frame one.
     */
    public static final float FLEET_ARM_DISTANCE = 220f;

    /**
     * The charge in an explosive head. RADIUS is deliberately under {@link #FLEET_ARM_DISTANCE}, so
     * a just-armed head can't catch the fleet that fired it. DURATION is vanilla's own multiplier on
     * how long the fireball plays out.
     */
    public static final float BLAST_RADIUS = 150f;
    public static final float BLAST_DURATION = 0.6f;
    public static final Color BLAST_COLOR = new Color(255, 190, 110);

    /**
     * What the blast does to the head that set it off. THROW_SPEED is how hard it's flung and DRAG
     * how fast that dies, so it goes out fast and coasts rather than travelling at one speed until
     * it vanishes. SPIN is the tumble. FADE_TIME is how long the whole line has left afterwards.
     */
    public static final float BLAST_THROW_SPEED = 700f;
    public static final float BLAST_THROW_DRAG = 1.6f;
    public static final float BLAST_SPIN = 540f;
    public static final float BLAST_FADE_TIME = 1.1f;

    /** Marks a fleet as hauled so a second line can't compete; expires so it can't outlive its harpoon. */
    public static final String HAULED_FLAG = "$catchrelease_hauled";
    public static final float HAULED_FLAG_EXPIRY_DAYS = 1f;

    /**
     * Winding in the line once the head is home. DONE is the length (gap + paid-out rope) below
     * which nothing more is drawn. MAX_TIME backstops a fleet outrunning the winch. FADE is the
     * short final fade once it's just a dot on the hull.
     */
    public static final float RETRACT_DONE = 4f;
    public static final float RETRACT_MAX_TIME = 0.5f;
    public static final float RETRACT_FADE = 0.07f;

    /** Winch pull-in rate while stowing - overrides the underdamped spring so it doesn't ring down for a second. */
    public static final float RETRACT_SLACK_PULL = 14f;

    /** Hairline core with a wider glow pass under it; both in screen pixels, so weight is camera-distance independent. */
    public static final float LINE_WIDTH = 1f;
    public static final float LINE_GLOW_WIDTH = 2f;
    public static final float LINE_ALPHA = 0.6f;
    public static final float LINE_GLOW_ALPHA = 0.1f;

    public static final int LINE_SEGMENTS = 32;

    /**
     * The rope's middle is a mass on a spring pulled toward the straight line between its ends,
     * never quite catching up while either end moves. SPRING is stiffness, DRAG is how fast the
     * swing dies (both underdamped, so it settles elastic rather than overshooting). MAX_STEP caps
     * the integration slice - campaign frames arrive in chunks large enough to blow up a spring
     * this stiff, so long frames are integrated in pieces.
     */
    public static final float LINE_SPRING = 150f;
    public static final float LINE_DRAG = 6f;
    public static final float LINE_MAX_STEP = 1f / 60f;

    /**
     * Slack rope in the air, which sets how far the middle hangs off the straight line. PAYOUT is
     * extra line paid out beyond distance covered. TAKEUP is the haul-in rate once taut. REEL_IN
     * is the return retrieval rate, slower than the head closes so the harpoon runs ahead of its
     * own rope. Excess feeds the (symmetric) waves below rather than hanging to one side.
     */
    public static final float LINE_PAYOUT = 1.06f;
    public static final float LINE_TAKEUP = 2500f;
    public static final float LINE_REEL_IN = 850f;

    /**
     * Shiver on top of the spring swing. WAVE_COUNT is simultaneous bends, SPEED how fast they run
     * down the rope, AMPLITUDE a share of line length. Strength is the max of three drivers: the
     * throw's initial impulse decaying over DAMPING seconds, current swing speed (REFERENCE_SPEED
     * = max), and slack in the air (EXCESS_FULL = fully loose).
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
