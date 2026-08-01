package catchrelease.campaign.ponds.constants;

public class PondConstants {

    //spawning
    public static final float MIN_EMPTY_RADIUS_AROUND_POND = 1000;
    public static final float MIN_DISTANCE = 10000f;
    public static final float DIST_PER_FITTING_ATTEMPT = 500f; //technical
    public static final int MIN_POND_AMT_PER_SYSTEM = 1;
    public static final int PLANETS_PER_ADDITIONAL_POND = 4;

    //interaction - multiplied by the pond radius. The fleet is "at" the pond within this, which is
    //both where the rod ability can be used and where the camera holds onto the pond
    public static final float POND_INTERACT_RANGE_MULT = 1.5f;

    //camera
    /** Seconds for the focus to close most of the distance - higher is softer and slower. */
    public static final float POND_FOCUS_TIME_CONSTANT = 0.75f;

    /** World units. Once the camera is this close to the fleet again, control goes back to the game. */
    public static final float POND_FOCUS_HANDBACK_DISTANCE = 5f;

}
