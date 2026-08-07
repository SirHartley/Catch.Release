package catchrelease.campaign.fish.constants;

import com.fs.starfarer.api.util.Pair;

import java.awt.Color;

public class FishConstants {

    //sector regions - band around the sector centre; inside is CORE_*, outside RIM_*, each
    //quartered by direction. Roughly the middle third of the 164000 x 104000 sector.
    public static final float CORE_BAND_HALF_WIDTH = 27000f;
    public static final float CORE_BAND_HALF_HEIGHT = 17000f;

    /** Result readout: its own panel to the right of the catch panel - no room to fit inside it. */
    public static final float MINIGAME_RESULT_GAP = 26f;
    /** Floor width; the card grows to fit long species names, up to MAX_WIDTH. */
    public static final float MINIGAME_RESULT_WIDTH = 210f;
    public static final float MINIGAME_RESULT_PAD = 16f;

    public static final float MINIGAME_RESULT_MAX_WIDTH = 400f;
    /** Minimum gap kept between a row's label and its value. */
    public static final float MINIGAME_RESULT_COLUMN_GAP = 14f;
    /** Matches a cargo cell, so the specimen icon reads as one. */
    public static final float MINIGAME_RESULT_BOX = 100f;
    public static final float MINIGAME_RESULT_BOX_PAD = 12f;
    /** Separates the icon from the stat lines, not one line from the next. */
    public static final float MINIGAME_RESULT_BOX_GAP = 24f;
    public static final float MINIGAME_RESULT_TITLE_GAP = 14f;
    public static final float MINIGAME_RESULT_LINE_HEIGHT = 19f;
    /** Delay between stat lines landing; each plays SOUND_RESULT_LINE. */
    public static final float MINIGAME_RESULT_LINE_DELAY = 0.24f;
    public static final float MINIGAME_RESULT_FADE = 0.15f;

    /** Bitmap fonts - size must match the cut size or the glyphs blur when resampled. */
    public static final String MINIGAME_RESULT_FONT = "graphics/fonts/insignia15LTaa.fnt";
    public static final String MINIGAME_RESULT_TITLE_FONT = "graphics/fonts/orbitron20aabold.fnt";
    public static final float MINIGAME_RESULT_TEXT_SIZE = 15f;
    public static final float MINIGAME_RESULT_TITLE_SIZE = 20f;

    /**
     * Close prompt breathes between DIM and LIT over PERIOD seconds. Fixed colours rather than
     * the player's UI grey, so it stays readable whatever the UI is tinted.
     */
    public static final float MINIGAME_RESULT_PROMPT_ALPHA = 0.55f;
    public static final Color MINIGAME_RESULT_PROMPT_DIM = new Color(125, 125, 125);
    public static final Color MINIGAME_RESULT_PROMPT_LIT = new Color(200, 200, 200);
    public static final float MINIGAME_RESULT_PROMPT_PERIOD = 1f;

    /**
     * Personal-best marker: an asterisk after the value (MARK_GAP), plus a banner floating
     * RECORD_GAP above the specimen, bouncing BOUNCE px at RATE rad/s. Records are tracked per
     * species by length.
     */
    public static final String MINIGAME_RESULT_RECORD_MARK = "*";
    public static final float MINIGAME_RESULT_MARK_GAP = 4f;
    public static final String MINIGAME_RESULT_RECORD = "NEW RECORD";
    public static final float MINIGAME_RESULT_RECORD_GAP = 8f;
    public static final float MINIGAME_RESULT_RECORD_BOUNCE = 3f;
    public static final float MINIGAME_RESULT_RECORD_BOUNCE_RATE = 4f;

    /**
     * Background bubbles behind the readout: few, faint outlines. Each rises at its own speed
     * (SPEED range) and sways DRIFT px on a sine at DRIFT_RATE rad/s.
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
    /** Gap from track to meter, wide enough that their border dressing doesn't cross. */
    public static final float MINIGAME_METER_GAP = 16f;
    /** Below this share of the meter it reads as losing. */
    public static final float MINIGAME_METER_DANGER = 0.3f;
    /** Seconds the result stays up before the dialog closes itself. */
    public static final float MINIGAME_END_LINGER = 0.9f;
    public static final float MINIGAME_FISH_ICON_SIZE = 38f;

