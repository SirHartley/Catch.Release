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
 * <p>
 * Every clock here lives in game memory rather than in a field, and the patrol being chased with is
 * found again by its flag rather than remembered. The script is transient - it is rebuilt on every
 * load - and a chase that lived in its fields would restart its timer on every reload, forfeit the
 * wait the player earned by shaking one off, and leave the previous patrol flying at them with
 * nothing left alive to call it off.
 */
public class HarpoonPatrolResponse implements EveryFrameScript {

    /**
     * Under whose name the pursuit flags are held.
     * <p>
     * Deliberately not the one a harpooned crew's own hostility is held under. Both are keyed to
     * this mod and both can be on the same fleet at once - harpoon the patrol that came about the
     * last harpoon and they are - and calling off the chase must not take the grudge with it.
     */
    public static final String REASON = "catchreleasePatrol";

    /**
     * Set on the patrol that has been sent, so the encounter knows which conversation to have.
     * <p>
     * Also the chase's clock. It is set for the length of the chase and never refreshed, so the
     * chase is over exactly when it says so - and it says so across a save, which a counter in a
     * field cannot.
     */
    public static final String PATROL_FLAG = "$catchrelease_harpoonPatrol";

    /** Which harpooning the patrol has come about, so the debt can be found again to settle it. */
    public static final String PATROL_FACTION_KEY = "$catchrelease_harpoonPatrolFaction";

    /** Set by the encounter once this patrol has had its say, however that went. */
    public static final String DEALT_WITH_KEY = "$catchrelease_harpoonPatrolDone";

    /** Set by the encounter when the fine was paid, which is the one outcome that settles it. */
    public static final String PAID_KEY = "$catchrelease_harpoonFinePaid";

    /** What the patrol will want, and the same figure written the way a person would say it. */
    public static final String FINE_KEY = "$catchrelease_harpoonFine";
    public static final String FINE_TEXT_KEY = "$catchrelease_harpoonFineDGS";

    /** Set when there is nothing to negotiate, because this is not the first one. */
    public static final String REPEAT_KEY = "$catchrelease_harpoonRepeat";

    /** Where the wait between chases is kept, so it survives a reload the way the chase does. */
    public static final String RETRY_KEY = "$catchrelease_harpoonPatrolWait";

    /** What one costs. */
    public static final int FINE = 10000;

    /** How far from the player a patrol can be and still be the one that gets sent. */
    public static final float SEARCH_RANGE = 2500f;

    /** Days one patrol will keep after the player before losing interest. */
    public static final float CHASE_DAYS = 12f;

    /** Days after a chase ends before the faction bothers sending another. */
    public static final float RETRY_DAYS = 5f;

    /**
     * Days a patrol that has already had this conversation stays out of it.
     * <p>
     * The whole memory window, so one patrol is one conversation per incident. Shorter and the same
     * ships come back to ask the same question; unbounded and a patrol that once collected a fine is
     * excluded from every incident for the rest of the game.
     */
    public static final float DEALT_WITH_DAYS = HarpoonOffence.MEMORY_DAYS;

    /**
     * How often to look for a patrol worth sending, in days.
     * <p>
     * Vanilla's own interval for the same search. Cheap on the frame either way, and a fraction of a
     * day is well inside the time it takes anything to fly anywhere.
     */
    protected final IntervalUtil interval = new IntervalUtil(0.1f, 0.3f);

    protected CampaignFleetAPI chasing = null;

    public static void register() {
        //transient, and safe to be: everything worth keeping is in game memory, so a fresh instance
        //picks the chase back up rather than starting a new one
        Global.getSector().addTransientScript(new HarpoonPatrolResponse());
    }

