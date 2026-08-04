package catchrelease.abilities.rod.constants;

import java.awt.Color;

public class RodConstants {

    /** Drone hull and trail colour. */
    public static final Color DRONE_COLOR = Color.ORANGE;

    //drones
    /** Radius of the ring the drones fly once they arrive. The reticule is sized to match. */
    public static final float DRONE_ORBIT_RADIUS = 60f;

    /**
     * The ring the swarm fishes inside, when the upgrade sheet has no row for it. Every real read
     * goes through the upgrade, so this is the number a save with a missing sheet falls back to
     * rather than the number the game normally uses.
     * <p>
     * Kept well under {@code PondConstants.POND_RADIUS}. At four hundred against a pond of five
     * this covered most of the water from wherever it was dropped, and a cast that catches
     * everything is not a cast - the aim stops being a decision.
     */
    public static final float RING_RADIUS_FALLBACK = 150f;

    /**
     * How far past that ring a drone still reacts, without the upgrade. See
     * {@code FishingDroneSwarmScript.getChaseMargin()} for why this is not zero.
     */
    public static final float CHASE_MARGIN_FALLBACK = 40f;

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
     * and flies straighter, higher eases off sooner and comes in gently.
     * <p>
     * This is a damping ratio in disguise, and that is what picks the number. The drone asks for a
     * closing speed of {@code gap / (response * margin)} but only eases onto it, over that same
     * response - so the gap is a mass on a spring: {@code r'' + r'/response + r/(response^2 *
     * margin) = 0}, damping at {@code sqrt(margin) / 2}. The response cancels out of that, so how
     * far past the fleet a drone sails is fixed by this number alone and no amount of upgraded
     * steering will help it. The old 0.5 damped at 0.35 and overshot by about a third of whatever
     * the gap was when braking started - fifty to seventy units on an ordinary return.
     * <p>
     * 4 would be critical damping and cannot overshoot at all, but it dawdles: an approach that
     * only ever decays takes a second and a half longer to get home than it needs to. 3 damps at
     * 0.87, where what is left of the overshoot is a couple of units - smaller than
     * {@link #DRONE_ARRIVAL_DISTANCE}, so the drone is home and gone before it could be seen.
     * Simulated across the upgrade range, fleets burning off at up to 300, gaps from 200 to 2000
     * and down to twenty frames a second: nothing crosses.
     * <p>
     * Tuning this for the shortest path is what got it wrong before: the shortest path to a point
     * you are allowed to fly past is always the one that brakes latest.
     */
    public static final float DRONE_BRAKE_MARGIN = 3f;

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
     * How much faster a returning drone gets per second on the way home, and the ceiling on it.
     * {@link #DRONE_SLOWING_DISTANCE} is not what stops it - a returning drone brakes off the gap
     * instead, see {@link #DRONE_BRAKE_MARGIN} - and the faster it has wound up to, the further out
     * that braking starts.
     */
    public static final float DRONE_RETURN_ACCELERATION = 0.2f;
    public static final float DRONE_RETURN_MAX_MULT = 3.5f;

    //the ring the drones fly, drawn so it is clear where a mote has to drift to count
    public static final int RING_SEGMENTS = 72;
    public static final float RING_WIDTH = 1.5f;

    /**
     * Dashes around the ring, and how much of each one is drawn rather than gap.
     * <p>
     * A count rather than a length, so the pattern belongs to the ring: it is the same at every zoom
     * level and it closes at the seam instead of clipping the last dash short. Sizing dashes in
     * screen pixels - which is right for a reticule, since that is drawn at whatever zoom the player
     * is aiming at - would have this circle re-cut its dashes every time the camera changed distance.
     */
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
