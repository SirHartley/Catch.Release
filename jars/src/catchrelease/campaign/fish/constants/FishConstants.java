package catchrelease.campaign.fish.constants;

import java.awt.Color;

public class FishConstants {

    // Inner band of the sector (CORE_*; everything outside is RIM_*), measured from centre.
    // Sector is 164000 x 104000, so this is roughly the middle third.
    public static final float CORE_BAND_HALF_WIDTH = 27000f;
    public static final float CORE_BAND_HALF_HEIGHT = 17000f;

    /** Result readout panel: separate from the catch panel, positioned off its right edge. */
    public static final float MINIGAME_RESULT_GAP = 26f;
    public static final float MINIGAME_RESULT_WIDTH = 210f;
    public static final float MINIGAME_RESULT_PAD = 16f;

    /** Card grows from WIDTH to fit long (mod-added) species names, capped at MAX_WIDTH. */
    public static final float MINIGAME_RESULT_MAX_WIDTH = 400f;
    public static final float MINIGAME_RESULT_COLUMN_GAP = 14f;
    public static final float MINIGAME_RESULT_BOX = 100f;
    public static final float MINIGAME_RESULT_BOX_PAD = 12f;
    /** Gap under the box, before the name; wider than inter-line gaps to separate picture from tally. */
    public static final float MINIGAME_RESULT_BOX_GAP = 24f;
    public static final float MINIGAME_RESULT_TITLE_GAP = 14f;
    public static final float MINIGAME_RESULT_LINE_HEIGHT = 19f;
    public static final float MINIGAME_RESULT_LINE_DELAY = 0.24f;
    public static final float MINIGAME_RESULT_FADE = 0.15f;

    /** Bitmap fonts: size must match the size the font was cut at, or the glyphs resample soft. */
    public static final String MINIGAME_RESULT_FONT = "graphics/fonts/insignia15LTaa.fnt";
    public static final String MINIGAME_RESULT_TITLE_FONT = "graphics/fonts/orbitron20aabold.fnt";
    public static final float MINIGAME_RESULT_TEXT_SIZE = 15f;
    public static final float MINIGAME_RESULT_TITLE_SIZE = 20f;

    /** Close prompt pulses between DIM and LIT over PERIOD seconds; colors fixed rather than UI-tinted. */
    public static final float MINIGAME_RESULT_PROMPT_ALPHA = 0.55f;
    public static final Color MINIGAME_RESULT_PROMPT_DIM = new Color(125, 125, 125);
    public static final Color MINIGAME_RESULT_PROMPT_LIT = new Color(200, 200, 200);
    public static final float MINIGAME_RESULT_PROMPT_PERIOD = 1f;

    /**
     * Personal-best display: MARK is appended to the value on the row that set it, RECORD is a
     * banner floating above the specimen. Records are tracked per species, by length.
     */
    public static final String MINIGAME_RESULT_RECORD_MARK = "*";
    public static final float MINIGAME_RESULT_MARK_GAP = 4f;
    public static final String MINIGAME_RESULT_RECORD = "NEW RECORD";
    public static final float MINIGAME_RESULT_RECORD_GAP = 8f;
    public static final float MINIGAME_RESULT_RECORD_BOUNCE = 3f;
    public static final float MINIGAME_RESULT_RECORD_BOUNCE_RATE = 4f;

    /** Background bubbles behind the readout; each rises/drifts at its own rate so they don't sync. */
    public static final int MINIGAME_RESULT_BUBBLES = 9;
    public static final float MINIGAME_RESULT_BUBBLE_ALPHA = 0.1f;
    public static final float MINIGAME_RESULT_BUBBLE_SPEED_MIN = 14f;
    public static final float MINIGAME_RESULT_BUBBLE_SPEED_MAX = 32f;
    public static final float MINIGAME_RESULT_BUBBLE_DRIFT = 6f;
    public static final float MINIGAME_RESULT_BUBBLE_DRIFT_RATE = 1.2f;
    public static final float MINIGAME_RESULT_BUBBLE_SIZE_MIN = 2f;
    public static final float MINIGAME_RESULT_BUBBLE_SIZE_MAX = 5f;

