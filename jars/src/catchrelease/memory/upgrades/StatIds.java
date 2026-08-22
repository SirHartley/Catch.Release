package catchrelease.memory.upgrades;

public class StatIds {
    public static final String
            SEARCHLIGHT_AREA = "searchlight_area",
            SEARCHLIGHT_SPEED = "searchlight_speed",
            SEARCHLIGHT_COUNT = "searchlight_count",
            SEARCHLIGHT_RARE_CHANCE = "searchlight_rare_chance",
            SEARCHLIGHT_TRACK_TIME = "searchlight_track_time",
            SEARCHLIGHT_DETECT_RADIUS = "searchlight_detect_radius",
            SEARCHLIGHT_SLOW = "searchlight_slow";
    public static final String
            FISHING_DRONE_COUNT = "fishing_drone_count",
            DRONE_SPEED = "drone_speed",
            DRONE_ACCELERATION = "drone_acceleration",
            DRONE_CATCH_AREA = "drone_catch_area",
            DRONE_CHASE_TIME = "drone_chase_time",
            DRONE_CHASE_MARGIN = "drone_chase_margin",
            DRONE_RARE_PRIORITY = "drone_rare_priority";
    public static final String
            HARPOON_CHARGES = "harpoon_charges",
            HARPOON_RECHARGE_TIME = "harpoon_recharge_time",
            HARPOON_SPEED = "harpoon_speed",
            HARPOON_AIM_ASSIST = "harpoon_aim_assist";
    public static final String
            FISHING_BAR_SIZE = "fishing_bar_size",
            MINIGAME_PROGRESS_RATE = "minigame_progress_rate",
            MINIGAME_ESCAPE_RESIST = "minigame_escape_resist";
    public static final String
            LAMPS_ABILITY = "catchrelease_searchlights",
            ROD_ABILITY = "catchrelease_rod",
            HARPOON_ABILITY = "catchrelease_harpoon";
    protected static final String[] LAMPS = {
            SEARCHLIGHT_AREA, SEARCHLIGHT_SPEED, SEARCHLIGHT_COUNT, SEARCHLIGHT_RARE_CHANCE,
            SEARCHLIGHT_TRACK_TIME, SEARCHLIGHT_DETECT_RADIUS,
            SEARCHLIGHT_SLOW,
    };
    protected static final String[] ROD = {
            FISHING_DRONE_COUNT, DRONE_SPEED, DRONE_ACCELERATION, DRONE_CATCH_AREA,
            DRONE_CHASE_TIME, DRONE_CHASE_MARGIN, DRONE_RARE_PRIORITY,
    };
    protected static final String[] HARPOON = {
            HARPOON_CHARGES, HARPOON_RECHARGE_TIME, HARPOON_SPEED, HARPOON_AIM_ASSIST,
    };

    public static String getAbilityId(String statId) {
        if (statId == null) return null;

        for (String id : LAMPS) if (id.equals(statId)) return LAMPS_ABILITY;
        for (String id : ROD) if (id.equals(statId)) return ROD_ABILITY;
        for (String id : HARPOON) if (id.equals(statId)) return HARPOON_ABILITY;

        return null;
    }
}
