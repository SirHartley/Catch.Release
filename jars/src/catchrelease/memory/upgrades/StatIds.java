package catchrelease.memory.upgrades;

/**
 * Every upgradeable number, by id.
 * <p>
 * The ids are the join between the code and data/config/UpgradeData.csv - a stat that is named here
 * and not there simply has no upgrade, and {@link UpgradeManager#getValue(String, float)} hands back
 * the fallback the caller passed. That is deliberate: a missing row should cost the upgrade, not the
 * feature.
 * <p>
 * Which category a stat is in lives in the sheet rather than here, since it is a property of the
 * upgrade rather than of the thing being upgraded.
 */
public class StatIds {

    //searchlights
    public static final String
            SEARCHLIGHT_AREA = "searchlight_area",
            SEARCHLIGHT_SPEED = "searchlight_speed",
            SEARCHLIGHT_COUNT = "searchlight_count",
            SEARCHLIGHT_RARE_CHANCE = "searchlight_rare_chance",
            SEARCHLIGHT_TRACK_TIME = "searchlight_track_time",
            SEARCHLIGHT_IDENTIFY = "searchlight_identify";

    //drones
    public static final String
            FISHING_DRONE_COUNT = "fishing_drone_count",
            DRONE_SPEED = "drone_speed",
            DRONE_ACCELERATION = "drone_acceleration",
            DRONE_CATCH_AREA = "drone_catch_area",
            DRONE_CHASE_TIME = "drone_chase_time",
            DRONE_CHASE_MARGIN = "drone_chase_margin",
            DRONE_RARE_PRIORITY = "drone_rare_priority";

    //harpoon
    public static final String
            HARPOON_CHARGES = "harpoon_charges",
            HARPOON_RECHARGE_TIME = "harpoon_recharge_time",
            HARPOON_SPEED = "harpoon_speed",
            HARPOON_AIM_ASSIST = "harpoon_aim_assist",
            HARPOON_DEEP = "harpoon_deep";

    //depth bombs
    public static final String
            BOMB_CHARGES = "bomb_charges",
            BOMB_RECHARGE_TIME = "bomb_recharge_time",
            BOMB_BLAST_RADIUS = "bomb_blast_radius",
            BOMB_SPEED = "bomb_speed",
            BOMB_SLOW = "bomb_slow",
            BOMB_STUN = "bomb_stun",
            BOMB_RUPTURE_TIME = "bomb_rupture_time";

    //the catch itself - these are the ones that need a slot
    public static final String
            FISHING_BAR_SIZE = "fishing_bar_size",
            MINIGAME_PROGRESS_RATE = "minigame_progress_rate",
            MINIGAME_ESCAPE_RESIST = "minigame_escape_resist";
}