    // minigame - panel
    public static final float MINIGAME_PANEL_WIDTH = 140f;
    public static final float MINIGAME_PANEL_HEIGHT = 480f;
    public static final float MINIGAME_TRACK_WIDTH = 52f;
    public static final float MINIGAME_TRACK_HEIGHT = 360f;
    public static final float MINIGAME_METER_WIDTH = 10f;
    /** Gap from track to meter; needs to clear both borders' own dressing (6 was too tight). */
    public static final float MINIGAME_METER_GAP = 16f;
    /** Below this share of the meter it reads as losing. */
    public static final float MINIGAME_METER_DANGER = 0.3f;
    /** Seconds the result stays up before the dialog closes itself. */
    public static final float MINIGAME_END_LINGER = 0.9f;
    public static final float MINIGAME_FISH_ICON_SIZE = 38f;

    /** Generic icon shown on the track during the catch; the species isn't revealed until landed. */
    public static final String MINIGAME_TRACK_ICON = "graphics/catchrelease/icon/small_icon_catchrelease.png";

    /** Chance a catch has treasure at all; kept low so it stays a bonus, not a second reward slot. */
    public static final float TREASURE_CHANCE = 0.12f;

    /** Piece count rolled 1-3 by weight once CHANCE passes; later pieces spawn only SPAWN_INTERVAL
     *  seconds after the previous one resolves. */
    public static final int TREASURE_MAX_PER_CATCH = 3;
    public static final float TREASURE_COUNT_WEIGHT_1 = 80f;
    public static final float TREASURE_COUNT_WEIGHT_2 = 17f;
    public static final float TREASURE_COUNT_WEIGHT_3 = 3f;
    public static final float TREASURE_SPAWN_INTERVAL = 9f;

    public static final float TREASURE_POSITION_INSET = 0.18f;
    public static final float TREASURE_LIFETIME_MIN = 6f;
    public static final float TREASURE_LIFETIME_MAX = 11f;
    /** Bar must hover over the piece for HOLD_TIME to collect it; HOLD_DECAY gives progress back
     *  if the bar slips off. */
    public static final float TREASURE_HOLD_TIME = 1.45f;
    public static final float TREASURE_HOLD_DECAY = 1.5f;

    /** Closing ring stops at RING_END * icon radius - just outside the icon, not touching it. */
    public static final float TREASURE_RING_END = 1.15f;

    public static final String TREASURE_ICON = "graphics/catchrelease/icon/small_icon_catchrelease2.png";
    public static final float TREASURE_ICON_SIZE = 26f;
    public static final float TREASURE_BAR_WIDTH = 30f;
    public static final float TREASURE_BAR_HEIGHT = 3f;
    public static final float TREASURE_BAR_GAP = 6f;

    /** Vanilla's own drop-group ids, referenced by name so they track vanilla if it changes them. */
    public static final String TREASURE_GROUP_BLUEPRINTS = "blueprints";
    public static final String TREASURE_GROUP_RARE_TECH = "rare_tech";

    /** Roughly what a pile of commodity treasure is worth, before the roll picks which commodity. */
    public static final float TREASURE_COMMODITY_VALUE = 2500f;

    // minigame - loot card, beside the track opposite the catch card
    public static final String MINIGAME_LOOT_TITLE = "RECOVERED";
    public static final float MINIGAME_LOOT_LINE_HEIGHT = 30f;
    public static final float MINIGAME_LOOT_ICON = 24f;
    public static final float MINIGAME_LOOT_ICON_GAP = 8f;
    public static final float MINIGAME_LOOT_COUNT_GAP = 12f;

    /** Coin rain behind the loot card; each coin falls and flips at its own rate so they don't sync. */
    public static final int MINIGAME_LOOT_COINS = 10;
    public static final float MINIGAME_LOOT_COIN_ALPHA = 0.14f;
    public static final float MINIGAME_LOOT_COIN_SPEED_MIN = 24f;
    public static final float MINIGAME_LOOT_COIN_SPEED_MAX = 52f;
    public static final float MINIGAME_LOOT_COIN_SIZE_MIN = 3f;
    public static final float MINIGAME_LOOT_COIN_SIZE_MAX = 6f;
    public static final float MINIGAME_LOOT_COIN_FLIP_RATE_MIN = 2.5f;
    public static final float MINIGAME_LOOT_COIN_FLIP_RATE_MAX = 5f;

