package catchrelease.tools;

// Tool-only copy. tests/test-fish-parity.ps1 checks it against the game.
final class SimulationConstants {

    static final float MINIGAME_TRACK_HEIGHT = 360f;
    static final float MINIGAME_FISH_ICON_SIZE = 38f;
    static final float MINIGAME_MOTE_HALO_SIZE = 44f;

    // Catch window
    static final float MINIGAME_BAR_SIZE_FALLBACK = 120f;
    static final float MINIGAME_BAR_MIN_FRACTION = 0.08f;
    static final float MINIGAME_BAR_MAX_FRACTION = 0.6f;
    static final float MINIGAME_BAR_LIFT = 2.8f;
    static final float MINIGAME_BAR_GRAVITY = 2.0f;
    static final float MINIGAME_BAR_MAX_SPEED = 1.25f;
    static final float MINIGAME_BAR_RESTITUTION = 0.72f;
    static final float MINIGAME_BAR_REST_SPEED = 0.04f;

    // Movement
    static final float MINIGAME_FISH_BASE_SPEED = 0.68f;
    static final float MINIGAME_FISH_STIFFNESS = 3.6f;
    static final float MINIGAME_FISH_RESPONSE = 0.3f;
    static final float MINIGAME_THINK_TIME_MIN = 0.35f;
    static final float MINIGAME_THINK_TIME_MAX = 1.2f;
    static final float MINIGAME_DARTER_PATIENCE = 1.3f;
    static final float MINIGAME_WEAVER_LOW = 0.15f;
    static final float MINIGAME_WEAVER_HIGH = 0.85f;
    static final float MINIGAME_WEAVER_ARRIVE = 0.06f;
    static final float MINIGAME_WEAVER_DWELL_FLOOR = 0.6f;
    static final float MINIGAME_TWITCHER_CADENCE = 0.35f;
    static final float MINIGAME_TWITCHER_HOP = 0.16f;
    static final float MINIGAME_TWITCHER_LEAP = 0.45f;
    static final float MINIGAME_TWITCHER_LEAP_CHANCE = 0.15f;
    static final float MINIGAME_LUNGER_PATIENCE = 2.5f;
    static final float MINIGAME_LUNGER_NEAR = 0.08f;
    static final float MINIGAME_LUNGER_DASH_MULT = 2.1f;
    static final float MINIGAME_LUNGER_CREEP_MULT = 0.15f;

    // Difficulty and progress
    static final float MINIGAME_GLOBAL_DIFFICULTY = 1f;
    static final float MINIGAME_DIFFICULTY_BASELINE = 50f;
    static final float MINIGAME_DIFFICULTY_FLOOR = 0.7f;
    static final float MINIGAME_DIFFICULTY_SCALE = 0.3f;
    static final float MINIGAME_RATE_COMPRESSION = 0.6f;
    static final float MINIGAME_DIFFICULTY_MIN = 1f;
    static final float MINIGAME_DIFFICULTY_MAX = 200f;
    static final float MINIGAME_SPEED_MIN = 0.1f;
    static final float MINIGAME_SPEED_MAX = 4f;
    static final float MINIGAME_DEV_PROGRESS_FLOOR = 0.02f;
    static final float MINIGAME_PROGRESS_START = 0.4f;
    static final float MINIGAME_CATCH_RATE = 0.2f;
    static final float MINIGAME_ESCAPE_RATE = 0.21f;

    // Visual shake
    static final float MINIGAME_FISH_JITTER = 2.5f;
    static final float MINIGAME_FISH_JITTER_SPEED = 7f;
    static final float MINIGAME_FISH_JITTER_EFFORT = 0.8f;
}
