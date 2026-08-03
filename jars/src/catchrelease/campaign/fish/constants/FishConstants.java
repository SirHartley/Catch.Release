package catchrelease.campaign.fish.constants;

public class FishConstants {

    //sector regions - the inner band, measured from the sector centre. Systems inside it are CORE_*,
    //everything else RIM_*, and each band is quartered by direction. The vanilla sector runs
    //164000 x 104000, so this is roughly its middle third
    public static final float CORE_BAND_HALF_WIDTH = 27000f;
    public static final float CORE_BAND_HALF_HEIGHT = 17000f;

    /**
     * The readout: a panel of its own, off the right edge of the one the catch is played in, with a
     * cargo-square of the specimen at the top of it and its numbers below a line at a time.
     * <p>
     * Its own panel rather than a column inside the catch's, because the catch's panel is sized to
     * the playfield and nothing else - there is no room in there, and making room would leave the
     * catch sitting in a half-empty box for the whole time it is being played.
     * <p>
     * BOX is a hundred to the side because that is what a cargo cell is, and the point of the box is
     * to be recognisably one. LINE_DELAY is the wait between one number and the next; each one plays
     * SOUND_RESULT_LINE as it lands.
     */
    public static final float MINIGAME_RESULT_GAP = 16f;
    public static final float MINIGAME_RESULT_WIDTH = 210f;
    public static final float MINIGAME_RESULT_PAD = 16f;
    public static final float MINIGAME_RESULT_BOX = 100f;
    public static final float MINIGAME_RESULT_BOX_PAD = 12f;
    public static final float MINIGAME_RESULT_TITLE_GAP = 14f;
    public static final float MINIGAME_RESULT_LINE_HEIGHT = 19f;
    public static final float MINIGAME_RESULT_LINE_DELAY = 0.24f;
    public static final float MINIGAME_RESULT_FADE = 0.15f;
    public static final String MINIGAME_RESULT_FONT = "graphics/fonts/insignia15LTaa.fnt";
    public static final String MINIGAME_RESULT_TITLE_FONT = "graphics/fonts/insignia21LTaa.fnt";
    public static final float MINIGAME_RESULT_TEXT_SIZE = 15f;
    public static final float MINIGAME_RESULT_TITLE_SIZE = 19f;
    public static final float MINIGAME_RESULT_PROMPT_ALPHA = 0.55f;

    /**
     * What a personal best is said with. The mark goes on the row that set it, the line goes at the
     * end of the tally - one so the eye lands on the number, the other so it is stated outright.
     * <p>
     * Records are per species and measured on length, since that is what a record is about.
     */
    public static final String MINIGAME_RESULT_RECORD_MARK = "* best";
    public static final String MINIGAME_RESULT_RECORD = "NEW RECORD";

    //minigame - panel. Wide enough for the playfield and its dressing, and no wider
    public static final float MINIGAME_PANEL_WIDTH = 140f;
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
    public static final float MINIGAME_FISH_ICON_SIZE = 38f;

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
    /** Baseline twitch, in pixels. Each fish scales it with the jitter column in fish.csv. */
    public static final float MINIGAME_FISH_JITTER = 2.5f;
    public static final float MINIGAME_FISH_JITTER_SPEED = 7f;
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

    /** Bounds on the two values a running catch can have retuned under it. */
    public static final float MINIGAME_DIFFICULTY_MIN = 1f;
    public static final float MINIGAME_DIFFICULTY_MAX = 200f;
    public static final float MINIGAME_SPEED_MIN = 0.1f;
    public static final float MINIGAME_SPEED_MAX = 4f;

    /** Dev mode only: the meter stops here instead of running out, so nothing is ever lost. */
    public static final float MINIGAME_DEV_PROGRESS_FLOOR = 0.02f;

    /** Padding between the playfield and whatever frames it. */
    public static final float MINIGAME_FRAME_PAD = 12f;