    @Override
    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);

        interval.advance(days);
        boolean tick = interval.intervalElapsed();

        //ahead of the chase and not inside it, because a refusal owed to one faction must not wait
        //on some other faction's patrol to finish flying about before it is charged for
        if (tick) HarpoonOffence.applyDueEvasions();

        if (chasing != null) {
            maintainChase();
            return;
        }

        if (!tick) return;

        //a chase already under way, from before this script existed
        chasing = reacquire();
        if (chasing != null) return;

        beginChase();
    }

    /**
     * Finds the patrol that was already coming, if one was.
     * <p>
     * The flag on the fleet is the chase, not this object, so a load in the middle of one is a
     * matter of looking around for it rather than of having remembered it.
     */
    protected CampaignFleetAPI reacquire() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() == null) return null;

        for (CampaignFleetAPI fleet : player.getContainingLocation().getFleets()) {
            if (!fleet.getMemoryWithoutUpdate().getBoolean(PATROL_FLAG)) continue;

            return fleet;
        }

        return null;
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
            //per faction, so one faction's patrol going home does not buy the player five quiet
            //days from everybody else they have put a rope into
            if (Global.getSector().getMemoryWithoutUpdate().getBoolean(RETRY_KEY + factionId)) continue;

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

                //one that has already had this conversation, or is on its way to have it
                MemoryAPI mem = curr.getMemoryWithoutUpdate();
                if (mem.getBoolean(DEALT_WITH_KEY) || mem.getBoolean(PATROL_FLAG)) return false;

                //patrols only. A war fleet carries the other flag and is on somebody's raid, and
                //the fleet AI's own pursuit support is written against patrols in both of the
                //places that decide whether a chase is sustained at all
                if (!mem.getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) return false;

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

        //worked out here rather than in the conversation, because the conversation is written in
        //rules and rules cannot count. What they can do is read a number somebody left for them
        mem.set(FINE_KEY, FINE, CHASE_DAYS);
        mem.set(FINE_TEXT_KEY, Misc.getWithDGS(FINE), CHASE_DAYS);
        mem.set(REPEAT_KEY, HarpoonOffence.isRepeatOffence(factionId), CHASE_DAYS);

        chasing = patrol;
    }

    /**
     * Keeps the pursuit alive for as long as it deserves to be.
     * <p>
     * Everything here is a reason to stop. The patrol died, the patrol turned hostile and is now
     * somebody else's problem, the chase ran out of days, the player made hyperspace, or the
     * conversation happened and there is nothing left to chase about.
     */
    protected void maintainChase() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (!chasing.isAlive() || player == null) {
            endChase();
            return;
        }

        MemoryAPI mem = chasing.getMemoryWithoutUpdate();

        //the encounter sets this when the patrol has said its piece. Read before anything else,
        //because settling the debt has to happen while the fleet still says whose debt it was
        if (mem.getBoolean(DEALT_WITH_KEY)) {
            collect();
            endChase();
            return;
        }

        //the flag is the clock, and it has run out
        if (!mem.getBoolean(PATROL_FLAG)) {
            endChase();
            return;
        }

        if (player.isInHyperspace() || player.isInHyperspaceTransition()) {
            endChase();
            return;
        }

        //not only hyperspace: a gate leaves the patrol in a system the player is no longer in, with
        //nothing to see and no way to give up, and the flag would sit on it for its whole clock
        if (chasing.getContainingLocation() != player.getContainingLocation()) {
            endChase();
            return;
        }

        //settled elsewhere, or lapsed while the chase was on. Either way this patrol is flying at
        //the player over a debt that no longer exists
        String factionId = mem.getString(PATROL_FACTION_KEY);
        if (factionId == null || !HarpoonOffence.isOutstanding(factionId)) {
            endChase();
            return;
        }

        //harpooned again while this one was still on its way, which changes what it has come to say
        mem.set(REPEAT_KEY, HarpoonOffence.isRepeatOffence(factionId), CHASE_DAYS);

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
     * Takes the harpooning off the faction's slate, if the conversation ended with it paid for.
     * <p>
     * Paying is the only thing that settles it. Refusing turns the patrol into a fight, and a fight
     * the player wins or runs from is not an answer to the question - the debt stands, and the
     * faction sends somebody else.
     */
    protected void collect() {
        MemoryAPI mem = chasing.getMemoryWithoutUpdate();

        String factionId = mem.getString(PATROL_FACTION_KEY);
        if (factionId == null) return;

        //either way the faction has had its answer and stops sending people about this one. What
        //differs is what the answer was
        HarpoonOffence.settle(factionId);

        if (mem.getBoolean(PAID_KEY)) {
            mem.unset(PAID_KEY);
            return;
        }

        //refused, or closed the link and flew off, which the faction files as the same thing. The
        //bill for it arrives days later, somewhere else
        HarpoonOffence.noteEvasion(factionId);
    }

    /**
     * Calls the patrol off and puts everything back the way it was found.
     * <p>
     * The assignment and the tactical target are cleared by hand because neither is on a clock -
     * left behind, a patrol that has lost interest keeps flying at the player anyway, on orders
     * nothing will ever rescind. What is deliberately left is the flag saying this patrol has had
     * its conversation, which has a clock and is supposed to outlive the chase.
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

        //read before the keys go, since the wait is kept per faction and this is where its name is
        String factionId = mem.getString(PATROL_FACTION_KEY);
        if (factionId != null) {
            Global.getSector().getMemoryWithoutUpdate().set(RETRY_KEY + factionId, true, RETRY_DAYS);
        }

        mem.unset(PATROL_FLAG);
        mem.unset(PATROL_FACTION_KEY);
        mem.unset(FINE_KEY);
        mem.unset(FINE_TEXT_KEY);
        mem.unset(REPEAT_KEY);

        chasing = null;
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