    /** Coin flip: width scales by |cos| while height holds, so it reads as face-over-edge rather
     *  than a spinning disc. EDGE is the minimum edge-on width kept; SHINE brightens that sliver. */
    public static final float MINIGAME_LOOT_COIN_EDGE = 0.15f;
    public static final float MINIGAME_LOOT_COIN_EDGE_SHINE = 1.5f;

    /** Old gold rather than bright yellow, so it reads as texture rather than a light source. */
    public static final Color MINIGAME_LOOT_COIN_COLOR = new Color(212, 172, 64);

    // minigame - the bar the player flies
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

    // minigame - the fish
    /** Track fractions per second, before the fish's own speed and difficulty are applied. */
    public static final float MINIGAME_FISH_BASE_SPEED = 0.68f;
    /** How hard the fish pulls towards where it is going, and how long it takes to get up to it. */
    public static final float MINIGAME_FISH_STIFFNESS = 3.6f;
    public static final float MINIGAME_FISH_RESPONSE = 0.3f;

    /** Baseline icon twitch in pixels/speed; each fish scales it via the jitter column in fish.csv. */
    public static final float MINIGAME_FISH_JITTER = 2.5f;
    public static final float MINIGAME_FISH_JITTER_SPEED = 7f;
    /** How much harder it twitches when swimming hard, per unit of track speed. */
    public static final float MINIGAME_FISH_JITTER_EFFORT = 0.8f;

    public static final float MINIGAME_THINK_TIME_MIN = 0.35f;
    public static final float MINIGAME_THINK_TIME_MAX = 1.2f;

    /** Darters are the wait as much as the bolt, so they keep more of their thinking time. */
    public static final float MINIGAME_DARTER_PATIENCE = 1.3f;

    /**
     * Difficulty scales speed and restlessness by DIFFICULTY_FLOOR + SCALE * (difficulty /
     * BASELINE); compressed rather than linear, tuned against ~160ms reaction / 120ms decision
     * cadence so both low and high difficulties stay winnable. GLOBAL_DIFFICULTY is a global
     * multiplier on top of the per-fish values in fish.csv; 1 is as tuned.
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

    /** Extra left-side frame padding to balance the meter and its dressing sitting on that side. */
    public static final float MINIGAME_FRAME_EXTRA_LEFT = 10f;

    /** Smallest specimen's value as a fraction of base; largest is 2x base before grade, so base
     *  value stays what a typical specimen fetches. */
    public static final float VALUE_FLOOR_MULT = 0.35f;

    /** Catch celebration; one clock (CELEBRATION_TIME) drives everything else as shares or fixed sizes. */
    public static final float CELEBRATION_TIME = 1.6f;
    public static final float CELEBRATION_FADE_FROM = 0.7f;
    public static final float CELEBRATION_FLASH_TIME = 0.35f;
    public static final float CELEBRATION_FLASH_SIZE = 260f;
    public static final float CELEBRATION_FLASH_ALPHA = 0.55f;
    public static final int CELEBRATION_CONFETTI = 70;
    public static final float CELEBRATION_CONFETTI_SPEED = 380f;
    public static final float CELEBRATION_CONFETTI_GRAVITY = 320f;
    public static final float CELEBRATION_CONFETTI_SIZE = 6f;

    /** ARC: half-angle of the burst cone off straight up. RARITY_SHARE: fraction of confetti drawn
     *  in the fish's own color; the rest sample across the wheel at fixed SATURATION. */
    public static final float CELEBRATION_CONFETTI_ARC = 62f;
    public static final float CELEBRATION_CONFETTI_SPREAD = 22f;
    public static final float CELEBRATION_CONFETTI_RARITY_SHARE = 0.3f;
    public static final float CELEBRATION_CONFETTI_SATURATION = 0.7f;
    public static final float CELEBRATION_CONFETTI_BRIGHTNESS = 1f;
    public static final float CELEBRATION_FISH_SIZE = 96f;
    public static final float CELEBRATION_FISH_GROW = 0.35f;

