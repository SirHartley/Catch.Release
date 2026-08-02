package catchrelease.campaign.fish.constants;

public class FishConstants {

    //sector regions - the inner band, measured from the sector centre. Systems inside it are CORE_*,
    //everything else RIM_*, and each band is quartered by direction. The vanilla sector runs
    //164000 x 104000, so this is roughly its middle third
    public static final float CORE_BAND_HALF_WIDTH = 27000f;
    public static final float CORE_BAND_HALF_HEIGHT = 17000f;

    //minigame - panel
    public static final float MINIGAME_PANEL_WIDTH = 420f;
    public static final float MINIGAME_PANEL_HEIGHT = 480f;
    public static final float MINIGAME_TRACK_WIDTH = 52f;
    public static final float MINIGAME_TRACK_HEIGHT = 360f;
    public static final float MINIGAME_METER_WIDTH = 10f;
    /**
     * Gap from the track to the meter. Wide enough for both to carry their own border dressing with
     * clear air between them - at the six units this was, the two borders crossed each other.
     */
    public static final float MINIGAME_METER_GAP = 16f;
    /** Below this share of the meter it reads as losing. */
    public static final float MINIGAME_METER_DANGER = 0.3f;
    /** Seconds the result stays up before the dialog closes itself. */
    public static final float MINIGAME_END_LINGER = 0.9f;
    public static final float MINIGAME_FISH_ICON_SIZE = 28f;

    //minigame - the bar the player flies
    /** Used when the upgrade stat is missing, so a fresh save can still fish. */
    public static final float MINIGAME_BAR_SIZE_FALLBACK = 120f;
    public static final float MINIGAME_BAR_MIN_FRACTION = 0.08f;
    public static final float MINIGAME_BAR_MAX_FRACTION = 0.6f;

    /** Track fractions per second squared, and the speed cap, in fractions per second. */
    public static final float MINIGAME_BAR_LIFT = 2.8f;
    public static final float MINIGAME_BAR_GRAVITY = 2.0f;
    public static final float MINIGAME_BAR_MAX_SPEED = 1.1f;

    /** Share of its speed the bar keeps on hitting an end, and the crawl below which it rests. */
    public static final float MINIGAME_BAR_RESTITUTION = 0.72f;
    public static final float MINIGAME_BAR_REST_SPEED = 0.04f;

    //minigame - the fish
    /** Track fractions per second, before the fish's own speed and difficulty are applied. */
    public static final float MINIGAME_FISH_BASE_SPEED = 0.68f;
    /** How hard the fish pulls towards where it is going, and how long it takes to get up to it. */
    public static final float MINIGAME_FISH_STIFFNESS = 3.6f;
    public static final float MINIGAME_FISH_RESPONSE = 0.3f;

    /** Visual only: how far the icon twitches, in pixels, and how quickly. */
    public static final float MINIGAME_FISH_JITTER = 3.5f;
    public static final float MINIGAME_FISH_JITTER_SPEED = 9f;
    /** How much harder it twitches when swimming hard, per unit of track speed. */
    public static final float MINIGAME_FISH_JITTER_EFFORT = 0.8f;

    public static final float MINIGAME_THINK_TIME_MIN = 0.35f;
    public static final float MINIGAME_THINK_TIME_MAX = 1.2f;

    /** Darters are the wait as much as the bolt, so they keep more of their thinking time. */
    public static final float MINIGAME_DARTER_PATIENCE = 1.3f;

    /**
     * Difficulty curve: a fish plays at DIFFICULTY_FLOOR + SCALE * (difficulty / BASELINE) times the
     * base speed and restlessness. Deliberately compressed - scaling straight off difficulty made a
     * 90 unwinnable and a 25 trivial, since it compounds across both speed and how often it turns.
     * <p>
     * Everything here was retuned against a player model with a 160ms reaction and a 120ms decision
     * cadence, rather than one that reacts every frame. The difference is not small: the first set of
     * numbers played at 86% for the perfect model and 7% for the realistic one.
     */
    /**
     * The one knob. Everything about how hard a fish is runs through this: lower is easier across
     * the board, higher is harder, and the per-fish numbers in fish.csv keep their relative shape
     * either way. 1 is as tuned.
     */
    public static final float MINIGAME_GLOBAL_DIFFICULTY = 1f;