    /** Icon shown on the track while the catch runs - not the species art, which appears only
     *  once landed (sonar, if it exists, would restore it here). */
    public static final String MINIGAME_TRACK_ICON = "graphics/catchrelease/icon/small_icon_catchrelease.png";

    /**
     * Treasure spawned in the track: doesn't move, doesn't last, isn't the fish. CHANCE is the
     * per-catch odds it appears at all. POSITION_INSET keeps it off the track ends. HOLD_TIME/
     * HOLD_DECAY require sustained contact rather than a touch.
     */
    public static final float TREASURE_CHANCE = 0.12f;

    /** Piece count rolled 1-3 by weight once CHANCE passes; extra pieces need SPAWN_INTERVAL
     *  seconds after the last one resolved. */
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

    /** Closing ring's stop radius as a multiple of the icon's own radius - just outside it,
     *  not touching (touching fights the icon's outline; a visible gap reads as unfinished). */
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
    public static final String MINIGAME_LOOT_TITLE = "Recovered";
    public static final float MINIGAME_LOOT_LINE_HEIGHT = 30f;
    public static final float MINIGAME_LOOT_ICON = 24f;
    public static final float MINIGAME_LOOT_ICON_GAP = 8f;
    public static final float MINIGAME_LOOT_COUNT_GAP = 12f;

    /**
     * Coins falling behind the loot card. Few, faint outlines like the readout's bubbles - a
     * receipt before a spectacle. Each falls at its own SPEED and flips at its own FLIP_RATE,
     * out of phase, so the rain never repeats in lockstep.
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
     * Flip animation: width scales by |cos| while height holds, so a disc narrows to an edge and
     * opens back out (a rotated disc alone reads as a plate, not a coin). EDGE is the minimum
     * width kept at edge-on; EDGE_SHINE brightens that sliver to sell it as a metal rim.
     */
    public static final float MINIGAME_LOOT_COIN_EDGE = 0.15f;
    public static final float MINIGAME_LOOT_COIN_EDGE_SHINE = 1.5f;

    /**
     * Secondary tumble: WOBBLE is a slower flip on the other axis (to DEPTH, not to the edge, so
     * it doesn't collapse the coin to a point with the primary flip). SPIN walks the ellipse
     * round. All rates are rolled per coin, independent and non-multiples of each other.
     */
    public static final float MINIGAME_LOOT_COIN_WOBBLE_RATE_MIN = 0.9f;
    public static final float MINIGAME_LOOT_COIN_WOBBLE_RATE_MAX = 1.9f;
    public static final float MINIGAME_LOOT_COIN_WOBBLE_DEPTH = 0.55f;
    public static final float MINIGAME_LOOT_COIN_SPIN_RATE_MIN = 0.6f;
    public static final float MINIGAME_LOOT_COIN_SPIN_RATE_MAX = 2.2f;

    /** Old gold rather than yellow - the coins are background texture, not a light source. */
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

    /** Baseline icon twitch, in pixels; each fish scales it via fish.csv's jitter column. */
    public static final float MINIGAME_FISH_JITTER = 2.5f;
    public static final float MINIGAME_FISH_JITTER_SPEED = 7f;
    /** How much harder it twitches when swimming hard, per unit of track speed. */
    public static final float MINIGAME_FISH_JITTER_EFFORT = 0.8f;

    public static final float MINIGAME_THINK_TIME_MIN = 0.35f;
    public static final float MINIGAME_THINK_TIME_MAX = 1.2f;

    /** Darters are the wait as much as the bolt, so they keep more of their thinking time. */
    public static final float MINIGAME_DARTER_PATIENCE = 1.3f;

    /**
     * The one knob: scales fish speed/restlessness overall; fish.csv keeps relative per-fish
     * shape. Curve is FLOOR + SCALE * (difficulty / BASELINE), compressed rather than linear
     * (linear made high difficulties unwinnable, low ones trivial) and tuned against a
     * ~160ms-reaction player model rather than a frame-perfect one. 1 = as tuned.
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

    /** Extra left-side frame padding, balancing the meter's visual weight on the right. */
    public static final float MINIGAME_FRAME_EXTRA_LEFT = 10f;

