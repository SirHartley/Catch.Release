package catchrelease.campaign.crime;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;

public class HarpoonWitness implements EveryFrameScript {

    public static final String REPORTING_FLAG = "$catchrelease_harpoonReporting";
    public static final float REPORT_DISTANCE = 400f;
    public static final float TRAVEL_DAYS = 12f;

    protected CampaignFleetAPI victim;
    protected CampaignFleetAPI patrol;
    protected String factionId;
    protected boolean identified;
    protected String victimName;
    protected String originName;
    protected boolean explosive;
    protected boolean hitmanEligible;
    protected float daysSpent = 0f;
    protected boolean done = false;

    public HarpoonWitness(CampaignFleetAPI victim, CampaignFleetAPI patrol, String factionId,
                          boolean identified, String victimName, String originName,
                          boolean explosive, boolean hitmanEligible) {
        this.victim = victim;
        this.patrol = patrol;
        this.factionId = factionId;
        this.identified = identified;
        this.victimName = victimName;
        this.originName = originName;
        this.explosive = explosive;
        this.hitmanEligible = hitmanEligible;
    }

    public static void begin(CampaignFleetAPI victim, String factionId,
                             boolean identified, boolean explosive) {
        if (victim == null || factionId == null) return;

        String victimName = victim.getName();
        LocationAPI origin = victim.getContainingLocation();
        String originName = origin == null ? "open space"
                : origin instanceof StarSystemAPI
                ? ((StarSystemAPI) origin).getNameWithNoType() : origin.getName();
        boolean hitmanEligible = HarpoonHitman.isEligibleVictim(victim);

        if (explosive && identified && hitmanEligible) {
            HarpoonHitman.send(factionId, victimName, originName, true, true);
            return;
        }
        if (explosive && identified) return;

        if (victim.getMemoryWithoutUpdate().getBoolean(REPORTING_FLAG)) return;

        CampaignFleetAPI patrol = HarpoonPatrolResponse.findNearbyPatrol(victim);

        if (patrol == null) {
            if (identified && hitmanEligible) {
                HarpoonHitman.send(factionId, victimName, originName, explosive, false);
            }
            return;
        }

        victim.getMemoryWithoutUpdate().set(REPORTING_FLAG, true, TRAVEL_DAYS);

        victim.clearAssignments();
        victim.addAssignment(FleetAssignment.GO_TO_LOCATION, patrol, TRAVEL_DAYS,
                "reporting an incident");

        Global.getSector().addScript(
                new HarpoonWitness(victim, patrol, factionId, identified,
                        victimName, originName, explosive, hitmanEligible));
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

        if (!isAlive(victim) || !isAlive(patrol)) {
            giveUp();
            return;
        }

        if (victim.getContainingLocation() != patrol.getContainingLocation()) {
            giveUp();
            return;
        }

        if (HarpoonOffence.isDemanding(victim) || HarpoonOffence.isFleeing(victim)
                || HarpoonOffence.hasTurnedHostile(victim)) {
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

    protected void report() {
        HarpoonPatrolResponse.clearRetryWait(factionId);
        HarpoonPatrolResponse.dispatch(patrol, factionId);

        release();
    }

    protected void giveUp() {
        if (identified && hitmanEligible) {
            HarpoonHitman.send(factionId, victimName, originName, explosive, false);
        }

        release();
    }

    protected void release() {
        abandon();

        if (!isAlive(victim)) return;

        victim.clearAssignments();
        Misc.giveStandardReturnToSourceAssignments(victim);
    }

    protected void abandon() {
        done = true;

        if (isAlive(victim)) victim.getMemoryWithoutUpdate().unset(REPORTING_FLAG);
    }
}
