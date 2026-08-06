package catchrelease.campaign.fish.constants;

import java.awt.Color;

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
    public static final float MINIGAME_RESULT_GAP = 26f;
    public static final float MINIGAME_RESULT_WIDTH = 210f;
    public static final float MINIGAME_RESULT_PAD = 16f;

    /**
     * WIDTH is the width the card would like to be; these are what happens when it cannot have it.
     * <p>
     * Species names come off a table any mod can add rows to, and the long ones ran off the card.
     * So the width is a floor and the card grows to fit what is actually in it, up to MAX_WIDTH -
     * without a ceiling a long enough name would walk the card off the side of the screen.
     * <p>
     * COLUMN_GAP is the least space left between a row's label and its value, which are drawn to
     * opposite edges and would otherwise meet in the middle on a wide enough pair.
     */
    public static final float MINIGAME_RESULT_MAX_WIDTH = 400f;
    public static final float MINIGAME_RESULT_COLUMN_GAP = 14f;
    public static final float MINIGAME_RESULT_BOX = 100f;
    public static final float MINIGAME_RESULT_BOX_PAD = 12f;
    /** Under the box, before the name. Wider than the gaps between lines - it separates picture
     * from tally, not one row from the next. */
    public static final float MINIGAME_RESULT_BOX_GAP = 24f;
    public static final float MINIGAME_RESULT_TITLE_GAP = 14f;
    public static final float MINIGAME_RESULT_LINE_HEIGHT = 19f;
    public static final float MINIGAME_RESULT_LINE_DELAY = 0.24f;
    public static final float MINIGAME_RESULT_FADE = 0.15f;

    /**
     * These are bitmap fonts, and every size here is the size its font was cut at. Rendered at any
     * other size the glyphs are resampled and go soft - the title spent a while at 19 on a 21px cut,
     * which read as blur, not as style. Change a size only together with its font.
     */
    public static final String MINIGAME_RESULT_FONT = "graphics/fonts/insignia15LTaa.fnt";
    public static final String MINIGAME_RESULT_TITLE_FONT = "graphics/fonts/orbitron20aabold.fnt";
    public static final float MINIGAME_RESULT_TEXT_SIZE = 15f;
    public static final float MINIGAME_RESULT_TITLE_SIZE = 20f;

    /**
     * The close prompt breathes between two greys, PERIOD seconds for the full round trip - a still
     * grey line read as a caption, and the point of it is that the card is waiting on a key. Fixed
     * colours rather than the player's UI grey, so the dim end matches vanilla's grey and the lit
     * end stays a step above it whatever the UI is tinted.
     */
    public static final float MINIGAME_RESULT_PROMPT_ALPHA = 0.55f;
    public static final Color MINIGAME_RESULT_PROMPT_DIM = new Color(125, 125, 125);
    public static final Color MINIGAME_RESULT_PROMPT_LIT = new Color(200, 200, 200);
    public static final float MINIGAME_RESULT_PROMPT_PERIOD = 1f;

    /**
     * What a personal best is said with. The mark goes on the row that set it, the banner floats
     * above the specimen - one so the eye lands on the number, the other so it is stated outright.
     * <p>
     * The mark is a bare asterisk hung MARK_GAP past the value's right edge, in the value's own
     * colour - a footnote on the number. Anything wordier in there fought the label for the middle
     * of the row.
     * <p>
     * The banner is its own element rather than a row of the tally: a record is an event, and a row
     * with an empty label read as a leftover. It sits RECORD_GAP over the box and rides a sine,
     * BOUNCE pixels either way at RATE radians a second - gentle enough to say "look here" without
     * shaking the readout under it.
     * <p>
     * Records are per species and measured on length, since that is what a record is about.
     */
    public static final String MINIGAME_RESULT_RECORD_MARK = "*";
    public static final float MINIGAME_RESULT_MARK_GAP = 4f;
    public static final String MINIGAME_RESULT_RECORD = "NEW RECORD";
    public static final float MINIGAME_RESULT_RECORD_GAP = 8f;
    public static final float MINIGAME_RESULT_RECORD_BOUNCE = 3f;
    public static final float MINIGAME_RESULT_RECORD_BOUNCE_RATE = 4f;

    /**
     * Bubbles up the card, behind the content. The card is a readout first, so they are few, faint,
     * and outlines rather than fills - texture, not weather. Each rises at its own speed between
     * the two SPEEDs and sways DRIFT pixels on a sine at DRIFT_RATE radians a second, because a
     * bubble that goes straight up reads as a particle effect rather than a bubble.
     */
    public static final int MINIGAME_RESULT_BUBBLES = 9;
    public static final float MINIGAME_RESULT_BUBBLE_ALPHA = 0.1f;
    public static final float MINIGAME_RESULT_BUBBLE_SPEED_MIN = 14f;
    public static final float MINIGAME_RESULT_BUBBLE_SPEED_MAX = 32f;
    public static final float MINIGAME_RESULT_BUBBLE_DRIFT = 6f;
    public static final float MINIGAME_RESULT_BUBBLE_DRIFT_RATE = 1.2f;
    public static final float MINIGAME_RESULT_BUBBLE_SIZE_MIN = 2f;
    public static final float MINIGAME_RESULT_BUBBLE_SIZE_MAX = 5f;

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

    /**
     * What is shown on the track while the catch is running.
     * <p>
     * Not the species. You are playing something you cannot see yet - the readout at the end is
     * where you find out what it was, and knowing beforehand took that away. The real art is still
     * used for the celebration and the readout, since by then it has been landed.
     * <p>
     * Sonar, when it exists, is the thing that puts the species back on the track.
     */
    public static final String MINIGAME_TRACK_ICON = "graphics/catchrelease/icon/small_icon_catchrelease.png";

    /**
     * Treasure in the track: a thing that does not move, does not last, and is not the fish.
     * <p>
     * CHANCE is whether anything is down there at all this catch, and is deliberately low - treasure
     * that turns up every other catch is a second reward slot rather than treasure. POSITION_INSET
     * keeps it away from the ends of the track, where the bar rests anyway, so taking one always
     * costs ground on the fish.
     * <p>
     * HOLD_TIME is how long the bar has to be over it, and HOLD_DECAY how fast that is given back
     * when the bar slips off - a hold rather than a touch, so taking one is a decision instead of
     * something that happens on the way past.
     */
    public static final float TREASURE_CHANCE = 0.12f;

    /**
     * How many pieces one catch can hold, and how the count is rolled once the CHANCE gate has
     * passed: the weights pick 1, 2 or 3, with 3 kept a story. Later pieces only ever appear
     * SPAWN_INTERVAL seconds after the previous one resolved, so a catch has to be long as well
     * as lucky to see them - a short fight ends before the second piece was due.
     */
    public static final int TREASURE_MAX_PER_CATCH = 3;
    public static final float TREASURE_COUNT_WEIGHT_1 = 80f;
    public static final float TREASURE_COUNT_WEIGHT_2 = 17f;
    public static final float TREASURE_COUNT_WEIGHT_3 = 3f;
    public static final float TREASURE_SPAWN_INTERVAL = 9f;

    public static final float TREASURE_POSITION_INSET = 0.18f;
    public static final float TREASURE_LIFETIME_MIN = 6f;
    public static final float TREASURE_LIFETIME_MAX = 11f;
    public static final float TREASURE_HOLD_TIME = 1.45f;
    public static final float TREASURE_HOLD_DECAY = 1.5f;

    /**
     * Where the closing ring stops, as a multiple of the icon's own radius.
     * <p>
     * Just outside it. The ring is the only thing saying how far along the hold is, so it has to
     * arrive somewhere the eye reads as arrival - and a ring that halts with a visible gap still
     * between it and the icon reads as an animation that was cut off rather than one that finished.
     * Not touching, either: a ring drawn exactly on the edge fights the icon's own outline.
     */
    public static final float TREASURE_RING_END = 1.15f;

    /** Stand-in art, and the sizes it and its clock are drawn at. */
    public static final String TREASURE_ICON = "graphics/catchrelease/icon/small_icon_catchrelease2.png";
    public static final float TREASURE_ICON_SIZE = 26f;
    public static final float TREASURE_BAR_WIDTH = 30f;
    public static final float TREASURE_BAR_HEIGHT = 3f;
    public static final float TREASURE_BAR_GAP = 6f;

    /** The game's own drop groups, rolled rather than listed so they stay correct as they change. */
    public static final String TREASURE_GROUP_BLUEPRINTS = "blueprints";
    public static final String TREASURE_GROUP_RARE_TECH = "rare_tech";

    /** Roughly what a pile of commodity treasure is worth, before the roll picks which commodity. */
    public static final float TREASURE_COMMODITY_VALUE = 2500f;

    //minigame - the loot card, beside the track on the side the catch card is not
    public static final String MINIGAME_LOOT_TITLE = "RECOVERED";
    public static final float MINIGAME_LOOT_LINE_HEIGHT = 30f;
    public static final float MINIGAME_LOOT_ICON = 24f;
    public static final float MINIGAME_LOOT_ICON_GAP = 8f;
    public static final float MINIGAME_LOOT_COUNT_GAP = 12f;

    /**
     * Gold coins down the loot card, behind the content. The readout's bubbles say water, and water
     * is the catch's motif, not the till's - what came out of the wreck should rain. Held to the
     * bubbles' restraint all the same - few, faint, texture rather than weather - because this card
     * is a receipt before it is a spectacle. Each coin falls at its own speed between the two SPEEDs
     * and tumbles at its own rate between the two FLIP_RATEs, in radians a second, from its own
     * point in the turn, so the rain never moves in step with itself.
     */
    public static final int MINIGAME_LOOT_COINS = 10;
    public static final float MINIGAME_LOOT_COIN_ALPHA = 0.14f;
    public static final float MINIGAME_LOOT_COIN_SPEED_MIN = 24f;
    public static final float MINIGAME_LOOT_COIN_SPEED_MAX = 52f;
    public static final float MINIGAME_LOOT_COIN_SIZE_MIN = 3f;
    public static final float MINIGAME_LOOT_COIN_SIZE_MAX = 6f;
    public static final float MINIGAME_LOOT_COIN_FLIP_RATE_MIN = 2.5f;
    public static final float MINIGAME_LOOT_COIN_FLIP_RATE_MAX = 5f;

    /**
     * The flip is the coin's width running on |cos| while its height holds: a disc narrowing to an
     * edge and opening back out is a coin going face over edge, where a rotated disc is only a plate
     * spinning on the glass. EDGE is the width kept at edge-on, as a fraction of the coin's size, so
     * the turn bottoms out in a sliver rather than a blink; SHINE is how much brighter that sliver
     * gets than the face - the rim catching the light, which is what sells the turn as metal.
     */
    public static final float MINIGAME_LOOT_COIN_EDGE = 0.15f;
    public static final float MINIGAME_LOOT_COIN_EDGE_SHINE = 1.5f;

    /** Old gold rather than yellow. Yellow is a light source against the black field, and the coins
     *  are texture behind a readout, not lamps in it. */
    public static final Color MINIGAME_LOOT_COIN_COLOR = new Color(212, 172, 64);

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
     * Extra frame on the left only. With the even pad the whole read weighed right - the meter and
     * its dressing sit on that side - so the frame gives the left the difference back without
     * moving anything inside it.
     */
    public static final float MINIGAME_FRAME_EXTRA_LEFT = 10f;

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
    /** A bitmap font, so SIZE has to be the 24 it was cut at - upscaled it went soft. The pop still
     * swells past it for a moment, which is motion rather than a resting size. */
    public static final String CELEBRATION_FONT = "graphics/fonts/orbitron24aabold.fnt";
    public static final float CELEBRATION_TEXT_SIZE = 24f;
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
    public static final float BAR_CENTER_MULT = 0.75f;
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
     * The codex category, and the little sector map inside a location-data entry.
     * <p>
     * The map is drawn rather than composed of elements: every system in the sector is a dot and
     * there are several hundred of them. DOT is the size of one, MARK the ring around where the
     * record came from, and PAD keeps the whole thing off the panel's own border.
     */
    public static final String CODEX_CATEGORY_TITLE = "Fish";
    public static final String CODEX_CATEGORY_ICON = "graphics/catchrelease/icon/small_icon_catchrelease.png";
    public static final float CODEX_MAP_WIDTH = 320f;
    public static final float CODEX_MAP_HEIGHT = 200f;
    public static final float CODEX_MAP_PAD = 8f;
    public static final float CODEX_MAP_DOT_SIZE = 1f;
    public static final float CODEX_MAP_DOT_ALPHA = 0.75f;
    public static final float CODEX_MAP_MARK_RADIUS = 14f;
    public static final float CODEX_MAP_MARK_FILL_ALPHA = 0.18f;
    public static final float CODEX_MAP_MARK_PULSE = 0.12f;
    public static final float CODEX_MAP_MARK_PULSE_RATE = 2.4f;

    /**
     * The sector map intel entry: where things have been found, and where they are said to be.
     * <p>
     * A marker is a fact - somewhere one was actually landed. A lit system is a hint, which is all
     * location data amounts to for something never caught. The map itself is the game's own; the
     * only geometry left to this side is the sidebar.
     */
    public static final String MAP_INTEL_TITLE = "Catch locations";
    public static final float MAP_SIDEBAR_WIDTH = 240f;

    /**
     * Buried motes: the things on the other side of the fabric that a breach lamp exposes and a
     * harpoon takes through.
     * <p>
     * POPULATION is how many are kept within RANGE of the player, aimed at as a number rather than
     * as a spawn rate - so sitting still does not accumulate them and travelling does not outrun
     * them. SPAWN_MIN_RANGE keeps new ones out past the light's reach, because one appearing inside
     * the beam reads as the light making them rather than finding them, and CULL_RANGE is where one
     * left behind stops being worth keeping in the world.
     * <p>
     * They wander rather than travel: a heading held for HEADING_TIME and then turned by up to TURN,
     * with WEAVE on top of it. Rarity divides the time and multiplies both angles, so a rare one
     * changes its mind sooner and harder - which is most of what makes it hard to stay with.
     * SURFACE_RUN is how far the mote swims once it is through.
     */
    public static final String BURIED_ENTITY_ID = "catchrelease_BuriedMote";

    /**
     * How many are kept within reach at once, and the band they are seeded into.
     * <p>
     * The band is not written down as two distances any more. It is measured off what the lights can
     * actually see - CLEARANCE past that, and BAND deep - because the two were set independently
     * and disagreed: the beams reached a little over a thousand units, nothing was seeded inside
     * sixteen hundred, and everything out to thirty-five hundred counted towards the population. So
     * the fleet was surrounded by motes that were nearly all too far out to ever drift into a beam,
     * and the lights found almost nothing.
     * <p>
     * CLEARANCE is what keeps them out of sight when they appear - a mote surfacing inside the light
     * was not found, it was handed over - and BAND is kept shallow so the population sits where a
     * wandering mote can still cross into a beam.
     */
    public static final int BURIED_POPULATION = 14;
    public static final float BURIED_SPAWN_CLEARANCE = 160f;
    public static final float BURIED_SPAWN_BAND = 900f;
    public static final float BURIED_CULL_RANGE = 6000f;
    public static final float BURIED_CHECK_INTERVAL = 3f;
    public static final float BURIED_SPEED = 55f;
    public static final float BURIED_TURN = 70f;
    public static final float BURIED_WEAVE = 25f;
    public static final float BURIED_HEADING_TIME_MIN = 2.5f;
    public static final float BURIED_HEADING_TIME_MAX = 6f;
    public static final float BURIED_SURFACE_RUN = 900f;

    /**
     * The dent a buried mote makes in the searchlight - a negative impression, not the thing itself.
     * <p>
     * Drawn subtractively so it is a hole in the light rather than a mark on top of it, which is the
     * difference between "something is under there" and "something is drawn there". SIZE is the dent
     * and RING is the standing wave around it; both are scaled by how near the middle of the beam it
     * is, so sweeping the light over one makes it swell and fade rather than blink.
     */
    public static final float IMPRESSION_SIZE = 34f;
    public static final float IMPRESSION_ALPHA = 0.85f;
    public static final float IMPRESSION_RING_SIZE = 1.9f;
    public static final float IMPRESSION_RING_ALPHA = 0.45f;
    public static final float IMPRESSION_PULSE = 0.12f;
    public static final float IMPRESSION_PULSE_RATE = 2.2f;

    /**
     * How far the ring leans toward the rarity's colour at the identify upgrade's first level.
     * Partway on purpose: at 1 the ring would already be the rarity's own colour and the second
     * level would have nothing left to sell. Far enough off the beam's purple to say "look
     * closer", not far enough to name the tier.
     */
    public static final float IMPRESSION_IDENTIFY_HINT_BLEND = 0.5f;

    /**
     * The identify upgrade's glow - a wide wash of the ring's colour standing around the dent, and
     * the part of the upgrade a player actually sees. Recolouring the ring alone was tried first and
     * read as nothing: the dent and its ring are carved out of light so faint that one tint of it
     * against another is invisible at campaign zoom. So the glow deliberately does not inherit the
     * beams' resting alpha the way those two passes do - it answers only to the mark and the fade,
     * bright enough to name a colour at a glance. SIZE is a multiplier on the dent so the glow
     * breathes with it, and wider than the ring's 1.9 so it reads as an aura rather than a second
     * rim. HINT_MULT is the first level's share of the full glow, held down on purpose so the second
     * level still has something to sell.
     */
    public static final float IMPRESSION_GLOW_SIZE = 3.2f;
    public static final float IMPRESSION_GLOW_ALPHA = 0.4f;
    public static final float IMPRESSION_GLOW_HINT_MULT = 0.45f;

    /**
     * The reveal - the dent turning inside out under a beam that is a window. With hyperspace
     * showing where the light lands there is no lit fabric left to be a hole in, so the mote is
     * drawn instead, wearing exactly the look it has swimming in a pond: the same stacked glow
     * sprite in its rarity's own colour. FULL_PENETRATION is how far into the beam the switch
     * completes - at 0.4, a mote two fifths of the way from the rim to the centre is fully its
     * pond self, and the blend runs over the outer stretch. The reveal follows the live beam and
     * not the lingering mark on purpose - the window is where the beam is, and a mote seen
     * through it goes back to being a dent the moment the light moves on.
     */
    public static final float IMPRESSION_REVEAL_FULL_PENETRATION = 0.4f;

    /** The pond mote's own glow size, so an exposed mote is the size it will be once unearthed. */
    public static final float IMPRESSION_EXPOSED_GLOW_SIZE = 25f;

    /**
     * The lamps' passive awareness: anything under the fabric this close to a live beam shows as
     * a dent even though no light is on it - the fabric bruises around a burn. FALLBACK is the
     * radius with no upgrade row, measured from the beam's centre; NEAR_DENT_MAX caps how deep a
     * proximity-only dent gets, so a mote actually swept over still reads as the stronger find.
     */
    public static final float IMPRESSION_DETECT_FALLBACK = 400f;
    public static final float IMPRESSION_NEAR_DENT_MAX = 0.75f;

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