    /** Backlight disc behind the specimen so its silhouette reads against the hyperspace backing;
     *  PULSE breathes its size. */
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
    /** Bitmap font cut at 24; must render at that size or it goes soft (the pop briefly overshoots it). */
    public static final String CELEBRATION_FONT = "graphics/fonts/orbitron24aabold.fnt";
    public static final float CELEBRATION_TEXT_SIZE = 24f;
    public static final float CELEBRATION_TEXT_ANGLE = 12f;
    public static final float CELEBRATION_TEXT_RISE = 110f;
    public static final float CELEBRATION_POP_TIME = 0.18f;
    public static final float CELEBRATION_POP_OVERSHOOT = 0.25f;

    /** Blank means no sound plays - a hook for audio that doesn't exist yet. */
    public static final String SOUND_CATCH = "";

    /** One per line of the readout as it lands. Vanilla's supplies-into-the-hold sound. */
    public static final String SOUND_RESULT_LINE = "ui_cargo_supplies";

    /** Player-flown bar. CENTER_MULT thins the middle so it reads as viewed-through rather than
     *  a solid block; the border pair gives it a lip above the track. */
    public static final float BAR_ALPHA_HOLDING = 0.5f;
    public static final float BAR_ALPHA_EMPTY = 0.4f;
    public static final float BAR_CENTER_MULT = 0.75f;
    public static final float BAR_BORDER_RADIUS = 3f;
    public static final float BAR_BORDER_WIDTH = 1f;
    public static final float BAR_BORDER_MULT = 2.2f;
    public static final float BAR_BORDER_INNER_INSET = 2f;
    public static final float BAR_BORDER_INNER_ALPHA = 0.45f;

    /** Rarity/grade marks on the cargo icon, top-left: rarity as an unbroken 3-pip bar, then grade
     *  as pips after it (so the bar can't be mistaken for part of the grade). */
    public static final float ITEM_MARK_INSET = 3f;
    public static final float ITEM_GRADE_PIP_SIZE = 3f;
    public static final float ITEM_GRADE_PIP_GAP = 2f;
    public static final float ITEM_RARITY_BAR_PIPS = 3f;
    public static final float ITEM_RARITY_BAR_GAP = 4f;
    public static final float ITEM_MARK_ALPHA = 0.9f;
    public static final float ITEM_MARK_EMPTY_ALPHA = 0.35f;
    public static final float ITEM_MARK_BACKING_PAD = 1f;
    public static final float ITEM_MARK_BACKING_ALPHA = 0.6f;

    /** BLANK: the item spec's own icon must be empty, since the cargo view draws it before the
     *  plugin can draw the species icon over it - there is no way to suppress that pass.
     *  FALLBACK is used where there's no specimen to read a species off (the codex). MOUSEOVER_MULT
     *  matches the cargo view's own hover-brighten pass. */
    public static final String ITEM_ICON_BLANK = "graphics/catchrelease/icon/blank.png";
    public static final String ITEM_ICON_FALLBACK = "graphics/catchrelease/icon/small_icon_catchrelease.png";
    public static final float ITEM_ICON_INSET = 5f;
    public static final float ITEM_ICON_MOUSEOVER_MULT = 0.5f;

    /** Codex category and its location-entry sector map, drawn as one dot per system (hundreds of
     *  them) rather than composed elements. DOT is dot size, MARK the ring around a record's origin. */
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

    /** Sector map intel: a marker means one was actually caught there; a lit system is only known
     *  location data. Map itself is vanilla's; only the sidebar is drawn here. */
    public static final String MAP_INTEL_TITLE = "Catch locations";
    public static final float MAP_SIDEBAR_WIDTH = 240f;

    /**
     * Buried motes (searchlight/harpoon targets). POPULATION is a maintained count within reach
     * of the player, not a spawn rate. Spawn band is measured off beam range: CLEARANCE keeps new
     * motes out of the light's reach, BAND is how deep the seed ring is; CULL_RANGE drops motes
     * that wander too far. They hold a heading for HEADING_TIME then turn, with WEAVE swaying on
     * top; rarity shortens legs and widens turns. SURFACE_RUN is how far a mote swims once exposed.
     */
    public static final String BURIED_ENTITY_ID = "catchrelease_BuriedMote";

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

    /** Dent a buried mote leaves in the searchlight, drawn subtractively (a hole in the light, not
     *  a mark on it). SIZE/RING scale with distance from the beam's center, so sweeping over one
     *  swells and fades rather than blinking. */
    public static final float IMPRESSION_SIZE = 34f;
    public static final float IMPRESSION_ALPHA = 0.85f;
    public static final float IMPRESSION_RING_SIZE = 1.9f;
    public static final float IMPRESSION_RING_ALPHA = 0.45f;
    public static final float IMPRESSION_PULSE = 0.12f;
    public static final float IMPRESSION_PULSE_RATE = 2.2f;

