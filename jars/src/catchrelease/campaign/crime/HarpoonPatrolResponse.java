package catchrelease.campaign.crime;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.FleetFilter;

import java.util.List;

/**
 * Somebody has to come and ask about the harpoon.
 * <p>
 * A faction that has been shot at with a rope does not simply note it down. While the incident is
 * still on their slate, their patrols stop being scenery: one of them peels off and comes to have a
 * word, and keeps coming as long as it can see where the player went.
 * <p>
 * Built the way vanilla builds the cargo scan, which is the one live example of the same idea - a
 * faction reaching out and touching the player over something the player did. One patrol at a time,
 * a chase with a clock on it, and the pursuit flags refreshed only while the patrol can actually see
 * what it is chasing, so losing them is a thing the player can do rather than a thing that happens.
 */
public class HarpoonPatrolResponse implements EveryFrameScript {

    /** Under whose name the pursuit flags are held, so lifting ours never lifts somebody else's. */
    public static final String REASON = "catchreleaseHarpoon";

    /**
     * Set on the patrol that has been sent, so the encounter knows which conversation to have.
     * <p>
     * Its own flag rather than the pursuit reason, because the two answer different questions - one
     * is why the patrol is flying at the player, the other is what it says when it arrives, and only
     * the first should lift when the chase times out.
     */
    public static final String PATROL_FLAG = "$catchrelease_harpoonPatrol";

    /** Which harpooning the patrol has come about, so the dialogue can name who is owed. */
    public static final String PATROL_FACTION_KEY = "$catchrelease_harpoonPatrolFaction";

    /** Set by the encounter once this patrol has had its say, so it does not ask twice. */
    public static final String DEALT_WITH_KEY = "$catchrelease_harpoonPatrolDone";

    /** How far from the player a patrol can be and still be the one that gets sent. */
    public static final float SEARCH_RANGE = 2500f;

    /** Days one patrol will keep after the player before losing interest. */
    public static final float CHASE_DAYS = 12f;

    /** Days after a chase ends before the faction bothers sending another. */
    public static final float RETRY_DAYS = 5f;

    /**
     * How often to look for a patrol worth sending, in days.
     * <p>
     * Vanilla's own interval for the same search. Cheap on the frame either way, and a fraction of a
     * day is well inside the time it takes anything to fly anywhere.
     */
    protected final IntervalUtil interval = new IntervalUtil(0.1f, 0.3f);

    protected CampaignFleetAPI chasing = null;
    protected float chaseElapsed = 0f;
    protected float retryWait = 0f;

    public static void register() {
        //transient: the state below is one patrol and two timers, all of it re-derivable from the
        //debts in the save, and none of it worth writing into one
        Global.getSector().addTransientScript(new HarpoonPatrolResponse());
    }

