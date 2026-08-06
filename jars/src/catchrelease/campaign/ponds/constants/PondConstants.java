package catchrelease.campaign.ponds.constants;

public class PondConstants {

    //spawning
    /** Terrain entities start at radius 0, so the pond sets its own. */
    public static final float POND_RADIUS = 500f;
    public static final float MIN_EMPTY_RADIUS_AROUND_POND = 1000;
    public static final float MIN_DISTANCE = 10000f;
    public static final float DIST_PER_FITTING_ATTEMPT = 500f;

    /** How far one rupture must be from the next - generous, so two in sight of each other read as a choice of which, not where. */
    public static final float MIN_POND_SEPARATION = 6000f;

    /**
     * Clearance from a nebula tile, checked via the nebula's own {@code containsPoint} - i.e. from the
     * actual cloud shape, not the terrain's centre. A rupture inside one is hidden and unreachable.
     */
    public static final float MIN_NEBULA_CLEARANCE = 1200f;

    /** Bearings tried per ring, and rings tried before the search gives up and takes what it has. */
    public static final float FITTING_ANGLE_STEP = 5f;
    public static final int MAX_FITTING_ATTEMPTS = 120;
    public static final int MIN_POND_AMT_PER_SYSTEM = 1;
    public static final int PLANETS_PER_ADDITIONAL_POND = 4;

    // Multiplied by pond radius: range at which the fleet is "at" the pond (rod usable, camera holds).
    public static final float POND_INTERACT_RANGE_MULT = 1.5f;

    /**
     * Inset from the rim, as a fraction of it. Spawning outside this risks a mote drifting past the
     * mask (culled) or appearing on the ragged mask edge, which is mostly undrawn.
     */
    public static final float MOTE_SPAWN_INSET = 0.85f;

    //camera
    /** Seconds for the focus to close most of the distance - higher is softer and slower. */
    public static final float POND_FOCUS_TIME_CONSTANT = 1f;

    /** Same, for the way back - quicker than outbound, so the handback (scaled by distance from the pond) doesn't linger once it's out of sight. */
    public static final float POND_FOCUS_RETURN_TIME_CONSTANT = 0.4f;

    /** World units. Once the camera is this close to the fleet again, control goes back to the game. */
    public static final float POND_FOCUS_HANDBACK_DISTANCE = 5f;

    /**
     * Room the fleet may be pushed off-centre while the camera holds a pond, as a share of the
     * half-screen. A fraction rather than a distance because the hold region is circular
     * ({@link #POND_INTERACT_RANGE_MULT} x pond radius) but the screen isn't - read off the live
     * viewport so it holds at every zoom and aspect ratio.
     */
    public static final float POND_FOCUS_FLEET_MARGIN = 0.8f;

    /**
     * The light motes at depth, keyed off a 0 (bottom) - 1 (near surface) depth value. SPEED_FLOOR is
     * the deepest layer's speed relative to the shallowest (keep well under 1 - the gap reads as
     * distance). REACH_FLOOR narrows deep layers toward the centre - too low and it reads as a disc
     * with a dead ring. FILL runs past 1 deliberately: the mask clips it, so a mote cut by the rim
     * reads as continuing underneath. SIZE is capped below {@code FishEntityPlugin.GLOW_SIZE} so
     * scenery never outshines the motes you're there to catch. Colours are a brightness ramp, not a
     * hue ramp - additive blending only adds light, so depth is dim colour + low alpha, not dark paint.
     * COUNTER_SHARE is the small fraction spinning the other way, enough that the field isn't a wheel.
     */
    public static final int DEPTH_PARTICLES = 90;
    public static final float DEPTH_SPIN_MIN = 1.5f;
    public static final float DEPTH_SPIN_MAX = 6f;
    public static final float DEPTH_COUNTER_SHARE = 0.1f;

    /** Drain: radius/sec and depth/sec lost (before depth-speed scaling), extra swirl gained approaching the middle, and the radius at which a particle recycles to the rim. */
    public static final float DEPTH_SINK_RADIUS = 0.045f;
    public static final float DEPTH_SINK_DEPTH = 0.03f;
    public static final float DEPTH_SWIRL_BOOST = 2.2f;
    public static final float DEPTH_DRAIN = 0.08f;

