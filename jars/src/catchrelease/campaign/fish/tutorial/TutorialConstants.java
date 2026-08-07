package catchrelease.campaign.fish.tutorial;

public class TutorialConstants {

    /** Where the intro's stage lives, and the person who runs it. */
    public static final String STAGE_KEY = "$catchrelease_intro_stage";
    public static final String BAHA_KEY = "$catchrelease_baha";

    /** How many markets the player has walked into, for the hand on the shoulder. */
    public static final String MARKETS_SEEN_KEY = "$catchrelease_marketsSeen";
    public static final int MARKETS_BEFORE_APPROACH = 2;

    /**
     * Baha, who never changes either. No rank and no post: an id the game does not know would take
     * the encounter screen down with it, and the title is in what they say anyway.
     */
    public static final String BAHA_FIRST = "Baha";
    public static final String BAHA_LAST = "";
    public static final String BAHA_PORTRAIT = "graphics/portraits/portrait36.png";

    /**
     * The rig handed over at the introduction, and the one held back.
     * <p>
     * The three are the whole loop - a light to find something by, a rod for a pond, a line to
     * throw. The outfitter is the reward for the first catch, because it is the one piece that
     * does nothing at all until there is something in the hold to spend.
     */
    public static final String[] STARTING_GEAR = {
            "catchrelease_searchlights",
            "catchrelease_rod",
            "catchrelease_harpoon",
    };
    public static final String OUTFITTER = "catchrelease_shop";

    /** What the first task asks for: one specimen, of anything. */
    public static final int FIRST_CATCH_COUNT = 1;

    //---------------------------------------------------------------- the lost harpoon

    public static final String HARPOON_ENTITY_ID = "catchrelease_LostHarpoon";
    public static final String HARPOON_PLACED_KEY = "$catchrelease_lostHarpoon";
    public static final String HARPOON_NAME = "Transponder Signal";

    /** How far out from the core the wreck is looked for, in light-years. */
    public static final float HARPOON_MIN_LY = 4f;
    public static final float HARPOON_MAX_LY = 14f;

    /** It sits on the limb rather than in orbit - close enough to read as stuck there. */
    public static final float HARPOON_SURFACE_PAD = 30f;
    public static final float HARPOON_ORBIT_DAYS = 90f;

    /** How far to one side of the world the rupture sits - over it, nobody could see it. */
    public static final float HARPOON_POND_OFFSET = 900f;

    /** Its own detectability, so the signal is what finds it rather than a survey sweep. */
    public static final float HARPOON_SENSOR_PROFILE = 400f;
}
