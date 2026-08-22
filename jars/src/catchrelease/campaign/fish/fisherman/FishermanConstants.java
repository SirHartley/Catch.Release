package catchrelease.campaign.fish.fisherman;

import java.awt.Color;

public class FishermanConstants {
    public static final String FLEET_NAME = "The Fisherman";
    public static final String FACTION = "independent";
    public static final String FLEET_FLAG = "$catchrelease_fisherman";
    public static final String VISITING_FLAG = "$catchrelease_fisherman_visiting";
    public static final String TUTORIAL_TARGET_KEY = "$catchrelease_fisherman_tutorialTarget";
    public static final String TUTORIAL_TEMPORARY_KEY = "$catchrelease_fisherman_tutorialTemporary";
    public static final String RETIRE_KEY = "$catchrelease_fisherman_retire";
    public static final String[] CORE_SHIPS = {
            "buffalo_Standard",
            "shepherd_Frontier",
    };
    public static final float CORE_CHECK_DAYS = 7f;
    public static final String ACTIVE_KEY = "$catchrelease_fisherman_fleet";
    public static final String LAST_SEEN_KEY = "$catchrelease_fisherman_last";
    public static final String[] SHIPS = {
            "venture_Exploration",
            "buffalo_Standard",
            "phaeton_Standard",
            "shepherd_Frontier",
    };
    public static final Color LIGHT_COLOR = new Color(255, 180, 50, 255);
    public static final int LIGHTS = 3;
    public static final float SWEEP_DEGREES_PER_SECOND = 30f;
    public static final String SOUND_TOGGLE = "catchrelease_ui_searchlight_toggle";
    public static final float STAY_DAYS = 14f;
    public static final float WIND_DOWN_SECONDS = 2f;
    public static final float MOTE_INTERVAL_MIN = 7f;
    public static final float MOTE_INTERVAL_MAX = 16f;
    public static final float SPAWN_BASE_CHANCE = 0.06f;
    public static final int CARGO_FISH_THRESHOLD = 15;
    public static final float CARGO_FULL_MULT = 4f;
    public static final float OVERDUE_DAYS = 60f;
    public static final float OVERDUE_MULT = 6f;
    public static final float CORE_SPAWN_MULT = 0.3f;
    public static final float SPAWN_LOCK_DAYS = 30f;
    public static final String SPAWN_LOCK_KEY = "$catchrelease_fisherRolled";
    public static final String BURN_ID = "catchrelease_fisherman_burn";
    public static final float BURN_WORKING = 4f;
    public static final float BURN_CHASING = 16f;
    public static final float SPAWN_DISTANCE_MIN = 4000f;
    public static final float SPAWN_DISTANCE_SPREAD = 3000f;
    public static final int SURVEY_COST = 2;
    public static final String SURVEY_STOCK_KEY = "$catchrelease_fisherman_survey";
    public static final int SURVEY_SLOTS_BASE = 2;
    public static final String SURVEY_SLOTS_KEY = "$catchrelease_fisher_slots";
    public static final String SHARED_SHELF_FLAG = "$catchrelease_fisher_shared";
    public static final String SHARED_STOCK_KEY = "$catchrelease_fisher_shared_stock";
    public static final String SHARED_PENDING_KEY = "$catchrelease_fisher_shared_pending";
    public static final float SHARED_REGEN_DAYS = 30f;
    public static final String LISTED_KEY = "$catchrelease_fisher_listed";
    public static final float[] SURVEY_RARITY_WEIGHTS = {10f, 6f, 3f, 1.5f, 0.75f};
    public static final float STRANGER_QUALITY_FLOOR = 0.85f;
    public static final float STRANGER_MAX_ABERRATION = 0.15f;
    public static final float RUMOR_COOLDOWN_DAYS = 30f;
    public static final float RUMOR_DURATION_DAYS = 30f;
    public static final float RUMOR_RARITY_BIAS = 1.5f;
    public static final float RUMOR_LOOT_MULT = 2.5f;
    public static final float RUMOR_STRANGER_WEIGHT = 8f;
    public static final float DIALOG_DIM = 0.9f;
    public static final String VISIBILITY_ID = "catchrelease_fisherman";
    public static final float DETECTED_RANGE = 100000f;
}
