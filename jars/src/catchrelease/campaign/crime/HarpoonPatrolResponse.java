package catchrelease.campaign.crime;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
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
 * Sends a faction patrol to chase the player down and demand a fine after a harpooning, while the
 * incident is still on the faction's books.
 * <p>
 * One patrol at a time, on a clock, with pursuit flags refreshed only while it can still see the
 * player - losing it is possible.
 * <p>
 * Transient: rebuilt on every load. All state lives in game memory rather than fields, and the
 * chased patrol is re-found by its flag, so a reload never resets the clock or orphans a chase.
 */
public class HarpoonPatrolResponse implements EveryFrameScript {

    /**
     * Reason key for the pursuit flags. Distinct from a harpooned crew's own hostility reason so
     * calling off the chase doesn't also clear that grudge.
     */
    public static final String REASON = "catchreleasePatrol";

    /** Marks the sent patrol; also its clock - set for the chase length and never refreshed. */
    public static final String PATROL_FLAG = "$catchrelease_harpoonPatrol";

    /** Which harpooning this patrol has come about. */
    public static final String PATROL_FACTION_KEY = "$catchrelease_harpoonPatrolFaction";

    /**
     * Set by the encounter once the patrol has had its say. Read once and cleared with the chase;
     * {@link #ANSWERED_KEY} is what persists.
     */
    public static final String DEALT_WITH_KEY = "$catchrelease_harpoonPatrolDone";

    /**
     * Count (not flag) of this faction's harpoonings this patrol has already been out about, so a
     * repeat harpooning of the same faction isn't silently absorbed by a patrol that already
     * answered for an earlier one. Compared against {@link HarpoonOffence#getIncidentCount}, which
     * is pruned to the same window, so answers lapse on their own.
     */
    public static final String ANSWERED_KEY = "$catchrelease_harpoonPatrolAnswered";

    /** Set by the encounter when the fine was paid. */
    public static final String PAID_KEY = "$catchrelease_harpoonFinePaid";

    /** Set by the encounter when a second harpooning inside the window forced the fight, no negotiation. */
    public static final String FORCED_KEY = "$catchrelease_harpoonForced";

    /** What the patrol will want, and the same figure formatted for display. */
    public static final String FINE_KEY = "$catchrelease_harpoonFine";
    public static final String FINE_TEXT_KEY = "$catchrelease_harpoonFineDGS";

    /** Set when there is nothing to negotiate because this isn't the first offence. */
    public static final String REPEAT_KEY = "$catchrelease_harpoonRepeat";

    /** Per-faction wait between chases, kept in memory so it survives a reload. */
    public static final String RETRY_KEY = "$catchrelease_harpoonPatrolWait";

    /** What one costs. */
    public static final int FINE = 10000;

    /** How far from the player a patrol can be and still be the one that gets sent. */
    public static final float SEARCH_RANGE = 2500f;

    /** Days one patrol will keep after the player before losing interest. */
    public static final float CHASE_DAYS = 12f;

    /** Days after a chase ends before the faction sends another. */
    public static final float RETRY_DAYS = 5f;

    /** Days a patrol remembers what it's answered for; matches {@link HarpoonOffence#MEMORY_DAYS}. */
    public static final float DEALT_WITH_DAYS = HarpoonOffence.MEMORY_DAYS;

    /** How often to look for a patrol worth sending, in days - matches vanilla's own search interval. */
    protected final IntervalUtil interval = new IntervalUtil(0.1f, 0.3f);

    protected CampaignFleetAPI chasing = null;

    public static void register() {
        Global.getSector().addTransientScript(new HarpoonPatrolResponse());
    }

