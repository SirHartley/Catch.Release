package catchrelease.campaign.fish.fisherman;

import java.awt.Color;

public class FishermanConstants {

    public static final String FLEET_NAME = "The Fisherman";
    public static final String FACTION = "independent";

    /** The flag on any fishing boat, which is what routes interaction to the trade's own dialog. */
    public static final String FLEET_FLAG = "$catchrelease_fisherman";

    /**
     * The flag on the boat that is only passing through.
     * <p>
     * Purely about the boat's schedule. It is the same man on every one of them - see
     * {@link FishermanIdentity} - so nothing about who the player is talking to hangs off this.
     */
    public static final String VISITING_FLAG = "$catchrelease_fisherman_visiting";

    /** The core's standing boats: one to a populated system, working the outer reaches. */
    public static final String[] CORE_SHIPS = {
            "buffalo_Standard",
            "shepherd_Frontier",
    };

    /** How often the core is swept for a system that has lost its boat. */
    public static final float CORE_CHECK_DAYS = 7f;

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
    public static final int LIGHTS = 3;
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

    /**
     * Spawn pressure: the odds are the old ones, asked once when the player arrives somewhere.
     * <p>
     * A small base leaned on hard by a full hold and by a long absence, which is a good curve - it
     * was the *frequency* that was wrong. Rolled per arrival rather than per day, and the system is
     * then locked for a month, so jumping out and back is not a re-roll and a rim run is not a boat
     * behind every jump.
     */
    public static final float SPAWN_BASE_CHANCE = 0.06f;
    public static final int CARGO_FISH_THRESHOLD = 15;
    public static final float CARGO_FULL_MULT = 4f;
    public static final float OVERDUE_DAYS = 60f;
    public static final float OVERDUE_MULT = 6f;

    /** Core systems still get visits, just fewer - the frontier is where the fishing is. */
    public static final float CORE_SPAWN_MULT = 0.3f;

    /** How long a system stays answered for once it has been rolled. */
    public static final float SPAWN_LOCK_DAYS = 30f;

    /** Set on the system itself, with the month as its own expiry. */
    public static final String SPAWN_LOCK_KEY = "$catchrelease_fisherRolled";

    /** The boat works at a trawler's pace, and only breaks it to head somebody off. */
    public static final String BURN_ID = "catchrelease_fisherman_burn";
    public static final float BURN_WORKING = 4f;
    public static final float BURN_CHASING = 16f;


    /** Arrival: past the longest sensor range in the game, plus a little scatter on top. */
    public static final float SPAWN_DISTANCE_MIN = 4000f;
    public static final float SPAWN_DISTANCE_SPREAD = 3000f;

    /** Range data costs fish one rung below the species' own rarity; commons cost a common. */
    public static final int SURVEY_COST = 2;

    /** A visiting boat's shelf: rolled once per visit, sold down, gone when it leaves. */
    public static final String SURVEY_STOCK_KEY = "$catchrelease_fisherman_survey";

    /**
     * How many charts are out at once to begin with, and where the extra slots earned by doing
     * work for the trade are counted.
     * <p>
     * Two is deliberately thin. A shelf that always has six things on it is a shop; a shelf with
     * two is a supply, and widening it is the reason to take a job.
     */
    public static final int SURVEY_SLOTS_BASE = 2;
    public static final String SURVEY_SLOTS_KEY = "$catchrelease_fisher_slots";

    /** Set on a boat that sells off the core's one shelf rather than a shelf of its own. */
    public static final String SHARED_SHELF_FLAG = "$catchrelease_fisher_shared";

    /** The core's shelf, and the sales it still owes a replacement for. */
    public static final String SHARED_STOCK_KEY = "$catchrelease_fisher_shared_stock";
    public static final String SHARED_PENDING_KEY = "$catchrelease_fisher_shared_pending";

    /**
     * How long after a chart is <b>sold</b> the chart crews have another ready.
     * <p>
     * Dated off the sale rather than off a calendar. A shelf on a monthly tick pays out to
     * whoever happens to ask after the tick, which rewards standing still; dating it off the
     * purchase means the wait is the same wait for everybody and starts when the player caused it.
     */
    public static final float SHARED_REGEN_DAYS = 30f;

    /** Every chart on sale anywhere, so no two boats offer the same one. */
    public static final String LISTED_KEY = "$catchrelease_fisher_listed";

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
