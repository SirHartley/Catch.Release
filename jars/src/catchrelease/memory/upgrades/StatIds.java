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

    //breach lamps. The ids still say searchlight because ids live in saves; the display name is
    //translated in the shop
    public static final String
            SEARCHLIGHT_AREA = "searchlight_area",
            SEARCHLIGHT_SPEED = "searchlight_speed",
            SEARCHLIGHT_COUNT = "searchlight_count",
            SEARCHLIGHT_RARE_CHANCE = "searchlight_rare_chance",
            SEARCHLIGHT_TRACK_TIME = "searchlight_track_time",
            SEARCHLIGHT_IDENTIFY = "searchlight_identify",
            SEARCHLIGHT_DETECT_RADIUS = "searchlight_detect_radius",
            SEARCHLIGHT_SLOW = "searchlight_slow";

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
            HARPOON_AIM_ASSIST = "harpoon_aim_assist";

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

    /** The abilities these numbers belong to, named where the shop can reach them. */
    public static final String
            LAMPS_ABILITY = "catchrelease_searchlights",
            ROD_ABILITY = "catchrelease_rod",
            HARPOON_ABILITY = "catchrelease_harpoon",
            BOMB_ABILITY = "catchrelease_depthbomb";

    /**
     * Which ability a stat is a number on, or null for one that is not about an ability at all.
     * <p>
     * Listed rather than matched on the id's prefix. The prefixes happen to be consistent today and
     * a convention held together by nothing is a convention somebody breaks - {@code fishing_bar_size}
     * already sits with the minigame despite reading like a drone stat, which is what that failure
     * looks like when it happens.
     * <p>
     * The point of asking is that a running ability was built from these numbers when it started, so
     * changing one under it leaves it running on the old figure until it is turned off and on again.
     */
    public static String getAbilityId(String statId) {
        if (statId == null) return null;

        for (String id : LAMPS) if (id.equals(statId)) return LAMPS_ABILITY;
        for (String id : ROD) if (id.equals(statId)) return ROD_ABILITY;
        for (String id : HARPOON) if (id.equals(statId)) return HARPOON_ABILITY;
        for (String id : BOMB) if (id.equals(statId)) return BOMB_ABILITY;

        //the minigame stats belong to no ability - nothing is running to be interrupted
        return null;
    }

    protected static final String[] LAMPS = {
            SEARCHLIGHT_AREA, SEARCHLIGHT_SPEED, SEARCHLIGHT_COUNT, SEARCHLIGHT_RARE_CHANCE,
            SEARCHLIGHT_TRACK_TIME, SEARCHLIGHT_IDENTIFY, SEARCHLIGHT_DETECT_RADIUS,
            SEARCHLIGHT_SLOW,
    };

    protected static final String[] ROD = {
            FISHING_DRONE_COUNT, DRONE_SPEED, DRONE_ACCELERATION, DRONE_CATCH_AREA,
            DRONE_CHASE_TIME, DRONE_CHASE_MARGIN, DRONE_RARE_PRIORITY,
    };

    protected static final String[] HARPOON = {
            HARPOON_CHARGES, HARPOON_RECHARGE_TIME, HARPOON_SPEED, HARPOON_AIM_ASSIST,
    };

    protected static final String[] BOMB = {
            BOMB_CHARGES, BOMB_RECHARGE_TIME, BOMB_BLAST_RADIUS, BOMB_SPEED, BOMB_SLOW,
            BOMB_STUN, BOMB_RUPTURE_TIME,
    };
}