    /** Smallest specimen's value as a fraction of base; largest is 2x base before grade, so
     *  base value sits at the midpoint. */
    public static final float VALUE_FLOOR_MULT = 0.35f;

    /** Catch celebration: one clock (CELEBRATION_TIME) drives everything below it as shares or
     *  plain sizes, so the whole thing can be retimed from one number. */
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
     * Burst shape: ARC is half-angle off straight up, SPREAD is starting separation.
     * RARITY_SHARE is the fraction taking the fish's own colour; the rest are drawn across the
     * wheel at a fixed SATURATION so none reads washed out or lurid.
     */
    public static final float CELEBRATION_CONFETTI_ARC = 62f;
    public static final float CELEBRATION_CONFETTI_SPREAD = 22f;
    public static final float CELEBRATION_CONFETTI_RARITY_SHARE = 0.3f;
    public static final float CELEBRATION_CONFETTI_SATURATION = 0.7f;
    public static final float CELEBRATION_CONFETTI_BRIGHTNESS = 1f;
    public static final float CELEBRATION_FISH_SIZE = 96f;
    public static final float CELEBRATION_FISH_GROW = 0.35f;

    /**
     * Backlight disc behind the specimen, ringed like the rest of the panel - needed since the
     * fish otherwise sits over the warping hyperspace backing, the worst thing to read a
     * silhouette against. Breathes by PULSE either side of its size.
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
    /** Bitmap font cut at 24 - SIZE must match or it blurs when scaled. Pop still overshoots it briefly. */
    public static final String CELEBRATION_FONT = "graphics/fonts/orbitron24aabold.fnt";
    public static final float CELEBRATION_TEXT_SIZE = 24f;
    public static final float CELEBRATION_TEXT_ANGLE = 12f;
    public static final float CELEBRATION_TEXT_RISE = 110f;
    public static final float CELEBRATION_POP_TIME = 0.18f;
    public static final float CELEBRATION_POP_OVERSHOOT = 0.25f;

    /** Blank = no sound wired up yet, not a placeholder id waiting to be found. */
    public static final String SOUND_CATCH = "";

    /** One per line of the readout as it lands. Vanilla's supplies-into-the-hold sound. */
    public static final String SOUND_RESULT_LINE = "ui_cargo_supplies";

    /**
     * The window the player flies: full alpha at top/bottom, thinning to CENTER_MULT through the
     * middle so it reads as looked-through rather than a painted block. A bright outer + dark
     * inner outline give it a raised lip above the track.
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
     * Marks on a catch's cargo icon, top left: rarity as an unbroken bar, then grade as pips
     * after it. The bar is 3 pips long so it can't be mistaken for part of the grade. Kept small
     * and inset - the icon has to read as the fish first.
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
     * A specimen shows its own species icon, so the item spec's icon must stay blank (the cargo
     * view draws it first, before the plugin can draw anything). FALLBACK is used where there's
     * no specimen to read a species from (the codex). MOUSEOVER_MULT matches the cargo view's
     * own hover highlight.
     */
    public static final String ITEM_ICON_BLANK = "graphics/catchrelease/icon/blank.png";
    public static final String ITEM_ICON_FALLBACK = "graphics/catchrelease/icon/small_icon_catchrelease.png";
    public static final float ITEM_ICON_INSET = 5f;
    public static final float ITEM_ICON_MOUSEOVER_MULT = 0.5f;

    /**
     * Codex category, and the mini sector map inside a location-data entry. The map is drawn
     * rather than composed of elements (several hundred systems). DOT is a system's size, MARK
     * the ring around where the record came from.
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
     * Sector map intel entry. A marker means an actual catch was landed there; a lit system is
     * only a hint from unconfirmed location data. The map is vanilla's own - only the sidebar
     * geometry belongs to this side.
     */
    public static final String MAP_INTEL_TITLE = "Catch locations";
    public static final float MAP_SIDEBAR_WIDTH = 240f;

