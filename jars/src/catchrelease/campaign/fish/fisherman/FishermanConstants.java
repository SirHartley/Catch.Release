package catchrelease.campaign.fish.fisherman;

import java.awt.Color;

public class FishermanConstants {

    public static final String FLEET_NAME = "The Fisherman";
    public static final String FACTION = "independent";

    /** The flag on the fleet that routes interaction to the Fisherman's own dialog. */
    public static final String FLEET_FLAG = "$catchrelease_fisherman";

    /** Where the live fleet is parked in sector memory, so only one ever exists at a time. */
    public static final String ACTIVE_KEY = "$catchrelease_fisherman_fleet";

    /** When one last left, for the has-not-come-by-in-a-while pressure. */
    public static final String LAST_SEEN_KEY = "$catchrelease_fisherman_last";

    /** One cruiser and a few logistics hulls - a working boat, not a warfleet. */
    public static final String[] SHIPS = {
            "venture_Exploration",
            "buffalo_Standard",
            "phaeton_Standard",
            "shepherd_Frontier",
    };

    /** The lamps: the old yellow light, thrown as fans - no upgrades, no breach. */
    public static final Color LIGHT_COLOR = new Color(255, 180, 50, 255);
    public static final int LIGHTS = 2;
    public static final float SWEEP_DEGREES_PER_SECOND = 30f;

    /** The one sound either end of the visit makes. */
    public static final String SOUND_TOGGLE = "catchrelease_ui_searchlight_toggle";

    /** Days the fleet fishes before the lights go out and it leaves. */
    public static final float STAY_DAYS = 14f;
    public static final float WIND_DOWN_SECONDS = 2f;

    /** How the light's catch is staged: motes seeded under the fans, then harpooned. */
    public static final float MOTE_INTERVAL_MIN = 7f;
    public static final float MOTE_INTERVAL_MAX = 16f;
    public static final float HARPOON_INTERVAL_MIN = 9f;
    public static final float HARPOON_INTERVAL_MAX = 22f;

    /** The least lit a mote has to be before the Fisherman bothers throwing at it. */
    public static final float HARPOON_MIN_LIT = 0.15f;

    /** Spawn pressure: a small daily roll, leaned on hard by a full hold or a long absence. */
    public static final float SPAWN_CHECK_DAYS = 1f;
    public static final float SPAWN_BASE_CHANCE = 0.06f;
    public static final int CARGO_FISH_THRESHOLD = 15;
    public static final float CARGO_FULL_MULT = 4f;
    public static final float OVERDUE_DAYS = 60f;
    public static final float OVERDUE_MULT = 6f;

    /** Core systems still get visits, just fewer - the frontier is where the fishing is. */
    public static final float CORE_SPAWN_MULT = 0.3f;

    /** Arrival: past the longest sensor range in the game, plus a little scatter on top. */
    public static final float SPAWN_DISTANCE_MIN = 4000f;
    public static final float SPAWN_DISTANCE_SPREAD = 3000f;

    /** Survey data costs fish one rung below the species' own rarity; commons cost a common. */
    public static final int SURVEY_COST = 2;

    /** The survey shelf: rolled once per visit, sold down, never restocked until the next boat. */
    public static final String SURVEY_STOCK_KEY = "$catchrelease_fisherman_survey";
    public static final int SURVEY_STOCK = 6;

    /** Roll weight by rarity ordinal: commons likely, legendaries a long shot - and as the
     *  commons become known they leave the pool, so a seasoned fisher is offered rarer charts. */
    public static final float[] SURVEY_RARITY_WEIGHTS = {10f, 6f, 3f, 1.5f, 0.75f};

    /** A rumored stranger is a prize specimen: quality floor and stability cap on its roll,
     *  over whatever the water and tackle would have said. */
    public static final float STRANGER_QUALITY_FLOOR = 0.85f;
    public static final float STRANGER_MAX_ABERRATION = 0.15f;

    /** Rumors: one a month, and what the whispered-about system is better at, for how long. */
    public static final float RUMOR_COOLDOWN_DAYS = 30f;
    public static final float RUMOR_DURATION_DAYS = 30f;
    public static final float RUMOR_RARITY_BIAS = 1.5f;
    public static final float RUMOR_LOOT_MULT = 2.5f;
    public static final float RUMOR_STRANGER_WEIGHT = 8f;

    /**
     * How dark the map behind the conversation is once the outfitter has been closed out of.
     * <p>
     * Restored rather than remembered: the shop dims on the way in and {@code InteractionDialogAPI}
     * has no getter for what it was. This is the figure vanilla uses for its own comm screens.
     */
    public static final float DIALOG_DIM = 0.9f;

    /**
     * How far off the boat can be seen, and the id the modifier is held under.
     * <p>
     * Far enough to cover any system. The lamps are drawn wherever the boat is, whether or not
     * anybody can make out the hull carrying them - so a Fisherman at the edge of sensor range was
     * two searchlights sweeping the dark under their own power. Being visible is not decoration
     * here, it is what stops the rig looking unattached.
     */
    public static final String VISIBILITY_ID = "catchrelease_fisherman";
    public static final float DETECTED_RANGE = 100000f;
}
