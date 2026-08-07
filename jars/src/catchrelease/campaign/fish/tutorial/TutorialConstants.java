package catchrelease.campaign.fish.tutorial;

public class TutorialConstants {

    /** Where the introduction's stage lives. */
    public static final String STAGE_KEY = "$catchrelease_intro_stage";

    /**
     * The rig, all of it, handed over in one go.
     * <p>
     * There is no ladder here on purpose. The introduction is detached from the ordinary loop - it
     * is how somebody stops being a person who has never heard of this and starts being one who
     * has - and metering the gear out across it would turn a scene into a checklist.
     */
    public static final String[] GEAR = {
            "catchrelease_searchlights",
            "catchrelease_rod",
            "catchrelease_harpoon",
            "catchrelease_shop",
    };

    //---------------------------------------------------------------- the wreck

    /** Somebody's cruiser, with a line still in it. */
    public static final String WRECK_ENTITY_ID = "catchrelease_TutorialWreck";
    public static final String WRECK_PLACED_KEY = "$catchrelease_wreckPlaced";
    public static final String WRECK_NAME = "Derelict Cruiser";

    /** Whether the player pulled the head out and is carrying it back. */
    public static final String CARRYING_KEY = "$catchrelease_carryingHarpoon";

    /** Cruiser hulls the wreck can turn out to be. Random, because it is nobody in particular. */
    public static final String[] WRECK_HULLS = {
            "eagle_Balanced",
            "falcon_Attack",
            "dominator_Assault",
            "venture_Exploration",
            "apogee_Balanced",
    };

    /** How near the rupture the hulk sits, and how far off a pond counts as "in view". */
    public static final float WRECK_ORBIT_RADIUS = 700f;
    public static final float WRECK_ORBIT_DAYS = 180f;
    public static final float WRECK_SPOT_RANGE = 4000f;

    //---------------------------------------------------------------- the castaway

    /** The one exiled crewman, and where the survey put him. */
    public static final String CASTAWAY_ENTITY_ID = "catchrelease_Castaway";
    public static final String CASTAWAY_PLACED_KEY = "$catchrelease_castawayPlaced";
    public static final String CASTAWAY_NAME = "Distress Beacon";

    public static final float CASTAWAY_SURFACE_PAD = 30f;
    public static final float CASTAWAY_ORBIT_DAYS = 90f;

    //---------------------------------------------------------------- the interception

    /**
     * How the boat arrives when it decides to head somebody off: outside the viewport, and gone
     * again if the player leaves.
     */
    public static final float INTERCEPT_SPAWN_DISTANCE = 2600f;
    public static final float INTERCEPT_TRIGGER_RANGE = 1400f;
    public static final float INTERCEPT_CHECK_SECONDS = 0.5f;
}