    /**
     * Buried motes: hidden under the fabric until a searchlight impression reveals them, taken
     * through by harpoon. POPULATION is a standing count kept near the player, not a spawn rate.
     * SPAWN_CLEARANCE keeps new motes out of beam range (so none spawn already "found");
     * SPAWN_BAND stays shallow so a wandering mote can still cross into a beam. They wander:
     * hold a heading for HEADING_TIME, then turn up to TURN plus WEAVE; rarity shortens the hold
     * and widens both angles. SURFACE_RUN is how far a mote swims once exposed.
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

    /**
     * Impression: a subtractive dent showing where a buried mote sits under the fabric - a hole
     * in the light, not a mark on it. SIZE/ALPHA are the dent, RING the standing wave around it;
     * both scale with proximity to the beam's centre.
     */
    public static final float IMPRESSION_SIZE = 34f;
    public static final float IMPRESSION_ALPHA = 0.85f;
    public static final float IMPRESSION_RING_SIZE = 1.9f;
    public static final float IMPRESSION_RING_ALPHA = 0.45f;
    public static final float IMPRESSION_PULSE = 0.12f;
    public static final float IMPRESSION_PULSE_RATE = 2.2f;

    /** Ring's blend toward the rarity colour at identify-upgrade level 1 - partial, so level 2
     *  still has something to add. */
    public static final float IMPRESSION_IDENTIFY_HINT_BLEND = 0.5f;

    /**
     * Identify upgrade's glow: a wash of the ring's colour, bright enough to name a colour at a
     * glance (recolouring the ring/dent alone is too faint to read at campaign zoom - it ignores
     * the beam's resting alpha for that reason). SIZE multiplies the dent size; HINT_MULT is
     * level 1's share of the full glow.
     */
    public static final float IMPRESSION_GLOW_SIZE = 3.2f;
    public static final float IMPRESSION_GLOW_ALPHA = 0.4f;
    public static final float IMPRESSION_GLOW_HINT_MULT = 0.45f;

    /**
     * Reveal: under a beam showing hyperspace, the mote is drawn as its pond self instead of a
     * dent. FULL_PENETRATION (0-1, rim to centre) is how far into the beam the switch completes.
     * Follows the live beam, not the lingering mark - it reverts the moment the light moves on.
     */
    public static final float IMPRESSION_REVEAL_FULL_PENETRATION = 0.4f;

    /** The pond mote's own glow size, so an exposed mote is the size it will be once unearthed. */
    public static final float IMPRESSION_EXPOSED_GLOW_SIZE = 25f;

    /**
     * Passive proximity detection: anything under the fabric within FALLBACK of a beam's centre
     * shows as a dent even with no light on it. NEAR_DENT_MAX caps how deep a proximity-only
     * dent gets, so an actually-swept mote still reads as the stronger find.
     */
    public static final float IMPRESSION_DETECT_FALLBACK = 400f;
    public static final float IMPRESSION_NEAR_DENT_MAX = 0.75f;

    /**
     * Aberration: how far a specimen departs from normal, from where it was caught - the inverse of
     * the coherence the player is shown. The four sources take their strongest rather than summing
     * (abyss alone reaches 1; none of the others do). Ranges are light-years; SPREAD is jitter
     * between two specimens from the same rupture.
     * <p>
     * A black hole outweighs a hypershunt and reaches less far, which is the difference between a
     * thing that bends space where it stands and a thing that draws power across a region. The
     * ordering is vanilla's own: it rates black holes its strongest slipsurge source, above neutron
     * stars, and a tap not at all.
     */
    public static final float ABERRATION_ABYSS_WEIGHT = 1f;
    public static final float ABERRATION_BLACKHOLE_WEIGHT = 0.85f;
    public static final float ABERRATION_HYPERSHUNT_WEIGHT = 0.75f;
    public static final float ABERRATION_SLIPSTREAM_WEIGHT = 0.6f;
    public static final float ABERRATION_BLACKHOLE_LY = 5f;
    public static final float ABERRATION_HYPERSHUNT_LY = 12f;
    public static final float ABERRATION_SLIPSTREAM_LY = 6f;
    /**
     * A gate, which reads as two different things depending on whether anything is coming through
     * it. Dormant it is a hole with the lid on - close range, and not much. Lit, it is an opening
     * between here and somewhere else that is being held open on purpose, which is the strongest
     * thing in the sector short of the abyss itself.
     */
    public static final float ABERRATION_GATE_WEIGHT = 0.3f;
    public static final float ABERRATION_GATE_LY = 3f;
    public static final float ABERRATION_GATE_ACTIVE_WEIGHT = 0.85f;
    public static final float ABERRATION_GATE_ACTIVE_LY = 6f;