    /**
     * What the smallest specimen of a species is worth as a share of its base value. The largest is
     * worth twice the base before its grade is applied, so the range either side of the middle is
     * even and the base value stays the number a typical specimen fetches.
     */
    public static final float VALUE_FLOOR_MULT = 0.35f;

    /**
     * The catch celebration. One clock at CELEBRATION_TIME drives all of it; the rest are shares of
     * that or plain sizes, so the whole thing can be retimed from one number.
     */
    public static final float CELEBRATION_TIME = 1.6f;
    public static final float CELEBRATION_FADE_FROM = 0.7f;
    public static final float CELEBRATION_FLASH_TIME = 0.35f;
    public static final float CELEBRATION_FLASH_SIZE = 260f;
    public static final float CELEBRATION_FLASH_ALPHA = 0.55f;
    public static final int CELEBRATION_CONFETTI = 70;
    public static final float CELEBRATION_CONFETTI_SPEED = 380f;
    public static final float CELEBRATION_CONFETTI_GRAVITY = 320f;
    public static final float CELEBRATION_CONFETTI_SIZE = 6f;

    /**
     * The shape and the colour of the burst.
     * <p>
     * ARC is the half-angle off straight up, so a wider one throws the burst out sideways as well as
     * over the top; SPREAD is how far apart they start. RARITY_SHARE is the fraction that take the
     * fish's own colour - enough to keep the burst reading as this catch rather than as any catch,
     * while the rest are drawn from across the wheel at a fixed saturation so no one of them is
     * washed out or lurid next to the others.
     */
    public static final float CELEBRATION_CONFETTI_ARC = 62f;
    public static final float CELEBRATION_CONFETTI_SPREAD = 22f;
    public static final float CELEBRATION_CONFETTI_RARITY_SHARE = 0.3f;
    public static final float CELEBRATION_CONFETTI_SATURATION = 0.7f;
    public static final float CELEBRATION_CONFETTI_BRIGHTNESS = 1f;
    public static final float CELEBRATION_FISH_SIZE = 96f;
    public static final float CELEBRATION_FISH_GROW = 0.35f;

    /**
     * What the specimen is shown against: a disc of light behind it, ringed the way the rest of the
     * panel is ringed - a bright line just off the light and a dimmer one outside that.
     * <p>
     * The fish had nothing behind it and sat over a warping hyperspace backing, which is the worst
     * thing to read a silhouette against. The disc is there for the whole celebration rather than
     * for the flash's third of a second, and breathes by PULSE either side of its size so it is
     * clearly lit rather than clearly painted.
     */
    public static final float CELEBRATION_BACKLIGHT_SIZE = 78f;
    public static final float CELEBRATION_BACKLIGHT_ALPHA = 0.55f;
    public static final float CELEBRATION_BACKLIGHT_EDGE_ALPHA = 0.06f;
    public static final float CELEBRATION_BACKLIGHT_PULSE = 0.06f;
    public static final float CELEBRATION_BACKLIGHT_PULSE_RATE = 5f;
    public static final float CELEBRATION_RING_ALPHA = 0.8f;
    public static final float CELEBRATION_RING_OUTER_ALPHA = 0.3f;
    public static final float CELEBRATION_RING_SPACING = 5f;
    public static final float CELEBRATION_RING_WIDTH = 1f;
    public static final String CELEBRATION_TEXT = "Caught!";
    public static final String CELEBRATION_FONT = "graphics/fonts/orbitron24aabold.fnt";
    public static final float CELEBRATION_TEXT_SIZE = 34f;
    public static final float CELEBRATION_TEXT_ANGLE = 12f;
    public static final float CELEBRATION_TEXT_RISE = 110f;
    public static final float CELEBRATION_POP_TIME = 0.18f;
    public static final float CELEBRATION_POP_OVERSHOOT = 0.25f;

    /**
     * Sound for the catch. Blank means nothing plays - these are hooks for sound that does not exist
     * yet, not ids waiting to be found.
     */
    public static final String SOUND_CATCH = "";

