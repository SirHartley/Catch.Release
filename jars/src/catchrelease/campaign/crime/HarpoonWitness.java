package catchrelease.campaign.crime;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.util.Misc;

/**
 * A holed crew going to find somebody to tell.
 * <p>
 * They do not radio it in. They break off whatever they were doing and fly to the nearest patrol
 * that is not at war with them, and the report lands when they get there - which makes reporting
 * something the player can watch happen, and outrun. Shoot the messenger, jump out, or simply be
 * gone by the time they arrive, and nobody was ever told.
 * <p>
 * Whose patrol it is does not matter, only that they are in earshot and not hostile to the crew
 * doing the telling. A hauler with a hole in it is not going to hold out for a patrol flying its own
 * flag - it is about the space this happened in, not about anybody's allegiance.
 * <p>
 * If there is nobody to tell, and the player left their transponder on long enough to be named, the
 * crew sometimes spends the money instead - see {@link HarpoonHitman}.
 */
public class HarpoonWitness implements EveryFrameScript {

    /** Set on a crew already on its way to report, so a second hit does not start a second errand. */
    public static final String REPORTING_FLAG = "$catchrelease_harpoonReporting";

    /** How close counts as having got there and said it. */
    public static final float REPORT_DISTANCE = 400f;

    /** Days they will spend trying to reach somebody before giving it up as a bad job. */
    public static final float TRAVEL_DAYS = 12f;

    protected CampaignFleetAPI victim;
    protected CampaignFleetAPI patrol;
    protected String factionId;
    protected boolean identified;

    protected float daysSpent = 0f;
    protected boolean done = false;

    /**
     * Sets a crew off to report a harpooning, or settles what happens when they cannot.
     *
     * @param identified whether the player was running a transponder, and so has a name on them
     * @param explosive  whether the hull was hit with a charge rather than a barb
     */
    public static void begin(CampaignFleetAPI victim, String factionId,
                             boolean identified, boolean explosive) {

        if (victim == null || factionId == null) return;

        //a charge in the hull with the player's own flag flying is not something anybody reports and
        //waits on. There is no patrol errand here - the money goes out the same day
        if (explosive && identified) {
            HarpoonHitman.send(factionId, true);
            return;
        }

        if (victim.getMemoryWithoutUpdate().getBoolean(REPORTING_FLAG)) return;

        CampaignFleetAPI patrol = HarpoonPatrolResponse.findNearbyPatrol(victim);

        //nobody within reach to tell. Some of them let it go and some of them pay somebody
        if (patrol == null) {
            if (identified && Math.random() < HarpoonHitman.CHANCE) HarpoonHitman.send(factionId);
            return;
        }

        victim.getMemoryWithoutUpdate().set(REPORTING_FLAG, true, TRAVEL_DAYS);

        victim.clearAssignments();
        victim.addAssignment(FleetAssignment.GO_TO_LOCATION, patrol, TRAVEL_DAYS,
                "reporting an incident");

        Global.getSector().addScript(
                new HarpoonWitness(victim, patrol, factionId, identified));
    }

    public HarpoonWitness(CampaignFleetAPI victim, CampaignFleetAPI patrol, String factionId,
                          boolean identified) {

        this.victim = victim;
        this.patrol = patrol;
        this.factionId = factionId;
        this.identified = identified;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        //the messenger did not make it, or the patrol they were flying at is gone. Either way the
        //report dies with them - which is the whole reason this is a journey rather than a message
        if (!isAlive(victim) || !isAlive(patrol)) {
            giveUp();
            return;
        }

        if (victim.getContainingLocation() != patrol.getContainingLocation()) {
            giveUp();
            return;
        }

        //hit again on the way, and the crew has stopped being a messenger - they are either coming
        //for the bill or running. Their new orders stand; this one lets go without touching them
        if (HarpoonOffence.isDemanding(victim) || HarpoonOffence.isFleeing(victim)) {
            abandon();
            return;
        }

        if (Misc.getDistance(victim.getLocation(), patrol.getLocation()) <= REPORT_DISTANCE) {
            report();
            return;
        }

        daysSpent += Global.getSector().getClock().convertToDays(amount);
        if (daysSpent > TRAVEL_DAYS) giveUp();
    }

    protected static boolean isAlive(CampaignFleetAPI fleet) {
        return fleet != null && !fleet.isExpired() && fleet.isAlive();
    }

    /** Told. The patrol takes it from here, and the crew goes back to whatever it was doing. */
    protected void report() {
        HarpoonPatrolResponse.clearRetryWait(factionId);
        HarpoonPatrolResponse.dispatch(patrol, factionId);

        release();
    }

    /**
     * Nobody was told. The errand is dropped, and a crew that got a look at who did it may still
     * decide the matter is worth paying to settle.
     */
    protected void giveUp() {
        if (identified && Math.random() < HarpoonHitman.CHANCE) HarpoonHitman.send(factionId);

        release();
    }

    /** Ends the errand and puts the crew back on a course of its own. */
    protected void release() {
        abandon();

        if (!isAlive(victim)) return;

        victim.clearAssignments();
        Misc.giveStandardReturnToSourceAssignments(victim);
    }

    /** Ends the errand and leaves the crew's orders alone, for when something else now owns them. */
    protected void abandon() {
        done = true;

        if (isAlive(victim)) victim.getMemoryWithoutUpdate().unset(REPORTING_FLAG);
    }
}
