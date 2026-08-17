package catchrelease.abilities.rod.constants;

import java.awt.Color;

public class RodConstants {

    /** Drone hull and trail colour. */
    public static final Color DRONE_COLOR = Color.ORANGE;

    /** Mono directional launch report, played once for each drone successfully put into the world. */
    public static final String SOUND_DRONE_LAUNCH = "catchrelease_drone_launch";

    /** Directional acquisition report when a passive or passive-bound drone commits to a new target. */
    public static final String SOUND_TARGET_LOCK = "catchrelease_rod_target_lock";

    /** Mono directional report at confirmed drone/mote contact. */
    public static final String SOUND_MOTE_HIT = "catchrelease_rod_hit";

    /** Second mono directional layer when the drone has the mote. */
    public static final String SOUND_MOTE_CAUGHT = "catchrelease_rod_catch";

    /** Stereo opening report, replacing the searchlight-toggle placeholder at the center impact. */
    public static final String SOUND_POND_OPEN = "catchrelease_pond_open_boom";

    //drones
    /** Seconds between drones in one launch sequence; the first leaves immediately. */
    public static float DRONE_LAUNCH_OFFSET = 0.5f;

    /** Radius of the ring drones fly once arrived; the reticule matches. */
    public static final float DRONE_ORBIT_RADIUS = 60f;

    /** Radius of the ring a roaming drone flies around the fleet. Wider than the orbit ring so it clears the fleet's own sprites. */
    public static final float DRONE_ROAM_RADIUS = 140f;

    /** Fallback fishing ring radius when the upgrade sheet has no row; kept well under {@code PondConstants.POND_RADIUS}. */
    public static final float RING_RADIUS_FALLBACK = 150f;

    /** Fallback reaction margin past the ring; see {@code FishingDroneSwarmScript.getChaseMargin()}. */
    public static final float CHASE_MARGIN_FALLBACK = 40f;

    /** World units per second, both outbound and returning. */
    public static final float DRONE_SPEED = 300f;

    /** Seconds for a drone to converge on its target velocity (steering); higher is heavier/wider-turning. */
    public static final float DRONE_STEER_RESPONSE = 0.45f;

    /** Distance to ease off on approach; tuned against {@link #DRONE_SPEED}, used as the floor for returning drones. */
    public static final float DRONE_SLOWING_DISTANCE = 120f;

    /**
     * Braking strength for returning drones, as a multiple of {@link #DRONE_STEER_RESPONSE}; acts as
     * an underdamped damping ratio (~0.35 at 0.5), so drones overshoot and swing back rather than
     * ease off too early and never close the gap.
     */
    public static final float DRONE_BRAKE_MARGIN = 0.5f;

    /** Sideways drift in world units per second, and how quickly it wanders. */
    public static final float DRONE_NOISE_STRENGTH = 40f;
    public static final float DRONE_NOISE_FREQUENCY = 0.9f;

    /** How close a drone has to get to a mote to have hold of it. */
    public static final float DRONE_CATCH_DISTANCE = 15f;

    /** Degrees per second around the ring; kept well under {@link #DRONE_SPEED} so a drone can still close on its slot. */
    public static final float DRONE_ORBIT_SPEED = 30f;

    /** How close to the ring counts as being on it, in world units. */
    public static final float DRONE_JOIN_DISTANCE = 15f;

    /** Degrees ahead of its slot a launching drone aims for, curving the approach so it's already heading the right way on arrival. */
    public static final float DRONE_JOIN_LEAD_ANGLE = 55f;

    /** How closely a drone's heading must match the ring's before it takes up circle flight. */
    public static final float DRONE_JOIN_ALIGNMENT = 60f;

    /** Seconds to bleed off the heading error a drone joined the circle with. */
    public static final float DRONE_FACING_RESPONSE = 0.4f;

    /** Degrees per second a drone may trim its ring position (on top of the ring's own rotation) while closing on its slot; caps rejoin speed. */
    public static final float DRONE_TRIM_RATE = 25f;

    /** Seconds for a drone on the circle to settle into its exact slot and radius. */
    public static final float DRONE_SETTLE_RESPONSE = 1.2f;

    /** Acceleration per second on the way home, and its cap. Braking is governed by {@link #DRONE_BRAKE_MARGIN}, not {@link #DRONE_SLOWING_DISTANCE}. */
    public static final float DRONE_RETURN_ACCELERATION = 0.2f;
    public static final float DRONE_RETURN_MAX_MULT = 3.5f;

    public static final int RING_SEGMENTS = 72;
    public static final float RING_WIDTH = 1.5f;

    /** Dash count (not length) so the pattern stays consistent at any zoom and closes cleanly at the seam. */
    public static final int RING_DASH_COUNT = 24;
    public static final float RING_DASH_DUTY = 0.5f;
    /** Alpha while nothing is in it, and while something is. */
    public static final float RING_ALPHA_IDLE = 0.02f;
    public static final float RING_ALPHA_ACTIVE = 0.1f;
    /** Seconds to fade in on arrival and out on recall, and the pulse rate while something is in. */
    public static final float RING_FADE_TIME = 0.5f;
    public static final float RING_PULSE_SPEED = 4f;

    /** How close counts as arrived, in world units. */
    public static final float DRONE_ARRIVAL_DISTANCE = 10f;

    public static final float DRONE_SPRITE_SIZE = 8f;
    public static final float DRONE_TRAIL_SIZE = 5f;

    /** Used when the upgrade stat is missing entirely, so a fresh save still fishes. */
    public static final int DRONE_COUNT_FALLBACK = 1;

    /** How often the swarm looks for something to catch, in seconds. */
    public static final float DRONE_SEARCH_INTERVAL = 0.25f;

}