    @Override
    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);

        interval.advance(days);
        boolean tick = interval.intervalElapsed();

        //independent of any active chase - a refusal owed to one faction must not wait on another
        //faction's patrol to finish flying about before it's charged for
        if (tick) {
            HarpoonOffence.applyDueEvasions();

            //and whatever a holed crew's own conversation settled, which is the same shape of
            //handoff and has no more reason than that one to own a script
            HarpoonOffence.resolveAnsweredDemands();
        }

        if (chasing != null) {
            maintainChase();
            return;
        }

        if (!tick) return;

        chasing = reacquire();
        if (chasing != null) return;

        beginChase();
    }

    /** Re-finds an in-progress chase by its flag on the fleet, since the chase lives in memory, not here. */
    protected CampaignFleetAPI reacquire() {
        //searches every location, not just the player's, since a patrol left behind elsewhere still
        //holds its flag until maintainChase() notices the location mismatch and lets it go
        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : location.getFleets()) {
                if (!fleet.getMemoryWithoutUpdate().getBoolean(PATROL_FLAG)) continue;

                return fleet;
            }
        }

        return null;
    }

    /** Picks one patrol to send, if any faction is owed and has a patrol near enough. */
    protected void beginChase() {
        final CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !player.isAlive()) return;
        if (player.isInHyperspace() || player.isInHyperspaceTransition()) return;

        for (String factionId : HarpoonOffence.getOwedFactions()) {
            if (Global.getSector().getMemoryWithoutUpdate().getBoolean(RETRY_KEY + factionId)) continue;

            final FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction == null) continue;

            CampaignFleetAPI patrol = findPatrol(player, faction);
            if (patrol == null) continue;

            send(patrol, factionId);
            return;
        }
    }

    /**
     * Whether this fleet is one that could be sent after the player about a harpooning.
     * <p>
     * Its own method because two things ask it and they look for candidates differently: the sweep
     * takes whoever is near the player, and a holed crew calling it in takes whoever is in earshot
     * of *them*. What makes a fleet a suitable answer is the same question either way.
     */
    protected static boolean canBeSent(CampaignFleetAPI curr, FactionAPI faction,
                                       CampaignFleetAPI player) {
        if (curr.getFaction() != faction) return false;
        if (curr.isStationMode()) return false;

        //already at war - nothing left to intercept the player about
        if (curr.isHostileTo(player)) return false;

        MemoryAPI mem = curr.getMemoryWithoutUpdate();
        if (mem.getBoolean(PATROL_FLAG)) return false;

        if (hasAnsweredEverything(curr, faction.getId())) return false;

        //patrols only - MEMORY_KEY_PATROL_FLEET is what the fleet AI's own pursuit support checks
        //to decide whether a chase is sustained
        if (!mem.getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) return false;

        if (curr.getAI() instanceof ModularFleetAIAPI) {
            ModularFleetAIAPI ai = (ModularFleetAIAPI) curr.getAI();
            if (ai.isFleeing()) return false;
            if (curr.getInteractionTarget() instanceof CampaignFleetAPI) return false;
        }

        return player.getVisibilityLevelTo(curr) != VisibilityLevel.NONE;
    }

    /**
     * A holed crew putting it on the local channel, which is a different search to the sweep's.
     * <p>
     * Anywhere in their own system rather than within the sweep's range of the player, because the
     * distance that matters is how far the call carries rather than how far the patrol is from
     * whoever it is being sent after. The retry wait is cleared for the same reason a fresh
     * harpooning clears it: somebody has just asked, and being asked is the point.
     *
     * @return whether anyone was in earshot
     */
    public static boolean callForHelp(CampaignFleetAPI victim) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (victim == null || player == null) return false;

        FactionAPI faction = victim.getFaction();
        if (faction == null || victim.getContainingLocation() == null) return false;

        CampaignFleetAPI nearest = null;
        float best = Float.MAX_VALUE;

        for (CampaignFleetAPI curr : victim.getContainingLocation().getFleets()) {
            if (curr == victim || curr == player) continue;
            if (!canBeSent(curr, faction, player)) continue;

            float distance = Misc.getDistance(victim.getLocation(), curr.getLocation());
            if (distance >= best) continue;

            best = distance;
            nearest = curr;
        }

        if (nearest == null) return false;

        clearRetryWait(faction.getId());
        dispatch(nearest, faction.getId());

        return true;
    }

    /** The nearest patrol of this faction that is in a position to be sent after anybody. */
    protected CampaignFleetAPI findPatrol(final CampaignFleetAPI player, final FactionAPI faction) {
        List<CampaignFleetAPI> patrols = Misc.findNearbyFleets(player, SEARCH_RANGE, new FleetFilter() {
            @Override
            public boolean accept(CampaignFleetAPI curr) {
                return canBeSent(curr, faction, player);
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

    /** Whether this crew has already been out about every harpooning on the faction's current books. */
    protected static boolean hasAnsweredEverything(CampaignFleetAPI patrol, String factionId) {
        return patrol.getMemoryWithoutUpdate().getInt(ANSWERED_KEY)
                >= HarpoonOffence.getIncidentCount(factionId);
    }

    /** Lets a faction send somebody again without waiting out the retry delay; called on a new harpooning. */
    public static void clearRetryWait(String factionId) {
        Global.getSector().getMemoryWithoutUpdate().unset(RETRY_KEY + factionId);
    }

    protected void send(CampaignFleetAPI patrol, String factionId) {
        dispatch(patrol, factionId);

        chasing = patrol;
    }

    /**
     * Puts a patrol on the player, and nothing else.
     * <p>
     * Static and without touching {@link #chasing}, because a crew calling one in has no script
     * instance to hand - the flag is the chase, and the running script re-finds it by that flag on
     * its next tick the same way it does after a reload.
     */
    protected static void dispatch(CampaignFleetAPI patrol, String factionId) {
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();

        //1-day flag, refreshed in maintainChase() while the patrol still has eyes on the player;
        //letting it lapse is how the chase ends
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, true, CHASE_DAYS);

        mem.set(PATROL_FLAG, true, CHASE_DAYS);
        mem.set(PATROL_FACTION_KEY, factionId, CHASE_DAYS);

        //computed here rather than in the rules-driven conversation, which can only read a number
        mem.set(FINE_KEY, FINE, CHASE_DAYS);
        mem.set(FINE_TEXT_KEY, Misc.getWithDGS(FINE), CHASE_DAYS);
        mem.set(REPEAT_KEY, HarpoonOffence.isRepeatOffence(factionId), CHASE_DAYS);
    }

    /** Ends the chase on death, hostility, expiry, hyperspace, location split, or a settled conversation. */
    protected void maintainChase() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = chasing.getMemoryWithoutUpdate();

        //checked first, before isAlive(): refusing a fine turns the patrol hostile and the resulting
        //fight happens inside the same paused dialog, so by the time the script looks again the
        //fleet may already be dead - this must still register as an answered conversation
        if (mem.getBoolean(DEALT_WITH_KEY)) {
            collect();
            endChase();
            return;
        }

        if (!chasing.isAlive() || player == null) {
            endChase();
            return;
        }

        if (!mem.getBoolean(PATROL_FLAG)) {
            endChase();
            return;
        }

        if (player.isInHyperspace() || player.isInHyperspaceTransition()) {
            endChase();
            return;
        }

        //a gate can leave the patrol in a different system without either side giving up
        if (chasing.getContainingLocation() != player.getContainingLocation()) {
            endChase();
            return;
        }

        //debt settled elsewhere or lapsed while the chase was on
        String factionId = mem.getString(PATROL_FACTION_KEY);
        if (factionId == null || !HarpoonOffence.isOutstanding(factionId)) {
            endChase();
            return;
        }

        //re-harpooned while this patrol was still en route, which changes what it's come to say
        mem.set(REPEAT_KEY, HarpoonOffence.isRepeatOffence(factionId), CHASE_DAYS);

        if (chasing.isHostileTo(player)) {
            endChase();
            return;
        }

        if (player.getVisibilityLevelTo(chasing) != VisibilityLevel.NONE) {
            Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
        }
    }

    /**
     * Takes the harpooning off the faction's books now that a patrol has asked about it - the
     * asking settles it, the answer only decides the price: paid, forced, or filed as an evasion.
     */
    protected void collect() {
        MemoryAPI mem = chasing.getMemoryWithoutUpdate();

        String factionId = mem.getString(PATROL_FACTION_KEY);
        if (factionId == null) return;

        //set before settle() clears what's owed, so it still reflects everything answered for
        mem.set(ANSWERED_KEY, HarpoonOffence.getIncidentCount(factionId), DEALT_WITH_DAYS);

        HarpoonOffence.settle(factionId);

        if (mem.getBoolean(PAID_KEY)) {
            mem.unset(PAID_KEY);
            return;
        }

        //forced (second offence in the window) was never offered a price, so nothing was evaded
        if (mem.getBoolean(FORCED_KEY)) {
            mem.unset(FORCED_KEY);
            return;
        }

        HarpoonOffence.noteEvasion(factionId);
    }

    /**
     * Calls the patrol off. The assignment and tactical target are cleared by hand since neither is
     * on a clock and would otherwise keep the patrol flying at the player. {@link #ANSWERED_KEY} is
     * left alone - it's meant to outlive the chase.
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

        //read before the keys below clear it
        String factionId = mem.getString(PATROL_FACTION_KEY);
        if (factionId != null) {
            Global.getSector().getMemoryWithoutUpdate().set(RETRY_KEY + factionId, true, RETRY_DAYS);
        }

        //must be cleared, or the next patrol sent would read it as already dealt with
        mem.unset(DEALT_WITH_KEY);

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
