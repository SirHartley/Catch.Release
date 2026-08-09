package catchrelease.campaign.fish.tutorial;

public class TutorialConstants {

    /** Where the introduction's stage and its current target live. */
    public static final String STAGE_KEY = "$catchrelease_intro_stage";
    public static final String TARGET_KEY = "$catchrelease_intro_target";

    /**
     * The one ability the first lesson hands over.
     * <p>
     * ROD and LINE are the same ability and always were - it reads the situation and does the right
     * thing: opens a rupture near unstable terrain, casts drones in target mode over an open pond,
     * and casts them in search mode while the lamps are lit. The second lesson explains the drones;
     * it does not grant anything, because there is nothing left to grant.
     */
    public static final String ROD = "catchrelease_rod";

    /** What the fourth lesson hands over, once there is something worth pointing at the dark. */
    public static final String[] DEEP_GEAR = {
            "catchrelease_searchlights",
            "catchrelease_harpoon",
    };

    /** The ledger. Introduced after the second catch, before the deep rigs change hands. */
    public static final String OUTFITTER = "catchrelease_shop";

    /** Free charts handed out along the way: two to learn on, then the graduation package. */
    public static final int FREE_COMMONS = 2;
    public static final int[] GRADUATION_CHARTS = {2, 1, 1};

    /** Where the "I have done this before" flag lives, outside any one save. */
    public static final String SEEN_FILE = "catchrelease_seen.txt";

    //---------------------------------------------------------------- the wreck

    public static final String WRECK_ENTITY_ID = "catchrelease_TutorialWreck";
    public static final String WRECK_PLACED_KEY = "$catchrelease_wreckPlaced";
    public static final String WRECK_NAME = "Derelict Cruiser";
    public static final String WRECK_TAG = "catchrelease_tutorial_wreck";

    /** Whether the player recovered the Fisherman's LYNE service assembly from the wreck. */
    public static final String FISHER_PROPERTY_KEY = "$catchrelease_fisherProperty";

    /** Old saves used the same breadcrumb for a recovered harpoon head. Read once as an alias. */
    public static final String LEGACY_CARRYING_HARPOON_KEY = "$catchrelease_carryingHarpoon";

    /** The second catch is in, but the interrupted deep-gear handoff has not finished yet. */
    public static final String DEEP_HANDOFF_KEY = "$catchrelease_deepHandoff";

    /** Cruiser hulls the wreck can turn out to be. Random, because it is nobody in particular. */
    public static final String[] WRECK_HULLS = {
            "eagle_Balanced",
            "falcon_Attack",
            "dominator_Assault",
            "venture_Exploration",
            "apogee_Balanced",
    };

    public static final float WRECK_ORBIT_RADIUS = 700f;
    public static final float WRECK_ORBIT_DAYS = 180f;
    public static final float WRECK_SPOT_RANGE = 4000f;

    //---------------------------------------------------------------- the rating

    /** The same person, found two ways: marooned on a surveyed rock, or nursing a drink in a bar. */
    public static final String CASTAWAY_ENTITY_ID = "catchrelease_Castaway";
    public static final String CASTAWAY_PLACED_KEY = "$catchrelease_castawayPlaced";
    public static final String CASTAWAY_NAME = "Distress Beacon";
    public static final String CASTAWAY_TAG = "catchrelease_castaway";

    public static final float CASTAWAY_SURFACE_PAD = 30f;
    public static final float CASTAWAY_ORBIT_DAYS = 90f;

    /** How many markets the player has walked into, before the bar version turns up. */
    public static final String MARKETS_SEEN_KEY = "$catchrelease_marketsSeen";
    public static final int MARKETS_BEFORE_APPROACH = 2;

    //---------------------------------------------------------------- the interception

    public static final float INTERCEPT_SPAWN_DISTANCE = 2600f;
    public static final float INTERCEPT_TRIGGER_RANGE = 1400f;
    public static final float INTERCEPT_CHECK_SECONDS = 0.5f;

    //---------------------------------------------------------------- the errands

    /** How far off the second errand's system may be, in light-years, and how thin its water. */
    public static final float SECOND_MIN_LY = 2f;
    public static final float SECOND_MAX_LY = 10f;
    public static final float SECOND_MIN_DRIFT = 0.3f;

    /** How often the keeper looks for a target that needs putting back. */
    public static final float KEEP_CHECK_SECONDS = 2f;
    public static final float SPOT_SPREAD = 400f;
}