    /**
     * The well - reads as a hole via a radial remap of the fill (r to r^GAMMA), not a rotation (which
     * reads as a lens error); meets the mask edge seamlessly since the remap returns to its start at
     * the rim. DEPTH blends the remap in (0 flat, 1 full funnel). GAMMA is the profile: below 1, lower
     * means a narrower throat, 1 is a no-op. DIM darkens the centre, fading out by two-thirds of the radius.
     */
    public static final float POND_WELL_DEPTH = 0.7f;
    public static final float POND_WELL_GAMMA = 0.6f;
    public static final float POND_WELL_DIM = 0.4f;

    /**
     * The rim eddy, layered on the well. TWIST is peak radians, EDGE the band's start as a fraction of
     * the mask radius (kept off-centre so the well/warp grid own the middle). TWIST is a standing curl,
     * not a spin - the drifting fill flows through a fixed curl rather than accumulating angle, which
     * would shear over a long session. BREATHE/RATE add an oscillation; BREATHE stays under 1 so the
     * turn never reverses (a direction change reads as a glitch).
     */
    public static final float POND_SWIRL_TWIST = 0.45f;
    public static final float POND_SWIRL_BREATHE = 0.35f;
    public static final float POND_SWIRL_RATE = 0.25f;
    public static final float POND_SWIRL_EDGE = 0.55f;
    public static final float DEPTH_SPEED_FLOOR = 0.25f;
    public static final float DEPTH_REACH_FLOOR = 0.7f;
    public static final float DEPTH_FILL = 1.15f;
    public static final float DEPTH_SIZE_MIN = 3f;
    public static final float DEPTH_SIZE_MAX = 11f;
    public static final float DEPTH_ALPHA_MIN = 0.1f;
    public static final float DEPTH_ALPHA_MAX = 0.35f;
    public static final float DEPTH_BOB = 0.25f;

    public static final java.awt.Color DEPTH_COLOR_DEEP = new java.awt.Color(25, 35, 80);
    public static final java.awt.Color DEPTH_COLOR_NEAR = new java.awt.Color(215, 175, 255);

    /**
     * Toggles between the new stencil+gradient hole (true) and the old masked-warp shader (false, kept
     * as fallback). The rest shape it: background alpha/zoom/drift; funnel darkening (WELL_ALPHA at
     * centre, gone by WELL_REACH); rim shadow rising from RIM_START to the rim.
     */
    public static final boolean POND_HOLE_LOOK = false;
    public static final float HOLE_FILL_MULT = 1.5f;
    public static final float HOLE_BG_ALPHA = 0.85f;
    public static final float HOLE_BG_ZOOM = 1.6f;
    public static final java.awt.Color HOLE_BG_TINT = new java.awt.Color(200, 190, 255);
    public static final float HOLE_DRIFT = 60f;
    public static final float HOLE_DRIFT_PERIOD = 31f;
    public static final float HOLE_WELL_ALPHA = 0.75f;
    public static final float HOLE_WELL_REACH = 0.8f;
    public static final float HOLE_RIM_START = 0.72f;
    public static final float HOLE_RIM_SHADOW = 0.55f;

    /** How much room a pond keeps from a ring band - terrain or purely visual - when spawning. */
    public static final float MIN_RING_CLEARANCE = 200f;

    /** The deep field's own wander (world units) and period - substitutes for parallax, which the camera being snapped to the pond kills. */
    public static final float POND_FILL_DRIFT = 90f;
    public static final float POND_FILL_DRIFT_PERIOD = 23f;

    /**
     * Screen-space distortion (GraphicsLib) from a rupture opening. SIZE is world-unit radius,
     * INTENSITY the bend strength. GROW/FADE are seconds to open/die; the effect runs for the longer
     * of the two and then removes itself.
     */
    public static final float OPEN_DISTORTION_SIZE = 620f;
    public static final float OPEN_DISTORTION_INTENSITY = 26f;
    public static final float OPEN_DISTORTION_GROW = 0.9f;
    public static final float OPEN_DISTORTION_FADE = 1.4f;

}