    /** Ring color blend toward the rarity color at identify-upgrade level 1; kept partial so
     *  level 2 still has something to add. */
    public static final float IMPRESSION_IDENTIFY_HINT_BLEND = 0.5f;

    /** Identify upgrade's glow around the dent. Recoloring the ring alone was invisible at campaign
     *  zoom, so the glow uses its own alpha rather than inheriting the beam's resting alpha. SIZE
     *  multiplies the dent size; HINT_MULT is level 1's (reduced) share of the full glow. */
    public static final float IMPRESSION_GLOW_SIZE = 3.2f;
    public static final float IMPRESSION_GLOW_ALPHA = 0.4f;
    public static final float IMPRESSION_GLOW_HINT_MULT = 0.45f;

    /** Fraction into the beam window at which the dent switches to full pond-mote art. Tracks the
     *  live beam, not the lingering mark, so it reverts as soon as the light moves off. */
    public static final float IMPRESSION_REVEAL_FULL_PENETRATION = 0.4f;

    /** The pond mote's own glow size, so an exposed mote is the size it will be once unearthed. */
    public static final float IMPRESSION_EXPOSED_GLOW_SIZE = 25f;

    /** Passive detection: motes within FALLBACK radius of a live beam show a shallow dent even
     *  without a direct sweep; NEAR_DENT_MAX caps that depth so an actual sweep still reads stronger. */
    public static final float IMPRESSION_DETECT_FALLBACK = 400f;
    public static final float IMPRESSION_NEAR_DENT_MAX = 0.75f;

    /** Aberration (how loosely a specimen holds to reality) takes the strongest of the three
     *  sources rather than summing them; abyss alone reaches 1. Ranges are light-years; SPREAD is
     *  jitter between two specimens from the same rupture. */
    public static final float ABERRATION_ABYSS_WEIGHT = 1f;
    public static final float ABERRATION_HYPERSHUNT_WEIGHT = 0.75f;
    public static final float ABERRATION_SLIPSTREAM_WEIGHT = 0.6f;
    public static final float ABERRATION_HYPERSHUNT_LY = 12f;
    public static final float ABERRATION_SLIPSTREAM_LY = 6f;
    public static final float ABERRATION_SPREAD = 0.05f;

    /** Track's hyperspace backing. Warp grid's border stays fixed so the swim stays inside the bar;
     *  CELLS is verts per side, the two radii are screen pixels (bar is 52 wide). */
    public static final float MINIGAME_TRACK_BG_ALPHA = 0.7f;
    public static final float MINIGAME_TRACK_BG_ZOOM = 1f;
    public static final int MINIGAME_TRACK_BG_WARP_CELLS = 6;
    public static final float MINIGAME_TRACK_BG_WARP_MIN = 1.5f;
    public static final float MINIGAME_TRACK_BG_WARP_MAX = 5f;
    public static final float MINIGAME_TRACK_BG_WARP_RATE = 1f;

    /** Backing fade to dark at top and bottom, under everything else in play. */
    public static final float MINIGAME_TRACK_FADE_TOP = 0.05f;
    public static final float MINIGAME_TRACK_FADE_BOTTOM = 0.95f;

    /** Border dressing around each bar (bright line, then a dimmer one further out). RADIUS clamps
     *  to what fits, so the 10-wide meter rounds tighter than the 52-wide track. */
    public static final float MINIGAME_BORDER_INSET = 2f;
    public static final float MINIGAME_BORDER_SPACING = 3f;
    public static final float MINIGAME_BORDER_RADIUS = 3f;
    public static final float MINIGAME_BORDER_WIDTH = 1f;
    public static final float MINIGAME_BORDER_ALPHA = 0.9f;
    public static final float MINIGAME_BORDER_OUTER_ALPHA = 0.35f;

    // minigame - the meter
    public static final float MINIGAME_PROGRESS_START = 0.4f;
    public static final float MINIGAME_CATCH_RATE = 0.2f;
    public static final float MINIGAME_ESCAPE_RATE = 0.21f;

}