    /** One per line of the readout as it lands. Vanilla's supplies-into-the-hold sound. */
    public static final String SOUND_RESULT_LINE = "ui_cargo_supplies";

    /**
     * The window the player flies.
     * <p>
     * The body lifts to full at top and bottom and thins to CENTER_MULT of that through the middle,
     * so it reads as something looked through rather than a painted block. Around it a bright outline
     * at the edge and a dark one just inside: the pair is what gives the window a lip, and the lip is
     * what makes it sit above the track rather than in it.
     */
    public static final float BAR_ALPHA_HOLDING = 0.5f;
    public static final float BAR_ALPHA_EMPTY = 0.4f;
    public static final float BAR_CENTER_MULT = 0.3f;
    public static final float BAR_BORDER_RADIUS = 3f;
    public static final float BAR_BORDER_WIDTH = 1f;
    public static final float BAR_BORDER_MULT = 2.2f;
    public static final float BAR_BORDER_INNER_INSET = 2f;
    public static final float BAR_BORDER_INNER_ALPHA = 0.45f;

    /**
     * The marks drawn onto a catch's cargo icon, both on one row across the top left: the rarity as a
     * single unbroken bar, then the grade as pips after it.
     * <p>
     * The bar is three pips long, so it cannot be counted as part of the grade - it is plainly a
     * different kind of mark on the same scale. Reading left to right the row is what the species is
     * followed by what this one is, which is the order the two are learned in. Kept small and inset;
     * the icon has to read as the fish first.
     */
    public static final float ITEM_MARK_INSET = 3f;
    public static final float ITEM_GRADE_PIP_SIZE = 3f;
    public static final float ITEM_GRADE_PIP_GAP = 2f;
    public static final float ITEM_RARITY_BAR_PIPS = 3f;
    public static final float ITEM_RARITY_BAR_GAP = 4f;
    public static final float ITEM_MARK_ALPHA = 0.9f;
    public static final float ITEM_MARK_EMPTY_ALPHA = 0.35f;
    public static final float ITEM_MARK_BACKING_PAD = 1f;
    public static final float ITEM_MARK_BACKING_ALPHA = 0.6f;

    /**
     * A specimen shows its own species' icon rather than a stand-in, which means the item spec's icon
     * has to be nothing at all - the cargo view draws that before the plugin gets to draw anything,
     * and there is no asking it not to. ITEM_ICON_FALLBACK is what stands in where there is no
     * specimen to read a species off, the codex being the case that matters.
     * <p>
     * INSET is per side, off a cell that is not ours to size; MOUSEOVER_MULT matches the extra
     * additive pass the cargo view gives every other icon, so a catch lights up under the cursor
     * along with the rest of the hold.
     */
    public static final String ITEM_ICON_BLANK = "graphics/catchrelease/icon/blank.png";
    public static final String ITEM_ICON_FALLBACK = "graphics/catchrelease/icon/small_icon_catchrelease.png";
    public static final float ITEM_ICON_INSET = 5f;
    public static final float ITEM_ICON_MOUSEOVER_MULT = 0.5f;

    /**
     * Aberration - how loosely a specimen holds to reality, from where it was taken.
     * <p>
     * The three sources are taken at their strongest rather than summed, so the weights say how bad
     * each one is at its worst rather than how much it contributes. The abyss reaches 1 on its own;
     * a hypershunt or a slipstream alone leaves a fish short of the worst there is. The ranges are
     * light-years, and SPREAD is the jitter between two specimens out of the same rupture.
     */
    public static final float ABERRATION_ABYSS_WEIGHT = 1f;
    public static final float ABERRATION_HYPERSHUNT_WEIGHT = 0.75f;
    public static final float ABERRATION_SLIPSTREAM_WEIGHT = 0.6f;
    public static final float ABERRATION_HYPERSHUNT_LY = 12f;
    public static final float ABERRATION_SLIPSTREAM_LY = 6f;
    public static final float ABERRATION_SPREAD = 0.05f;

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
    public static final float MINIGAME_TRACK_FADE_TOP = 0.05f;
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
