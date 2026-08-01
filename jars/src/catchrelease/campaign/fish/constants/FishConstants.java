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
    public static final float MINIGAME_TRACK_WIDTH = 60f;
    public static final float MINIGAME_TRACK_HEIGHT = 360f;
    public static final float MINIGAME_METER_WIDTH = 26f;
    public static final float MINIGAME_METER_GAP = 14f;
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

    //minigame - the fish
    /** Track fractions per second, before the fish's own speed and difficulty are applied. */
    public static final float MINIGAME_FISH_BASE_SPEED = 0.5f;
    public static final float MINIGAME_THINK_TIME_MIN = 0.4f;
    public static final float MINIGAME_THINK_TIME_MAX = 1.4f;

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

    //minigame - the meter
    public static final float MINIGAME_PROGRESS_START = 0.4f;
    public static final float MINIGAME_CATCH_RATE = 0.2f;
    public static final float MINIGAME_ESCAPE_RATE = 0.2f;

}
