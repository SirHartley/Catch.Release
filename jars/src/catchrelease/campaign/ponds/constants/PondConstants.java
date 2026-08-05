package catchrelease.campaign.ponds.constants;

public class PondConstants {

    //spawning
    /** Terrain entities start at radius 0, so the pond sets its own. */
    public static final float POND_RADIUS = 500f;
    public static final float MIN_EMPTY_RADIUS_AROUND_POND = 1000;
    public static final float MIN_DISTANCE = 10000f;
    public static final float DIST_PER_FITTING_ATTEMPT = 500f; //technical

    /**
     * How far one rupture has to be from the next.
     * <p>
     * Nothing measured this before - a spot was only ever checked against planets, so the second
     * pond in a system was free to land on the first, and did. Generous, because two of them within
     * sight of each other makes the cast a choice of which one rather than where in one.
     */
    public static final float MIN_POND_SEPARATION = 6000f;

    /**
     * How far clear of a nebula tile a rupture has to sit.
     * <p>
     * Handed to the nebula's own containsPoint, which walks its tiles - so this is clearance from
     * the cloud where it actually is, not from the middle of the terrain that owns it. A rupture
     * inside one is invisible under the cloud and unreachable through the slowdown around it.
     */
    public static final float MIN_NEBULA_CLEARANCE = 1200f;

    /** Bearings tried per ring, and rings tried before the search gives up and takes what it has. */
    public static final float FITTING_ANGLE_STEP = 5f;
    public static final int MAX_FITTING_ATTEMPTS = 120;
    public static final int MIN_POND_AMT_PER_SYSTEM = 1;
    public static final int PLANETS_PER_ADDITIONAL_POND = 4;

    //interaction - multiplied by the pond radius. The fleet is "at" the pond within this, which is
    //both where the rod ability can be used and where the camera holds onto the pond
    public static final float POND_INTERACT_RANGE_MULT = 1.5f;

    /**
     * How far inside the rim a mote is put when it spawns, as a share of it.
     * <p>
     * Inside rather than on it for two reasons. A mote outside the mask is culled, and one born
     * exactly on the line could be tipped over it by its own first wander and go straight back out.
     * And the mask is a ragged shape inscribed in a square, so the circle's own edge is mostly not
     * drawn - a mote spawned there would appear out of nothing some way in.
     */
    public static final float MOTE_SPAWN_INSET = 0.85f;

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
     * well - gently, or the field bunches into a disc with a dead ring around it. FILL runs past 1
     * on purpose: the mask stencil cuts whatever crosses the rim, and a mote cut in half by the edge
     * reads as one that continues underneath it.
     * <p>
     * SIZE is the full width handed to the glow sprite, most of which is transparent falloff. The
     * ceiling is set against the catchable motes rather than picked on its own: those draw at
     * {@code FishEntityPlugin.GLOW_SIZE}, and scenery that outgrows the thing you are there to catch
     * puts the eye on the wrong light. The shallowest of these lands comfortably under it.
     * <p>
     * The colours are a brightness ramp as much as a hue ramp: additive blending can
     * only add light, so a deep mote is dark by contributing nearly nothing - dim colour, low alpha
     * - not by being painted dark.
     * <p>
     * COUNTER_SHARE is how many turn the other way. Not many: enough that the field is not a wheel.
     */
    public static final int DEPTH_PARTICLES = 90;
    public static final float DEPTH_SPIN_MIN = 1.5f;
    public static final float DEPTH_SPIN_MAX = 6f;
    public static final float DEPTH_COUNTER_SHARE = 0.1f;

    /**
     * The drain. Radius lost per second (before the depth speed scaling), depth lost per second,
     * how much extra turn a particle picks up by the middle, and the radius at which the drain
     * takes it and it starts over at the rim.
     */
    public static final float DEPTH_SINK_RADIUS = 0.045f;
    public static final float DEPTH_SINK_DEPTH = 0.03f;
    public static final float DEPTH_SWIRL_BOOST = 2.2f;
    public static final float DEPTH_DRAIN = 0.08f;

    /**
     * The well: what makes the rupture read as a hole rather than a disc of space. Not a rotation
     * - a rotation only ever reads as a lens error - but a radial remap of the fill, r to
     * r^GAMMA, which compresses the fill ever harder towards the centre the way a funnel wall
     * does when looked at from above, and lands back where it started at the rim so the remapped
     * fill meets the space outside the mask without a seam.
     * <p>
     * DEPTH is how far that remap is blended in, 0 flat to 1 the full funnel, and is what to turn
     * first if the hole is too shallow or too deep. GAMMA is the funnel's profile: under 1, and
     * lower is a steeper, narrower throat - at 1 the remap does nothing at any DEPTH. DIM is how
     * dark the fill goes at the dead centre, gone by two thirds of the way out; the throat is dark
     * because it is far away, and the depth motes read better lighting a dark floor than floating
     * on a bright one. The remap is static, so nothing here can smear over a long session - the
     * only motion through it is the fill's own drift, which the funnel bends as it passes.
     */
    public static final float POND_WELL_DEPTH = 0.7f;
    public static final float POND_WELL_GAMMA = 0.6f;
    public static final float POND_WELL_DIM = 0.4f;

    /**
     * The eddy at the rim, riding on the well rather than carrying the effect alone. TWIST is
     * radians at the strongest point of the band and EDGE the radius the band starts at, as a
     * fraction of the mask's own radius - kept off the middle, where the funnel and the warp grid
     * own the water, so the twist is something the eye catches at the rim and not a second,
     * louder warper fighting the first. TWIST is a standing turn, not a spin: the drifting fill
     * flows through a fixed curl, which is what keeps the band from shearing itself into mush
     * over hours the way an accumulating angle did.
     * <p>
     * BREATHE and RATE are the life on top: the twist swells by BREATHE of itself and eases back
     * once per 2*pi/RATE seconds. BREATHE stays under 1 so the turn never reverses - a rim that
     * changes direction reads as a glitch, not water.
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
     * The deep field's own drift, in world units, and how long one wander takes.
     * <p>
     * The starfield behind the mask used to slide because the camera moved relative to the pond.
     * With the camera snapped to the pond it never does, so the field is given a slow wander of its
     * own - the two are added, so this is what is left when the camera contributes nothing.
     */
    /**
     * The hole look, trialled against the shader swirl. HOLE_LOOK picks which of the two draws
     * the fill: true is the new stencil-and-gradient hole, false is the old masked-warp shader,
     * kept whole in case the verdict goes the other way. The rest shape the hole itself: the
     * background's alpha, zoom and slow wander; the funnel pooling dark in the middle
     * (WELL_ALPHA at the centre, gone by WELL_REACH of the radius); and the wall shadow rising
     * again from RIM_START of the radius to the rim.
     */
    public static final boolean POND_HOLE_LOOK = true;
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

    public static final float POND_FILL_DRIFT = 90f;
    public static final float POND_FILL_DRIFT_PERIOD = 23f;

    /**
     * The shove space takes when a rupture opens - a real screen-space distortion through
     * GraphicsLib, not another ring drawn over the top of the ones that are already there.
     * <p>
     * SIZE is the radius it reaches in world units and INTENSITY how hard it bends what is behind
     * it. GROW is the seconds it takes to open out and FADE the seconds it takes to die; the ripple
     * runs for the longer of the two and removes itself.
     */
    public static final float OPEN_DISTORTION_SIZE = 620f;
    public static final float OPEN_DISTORTION_INTENSITY = 26f;
    public static final float OPEN_DISTORTION_GROW = 0.9f;
    public static final float OPEN_DISTORTION_FADE = 1.4f;

}
