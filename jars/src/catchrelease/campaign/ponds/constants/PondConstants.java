package catchrelease.campaign.ponds.constants;

public class PondConstants {

    //spawning
    /** Terrain entities start at radius 0, so the pond sets its own. */
    public static final float POND_RADIUS = 500f;
    public static final float MIN_EMPTY_RADIUS_AROUND_POND = 1000;
    public static final float MIN_DISTANCE = 10000f;
    public static final float DIST_PER_FITTING_ATTEMPT = 500f; //technical
    public static final int MIN_POND_AMT_PER_SYSTEM = 1;
    public static final int PLANETS_PER_ADDITIONAL_POND = 4;

    //interaction - multiplied by the pond radius. The fleet is "at" the pond within this, which is
    //both where the rod ability can be used and where the camera holds onto the pond
    public static final float POND_INTERACT_RANGE_MULT = 1.5f;

    //camera
    /** Seconds for the focus to close most of the distance - higher is softer and slower. */
    public static final float POND_FOCUS_TIME_CONSTANT = 1f;

    /**
     * The same, for the way back. Deliberately quicker than the way out: the camera is handed over
     * once the eased centre reaches the fleet, and how long that takes scales with how far the pond
     * has been left behind - at the outbound constant a fleet burning away holds the camera, and the
     * free look it suppresses, for six or seven seconds after leaving. Long enough that the pond has
     * usually gone out of sight and closed itself by then, which made the two look connected.
     */
    public static final float POND_FOCUS_RETURN_TIME_CONSTANT = 0.4f;

    /** World units. Once the camera is this close to the fleet again, control goes back to the game. */
    public static final float POND_FOCUS_HANDBACK_DISTANCE = 5f;

    /**
     * The motes of light hanging inside the rupture at different depths.
     * <p>
     * Depth runs 0 at the bottom to 1 just under the surface, and every one of these is read off it.
     * SPEED_FLOOR is how fast the deepest layer turns compared to the shallowest - the gap between
     * them is what the eye reads as distance, so this being well under 1 is the point of the whole
     * thing. REACH_FLOOR narrows the deep layers towards the middle, which turns a cylinder into a
     * well. EDGE_FROM is where a particle starts fading so nothing sits hard against the mask, and
     * FILL keeps the field inside the rim.
     * <p>
     * COUNTER_SHARE is how many turn the other way. Not many: enough that the field is not a wheel.
     */
    public static final int DEPTH_PARTICLES = 90;
    public static final float DEPTH_SPIN_MIN = 1.5f;
    public static final float DEPTH_SPIN_MAX = 6f;
    public static final float DEPTH_COUNTER_SHARE = 0.25f;
    public static final float DEPTH_SPEED_FLOOR = 0.25f;
    public static final float DEPTH_REACH_FLOOR = 0.45f;
    public static final float DEPTH_FILL = 0.92f;
    public static final float DEPTH_EDGE_FROM = 0.75f;
    public static final float DEPTH_SIZE_MIN = 1.2f;
    public static final float DEPTH_SIZE_MAX = 4.5f;
    public static final float DEPTH_ALPHA_MIN = 0.12f;
    public static final float DEPTH_ALPHA_MAX = 0.55f;
    public static final float DEPTH_BOB = 0.25f;

    public static final java.awt.Color DEPTH_COLOR_DEEP = new java.awt.Color(70, 90, 190);
    public static final java.awt.Color DEPTH_COLOR_NEAR = new java.awt.Color(215, 175, 255);

    /**
     * The deep field's own drift, in world units, and how long one wander takes.
     * <p>
     * The starfield behind the mask used to slide because the camera moved relative to the pond.
     * With the camera snapped to the pond it never does, so the field is given a slow wander of its
     * own - the two are added, so this is what is left when the camera contributes nothing.
     */
    public static final float POND_FILL_DRIFT = 90f;
    public static final float POND_FILL_DRIFT_PERIOD = 23f;

}
