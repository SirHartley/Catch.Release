package catchrelease.abilities.rod.constants;

import java.awt.Color;

public class RodConstants {

    /** Drone hull and trail colour. */
    public static final Color DRONE_COLOR = Color.ORANGE;

    //drones
    /** Radius of the ring the drones fly once they arrive. The reticule is sized to match. */
    public static final float DRONE_ORBIT_RADIUS = 60f;

    /** World units per second on the way out and the way home. */
    public static final float DRONE_SPEED = 300f;

    /**
     * Seconds for a drone to converge on the velocity it wants - the give in its steering. Higher is
     * heavier and wider-turning, lower is twitchier. This is what curves the flight paths: the drone
     * steers its velocity rather than being placed along a line.
     */
    public static final float DRONE_STEER_RESPONSE = 0.45f;

    /**
     * Distance over which a drone eases off as it closes on whatever it is heading for. Tuned
     * against {@link #DRONE_SPEED}; a returning drone works out its own from the speed it is
     * actually doing, and uses this as the floor.
     */
    public static final float DRONE_SLOWING_DISTANCE = 120f;

    /**
     * How hard a returning drone brakes, as a multiple of {@link #DRONE_STEER_RESPONSE}: it asks for
     * the closing speed that would cover what is left of the gap in that long. Lower brakes later
     * and flies straighter, higher eases off sooner and takes the long way round.
     * <p>
     * Tuned by simulating returns from 200 to 1200 units against a fleet burning away at up to 300
     * units a second, from every start angle on the ring. This is where the path is shortest without
     * the approach to a stationary fleet changing at all.
     */
    public static final float DRONE_BRAKE_MARGIN = 0.5f;

    /** Sideways drift in world units per second, and how quickly it wanders. */
    public static final float DRONE_NOISE_STRENGTH = 40f;
    public static final float DRONE_NOISE_FREQUENCY = 0.9f;

    /** How close a drone has to get to a mote to have hold of it. */
    public static final float DRONE_CATCH_DISTANCE = 15f;

    /**
     * Degrees per second around the ring. At 250 units out this is about 130 units/second of
     * tangential motion - deliberately well under {@link #DRONE_SPEED}, so a drone has the headroom
     * to close on its slot instead of permanently trailing it.
     */
    public static final float DRONE_ORBIT_SPEED = 30f;

    /** How close to the ring counts as being on it, in world units. */
    public static final float DRONE_JOIN_DISTANCE = 15f;

    /**
     * Degrees ahead of its own slot a launching drone aims for. Aiming straight at the slot means
     * meeting the ring head-on and having to turn ninety degrees on arrival; aiming ahead of it
     * curves the approach round so the drone is already going the right way when it gets there.
     */
    public static final float DRONE_JOIN_LEAD_ANGLE = 55f;

    /** How closely a drone's heading must match the ring's before it takes up circle flight. */
    public static final float DRONE_JOIN_ALIGNMENT = 60f;

    /** Seconds to bleed off the heading error a drone joined the circle with. */
    public static final float DRONE_FACING_RESPONSE = 0.4f;

    /**
     * Degrees per second a drone may trim its position round the ring, on top of the ring's own
     * rotation, while closing on its slot. This is what keeps it from sprinting round to its place:
     * uncapped, a drone rejoining from the far side turns two to three times faster than one simply
     * flying the circle, which reads as a sharp snap rather than drifting back into formation.
     */
    public static final float DRONE_TRIM_RATE = 25f;

    /** Seconds for a drone on the circle to settle into its exact slot and radius. */
    public static final float DRONE_SETTLE_RESPONSE = 1.2f;

    /**
     * How much faster a returning drone gets per second on the way home, and the ceiling on it. The
     * slowing distance still applies at the end, so it arrives rather than overshooting the fleet.
     */
    public static final float DRONE_RETURN_ACCELERATION = 0.7f;
    public static final float DRONE_RETURN_MAX_MULT = 3.5f;

    //the ring the drones fly, drawn so it is clear where a mote has to drift to count
    public static final int RING_SEGMENTS = 72;
    public static final float RING_WIDTH = 1.5f;
    /** Alpha while nothing is in it, and while something is. */
    public static final float RING_ALPHA_IDLE = 0.2f;
    public static final float RING_ALPHA_ACTIVE = 0.55f;
    /** Seconds to fade in on arrival and out on recall, and the pulse rate while something is in. */
    public static final float RING_FADE_TIME = 0.5f;
    public static final float RING_PULSE_SPEED = 4f;

    /** How close counts as arrived, in world units. */
    public static final float DRONE_ARRIVAL_DISTANCE = 20f;

    public static final float DRONE_SPRITE_SIZE = 10;
    public static final float DRONE_TRAIL_SIZE = 20f;

    /** Used when the upgrade stat is missing entirely, so a fresh save still fishes. */
    public static final int DRONE_COUNT_FALLBACK = 1;

    /** How often the swarm looks for something to catch, in seconds. */
    public static final float DRONE_SEARCH_INTERVAL = 0.25f;

}