    public static final float MINIGAME_DIFFICULTY_BASELINE = 50f;
    public static final float MINIGAME_DIFFICULTY_FLOOR = 0.7f;
    public static final float MINIGAME_DIFFICULTY_SCALE = 0.3f;

    //minigame - dev controls
    public static final float MINIGAME_DIFFICULTY_MIN = 1f;
    public static final float MINIGAME_DIFFICULTY_MAX = 200f;
    public static final float MINIGAME_DIFFICULTY_STEP = 10f;
    public static final float MINIGAME_SPEED_MIN = 0.1f;
    public static final float MINIGAME_SPEED_MAX = 4f;
    public static final float MINIGAME_SPEED_STEP = 0.2f;
    /** Dev mode only: the meter stops here instead of running out, so nothing is ever lost. */
    public static final float MINIGAME_DEV_PROGRESS_FLOOR = 0.02f;
    public static final float MINIGAME_DEV_ROW_HEIGHT = 74f;
    public static final float MINIGAME_DEV_BUTTON_WIDTH = 62f;
    public static final float MINIGAME_DEV_BUTTON_HEIGHT = 22f;

    /** Padding between the playfield and whatever frames it. */
    public static final float MINIGAME_FRAME_PAD = 12f;

    /**
     * The track's hyperspace backing: how strongly it shows, how far into the sprite it zooms, and
     * the warp that keeps it moving.
     * <p>
     * The warp grid's border does not move, so the swim stays inside the bar. CELLS is vertices per
     * side, and the two radii are in screen pixels - small numbers, since the bar is 52 wide.
     */
    public static final float MINIGAME_TRACK_BG_ALPHA = 0.7f;
    public static final float MINIGAME_TRACK_BG_ZOOM = 1f;
    public static final int MINIGAME_TRACK_BG_WARP_CELLS = 6;
    public static final float MINIGAME_TRACK_BG_WARP_MIN = 1.5f;
    public static final float MINIGAME_TRACK_BG_WARP_MAX = 5f;
    public static final float MINIGAME_TRACK_BG_WARP_RATE = 1f;

    /**
     * The dark the backing fades into going down the bar, top and bottom. Drawn over the backing but
     * under everything in play, so the fish and the bar stay as readable at the bottom as the top.
     */
    public static final float MINIGAME_TRACK_FADE_TOP = 0.15f;
    public static final float MINIGAME_TRACK_FADE_BOTTOM = 0.95f;

    /**
     * The dressing drawn around each of the two bars: a bright rounded outline just off the bar, and
     * a dimmer one a little further out, the way vanilla dresses its own panels.
     * <p>
     * INSET is the gap from the bar to the bright line, SPACING the gap from that to the dim one, and
     * the radius is clamped to what fits - so the meter, being ten units wide, rounds tighter than
     * the track does rather than bowing.
     */
    public static final float MINIGAME_BORDER_INSET = 2f;
    public static final float MINIGAME_BORDER_SPACING = 3f;
    public static final float MINIGAME_BORDER_RADIUS = 3f;
    public static final float MINIGAME_BORDER_WIDTH = 1f;
    public static final float MINIGAME_BORDER_ALPHA = 0.9f;
    public static final float MINIGAME_BORDER_OUTER_ALPHA = 0.35f;

    //minigame - the meter
    public static final float MINIGAME_PROGRESS_START = 0.4f;
    public static final float MINIGAME_CATCH_RATE = 0.2f;
    public static final float MINIGAME_ESCAPE_RATE = 0.21f;

}