    /**
     * Something big enough to bend a planet, which bends rather less than a gate and only right on
     * top of it. Nothing in vanilla is this - see {@code Aberration}'s foreign tag list.
     */
    public static final float ABERRATION_ENGINE_WEIGHT = 0.3f;
    public static final float ABERRATION_ENGINE_LY = 1f;

    public static final float ABERRATION_SPREAD = 0.05f;

    /**
     * The low-coherence overlay: a full-screen warp and purple lean while a rig runs somewhere
     * thin. FLOOR/CEIL map the place's steady aberration onto overlay level 0-1 - nothing at
     * "stable", full by "barely holding", the same cuts the coherence labels use. FLOOR doubles
     * as the least share worth blaming on a named source in the terrain bar. EASE_* are seconds
     * to fade in and out; the whisper loop's volume rides the level.
     */
    public static final float COHERENCE_OVERLAY_FLOOR = 0.12f;
    public static final float COHERENCE_OVERLAY_CEIL = 0.8f;

    /** Centre-distance the overlay may creep in to at full level (0 centre, 1 mid-edge), never
     *  past - the middle of the screen stays readable however bad the water gets. */
    public static final float COHERENCE_OVERLAY_INNER_CLEAR = 0.4f;

    public static final float COHERENCE_OVERLAY_EASE_IN = 2f;
    public static final float COHERENCE_OVERLAY_EASE_OUT = 1.5f;
    public static final String SOUND_COHERENCE_WHISPERS = "catchrelease_coherence_whispers";
    public static final float COHERENCE_WHISPER_VOLUME = 0.7f;

    /**
     * How near a fishing boat counts as approaching one, for the warp.
     * <p>
     * The other thing that turns the screen over. A rig running is the player doing something to
     * the fabric; a fishing boat is the fabric having already done something to somebody, and
     * standing next to it in bad water should read the same way.
     */
    public static final float COHERENCE_FISHERMAN_RANGE = 2500f;

    /**
     * The aberration a boat's vicinity assumes - 0.9 stability, so 0.1 - even where the water
     * reads dead calm. Turned into a level by dividing by CEIL directly rather than through
     * {@code levelFor}: the FLOOR would eat 0.1 whole, and the boat must always show.
     */
    public static final float COHERENCE_FISHERMAN_ABERRATION = 0.1f;

    /** Multiplied by pond radius: how far past the water's edge an open pond still turns the
     *  screen over. Measured from the surface, so anywhere on the pond is full strength. */
    public static final float COHERENCE_POND_RANGE_MULT = 4f;

    /**
     * Track's hyperspace backing and its warp grid. The grid border doesn't move, so the swim
     * stays inside the bar. CELLS is vertices per side; the radii are screen pixels (small - the
     * bar is 52 wide).
     */
    public static final float MINIGAME_TRACK_BG_ALPHA = 0.7f;
    public static final float MINIGAME_TRACK_BG_ZOOM = 1f;
    public static final int MINIGAME_TRACK_BG_WARP_CELLS = 6;
    public static final float MINIGAME_TRACK_BG_WARP_MIN = 1.5f;
    public static final float MINIGAME_TRACK_BG_WARP_MAX = 5f;
    public static final float MINIGAME_TRACK_BG_WARP_RATE = 1f;

    /** Backing fades to dark going down the bar, top and bottom; drawn under gameplay so the
     *  fish and bar stay readable across the whole track. */
    public static final float MINIGAME_TRACK_FADE_TOP = 0.05f;
    public static final float MINIGAME_TRACK_FADE_BOTTOM = 0.95f;

    /**
     * Border dressing on both bars, vanilla-panel style: a bright line just off the bar
     * (INSET), a dimmer one further out (SPACING). Radius clamps to what fits, so the 10-wide
     * meter rounds tighter than the track.
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