    @Override
    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);

        if (chasing != null) {
            maintainChase(days);
            return;
        }

        if (retryWait > 0f) retryWait -= days;

        interval.advance(days);
        if (!interval.intervalElapsed()) return;

        if (retryWait > 0f) return;

        beginChase();
    }

    /**
     * Picks a patrol to send, if anyone is owed and anyone is near enough to send.
     * <p>
     * Only ever one at a time. A faction with a grievance and four patrols in the system does not
     * get to dogpile: the point is being found, not being hunted to extinction, and the player who
     * shakes one off has bought themselves the retry wait rather than the next one immediately.
     */
    protected void beginChase() {
        final CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !player.isAlive()) return;
        if (player.isInHyperspace() || player.isInHyperspaceTransition()) return;

        for (String factionId : HarpoonOffence.getOwedFactions()) {
            final FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction == null) continue;

            CampaignFleetAPI patrol = findPatrol(player, faction);
            if (patrol == null) continue;

            send(patrol, factionId);
            return;
        }
    }

    /** The nearest patrol of this faction that is in a position to be sent after anybody. */
    protected CampaignFleetAPI findPatrol(final CampaignFleetAPI player, final FactionAPI faction) {
        List<CampaignFleetAPI> patrols = Misc.findNearbyFleets(player, SEARCH_RANGE, new FleetFilter() {
            @Override
            public boolean accept(CampaignFleetAPI curr) {
                if (curr.getFaction() != faction) return false;
                if (curr.isStationMode()) return false;

                //already at war, so there is nothing to intercept the player about - they will
                //come for them anyway, and on their own terms
                if (curr.isHostileTo(player)) return false;

                //one that has already had this conversation, or is having one now
                if (curr.getMemoryWithoutUpdate().getBoolean(DEALT_WITH_KEY)) return false;

                if (!curr.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)
                        && !curr.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_WAR_FLEET)) {
                    return false;
                }

                if (curr.getAI() instanceof ModularFleetAIAPI) {
                    ModularFleetAIAPI ai = (ModularFleetAIAPI) curr.getAI();
                    if (ai.isFleeing()) return false;
                    if (curr.getInteractionTarget() instanceof CampaignFleetAPI) return false;
                }

                //a patrol that cannot see the player has nothing to go on. Being found is supposed
                //to follow from having been seen
                return player.getVisibilityLevelTo(curr) != VisibilityLevel.NONE;
            }
        });

        CampaignFleetAPI closest = null;
        float bestDistance = Float.MAX_VALUE;

        for (CampaignFleetAPI curr : patrols) {
            float distance = Misc.getDistance(player.getLocation(), curr.getLocation());
            if (distance >= bestDistance) continue;

            bestDistance = distance;
            closest = curr;
        }

        return closest;
    }

    protected void send(CampaignFleetAPI patrol, String factionId) {
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();

        //a day at a time, refreshed below while the patrol still has eyes on. The short clock is the
        //whole mechanism: stop refreshing it and the chase ends by itself
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, true, CHASE_DAYS);

        mem.set(PATROL_FLAG, true, CHASE_DAYS);
        mem.set(PATROL_FACTION_KEY, factionId, CHASE_DAYS);

        chasing = patrol;
        chaseElapsed = 0f;
    }

    /**
     * Keeps the pursuit alive for as long as it deserves to be.
     * <p>
     * Everything here is a reason to stop. The patrol died, the patrol turned hostile and is now
     * somebody else's problem, the chase ran out of days, the player made hyperspace, or the
     * conversation happened and there is nothing left to chase about.
     */
    protected void maintainChase(float days) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (!chasing.isAlive() || player == null) {
            endChase();
            return;
        }

        chaseElapsed += days;
        if (chaseElapsed > CHASE_DAYS) {
            endChase();
            return;
        }

        if (player.isInHyperspace() || player.isInHyperspaceTransition()) {
            endChase();
            return;
        }

        MemoryAPI mem = chasing.getMemoryWithoutUpdate();

        //the encounter sets this when the patrol has said its piece, however that went
        if (mem.getBoolean(DEALT_WITH_KEY)) {
            endChase();
            return;
        }

        //settled with somebody else, or lapsed while the chase was on. Either way this patrol is
        //flying at the player over a debt that no longer exists
        String factionId = mem.getString(PATROL_FACTION_KEY);
        if (factionId == null || !HarpoonOffence.isOutstanding(factionId)) {
            endChase();
            return;
        }

        //hostile now, so the encounter this was going to produce is not the one it would get
        if (chasing.isHostileTo(player)) {
            endChase();
            return;
        }

        if (player.getVisibilityLevelTo(chasing) != VisibilityLevel.NONE) {
            Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
        }
    }

    /**
     * Calls the patrol off and puts everything back the way it was found.
     * <p>
     * The assignment and the tactical target are cleared by hand because neither is on a clock -
     * left behind, a patrol that has lost interest keeps flying at the player anyway, on orders
     * nothing will ever rescind.
     */
    protected void endChase() {
        if (chasing == null) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = chasing.getMemoryWithoutUpdate();

        FleetAssignmentDataAPI assignment = chasing.getCurrentAssignment();
        if (assignment != null && assignment.getAssignment() == FleetAssignment.INTERCEPT
                && assignment.getTarget() == player) {
            chasing.removeFirstAssignmentIfItIs(assignment.getAssignment());
        }

        chasing.setInteractionTarget(null);

        if (chasing.getAI() instanceof ModularFleetAIAPI) {
            ModularFleetAIAPI ai = (ModularFleetAIAPI) chasing.getAI();
            if (ai.getTacticalModule().getTarget() == player) ai.getTacticalModule().setTarget(null);
        }

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, false, 0f);

        mem.unset(PATROL_FLAG);
        mem.unset(PATROL_FACTION_KEY);

        chasing = null;
        chaseElapsed = 0f;
        retryWait = RETRY_DAYS;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
