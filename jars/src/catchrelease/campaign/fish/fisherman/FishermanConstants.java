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

    /** Rumors: one a month, and what the whispered-about system is better at, for how long. */
    public static final float RUMOR_COOLDOWN_DAYS = 30f;
    public static final float RUMOR_DURATION_DAYS = 30f;
    public static final float RUMOR_RARITY_BIAS = 1.5f;
    public static final float RUMOR_LOOT_MULT = 2.5f;
    public static final float RUMOR_STRANGER_WEIGHT = 8f;
}
