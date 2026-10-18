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
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.FleetFilter;

import java.util.ArrayList;
import java.util.List;

public class HarpoonPatrolResponse implements EveryFrameScript {

    public static final String REASON = "catchreleasePatrol";
    public static final String PATROL_FLAG = "$catchrelease_harpoonPatrol";
    public static final String PATROL_FACTION_KEY = "$catchrelease_harpoonPatrolFaction";

    public static final String DEALT_WITH_KEY = "$catchrelease_harpoonPatrolDone";
    public static final String ANSWERED_KEY = "$catchrelease_harpoonPatrolAnswered";
    public static final String PAID_KEY = "$catchrelease_harpoonFinePaid";
    public static final String FORCED_KEY = "$catchrelease_harpoonForced";

    public static final String FINE_KEY = "$catchrelease_harpoonFine";
    public static final String FINE_TEXT_KEY = "$catchrelease_harpoonFineDGS";

    public static final String REPEAT_KEY = "$catchrelease_harpoonRepeat";
    public static final String COUNT_KEY = "$catchrelease_harpoonCount";
    public static final String RETRY_KEY = "$catchrelease_harpoonPatrolWait";
    public static final String ALERT_KEY = "$catchrelease_harpoonAlert";

    public static final int FINE = 10000;
    public static final float SEARCH_RANGE = 2500f;
    public static final float CHASE_DAYS = 12f;
    public static final float RETRY_DAYS = 5f;
    public static final float DEALT_WITH_DAYS = HarpoonOffence.MEMORY_DAYS;
    public static final float ALERT_DAYS = HarpoonOffence.MEMORY_DAYS;

    protected final IntervalUtil interval = new IntervalUtil(0.1f, 0.3f);

    protected final List<CampaignFleetAPI> chasing = new ArrayList<>();

    public static void register() {
        Global.getSector().addTransientScript(new HarpoonPatrolResponse());
    }

    @Override
    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);

        interval.advance(days);
        boolean tick = interval.intervalElapsed();

        // independent of any active chase - a refusal owed to one faction must not wait on another faction's patrol to finish flying about before it's charged for
        if (tick) {
            HarpoonOffence.applyDueEvasions();

            // and whatever a holed crew's own conversation settled, which is the same shape of handoff and has no more reason than that one to own a script
            HarpoonOffence.resolveAnsweredDemands();
        }

        for (CampaignFleetAPI patrol : new ArrayList<>(chasing)) maintainChase(patrol);

        if (!tick) return;

        reacquire();
        if (chasing.isEmpty()) beginChase();

        answerAlerts();
    }

    // patrols flagged elsewhere (a witness's report, or before a load - this script is transient) are found by their flag
    protected void reacquire() {
        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : location.getFleets()) {
                if (fleet.getMemoryWithoutUpdate().getBoolean(PATROL_FLAG)
                        && !chasing.contains(fleet)) chasing.add(fleet);
            }
        }
    }

    // a reported refusal puts the claim out to every patrol in the system that sees the player, not one collector
    protected void answerAlerts() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !player.isAlive()) return;
        if (player.isInHyperspace() || player.isInHyperspaceTransition()) return;

        LocationAPI here = player.getContainingLocation();
        for (String factionId : HarpoonOffence.getOwedFactionsIn(here)) {
            if (!isAlerted(factionId, here)) continue;

            FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction == null) continue;

            for (CampaignFleetAPI curr : here.getFleets()) {
                if (curr == player || !canBeSent(curr, faction, player)) continue;

                send(curr, factionId);
            }
        }
    }

    protected void beginChase() {
        final CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !player.isAlive()) return;
        if (player.isInHyperspace() || player.isInHyperspaceTransition()) return;

        // the claim is local: only what is owed for this system is served in it
        for (String factionId : HarpoonOffence.getOwedFactionsIn(player.getContainingLocation())) {
            if (Global.getSector().getMemoryWithoutUpdate().getBoolean(
                    retryKey(factionId, player.getContainingLocation()))) continue;

            final FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction == null) continue;

            CampaignFleetAPI patrol = findPatrol(player, faction);
            if (patrol == null) continue;

            send(patrol, factionId);
            return;
        }
    }

    protected static boolean canBeSent(CampaignFleetAPI curr, FactionAPI faction,
                                       CampaignFleetAPI player) {
        if (!canCollectFor(curr, faction, player)) return false;

        MemoryAPI mem = curr.getMemoryWithoutUpdate();
        if (mem.getBoolean(PATROL_FLAG)) return false;

        if (hasAnsweredEverything(curr, faction.getId())) return false;

        return player.getVisibilityLevelTo(curr) != VisibilityLevel.NONE;
    }

    protected static boolean canCollectFor(CampaignFleetAPI curr, FactionAPI faction,
                                           CampaignFleetAPI player) {
        FactionAPI patrolFaction = curr.getFaction();
        if (patrolFaction == null || patrolFaction.isHostileTo(faction)) return false;

        // the space polices itself, but not through just anybody: a third party only
        // collects for a faction it actually favours, and the Path and the pirates -
        // the antagonists of this trade - never collect for anybody but themselves
        boolean thirdParty = !patrolFaction.getId().equals(faction.getId());
        if (thirdParty && (Factions.LUDDIC_PATH.equals(patrolFaction.getId())
                || Factions.PIRATES.equals(patrolFaction.getId()))) {
            return false;
        }
        if (thirdParty && !patrolFaction.getRelationshipLevel(faction)
                .isAtWorst(RepLevel.FAVORABLE)) {
            return false;
        }

        if (curr.isStationMode()) return false;

        if (curr.isHostileTo(player)) return false;

        // patrols only - MEMORY_KEY_PATROL_FLEET is what the fleet AI's own pursuit support checks to decide whether a chase is sustained
        if (!curr.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) return false;

        if (curr.getAI() instanceof ModularFleetAIAPI) {
            ModularFleetAIAPI ai = (ModularFleetAIAPI) curr.getAI();
            if (ai.isFleeing()) return false;
            if (curr.getInteractionTarget() instanceof CampaignFleetAPI) return false;
        }

        return true;
    }

    public static boolean callForHelp(CampaignFleetAPI victim) {
        CampaignFleetAPI nearest = findNearbyPatrol(victim);
        if (nearest == null || victim.getFaction() == null) return false;

        String factionId = victim.getFaction().getId();

        // cleared for the same reason a fresh harpooning clears it: somebody has just asked, and being asked is the point
        clearRetryWait(factionId, victim.getContainingLocation());
        dispatch(nearest, factionId);

        return true;
    }

    public static CampaignFleetAPI findNearbyPatrol(CampaignFleetAPI victim) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (victim == null || player == null) return null;

        FactionAPI faction = victim.getFaction();
        if (faction == null || victim.getContainingLocation() == null) return null;

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

        return nearest;
    }

    public static CampaignFleetAPI findReportTarget(CampaignFleetAPI victim) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (victim == null || player == null) return null;

        FactionAPI faction = victim.getFaction();
        if (faction == null || victim.getContainingLocation() == null) return null;

        CampaignFleetAPI nearest = null;
        float best = Float.MAX_VALUE;

        for (CampaignFleetAPI curr : victim.getContainingLocation().getFleets()) {
            if (curr == victim || curr == player) continue;
            if (!canCollectFor(curr, faction, player)) continue;

            float distance = Misc.getDistance(victim.getLocation(), curr.getLocation());
            if (distance >= best) continue;

            best = distance;
            nearest = curr;
        }

        return nearest;
    }

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

    protected static boolean hasAnsweredEverything(CampaignFleetAPI patrol, String factionId) {
        return patrol.getMemoryWithoutUpdate().getInt(ANSWERED_KEY)
                >= HarpoonOffence.getIncidentCount(factionId, patrol.getContainingLocation());
    }

    protected static String retryKey(String factionId, LocationAPI where) {
        return RETRY_KEY + factionId + "_" + (where == null ? "unknown" : where.getId());
    }

    public static void clearRetryWait(String factionId, LocationAPI where) {
        Global.getSector().getMemoryWithoutUpdate().unset(retryKey(factionId, where));
    }

    protected static String alertKey(String factionId, LocationAPI where) {
        return ALERT_KEY + factionId + "_" + (where == null ? "unknown" : where.getId());
    }

    public static void raiseAlert(String factionId, LocationAPI where) {
        Global.getSector().getMemoryWithoutUpdate().set(alertKey(factionId, where), true, ALERT_DAYS);
    }

    public static void clearAlert(String factionId, LocationAPI where) {
        Global.getSector().getMemoryWithoutUpdate().unset(alertKey(factionId, where));
    }

    public static boolean isAlerted(String factionId, LocationAPI where) {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(alertKey(factionId, where));
    }

    // a reported refusal is still the first claim served, so it gets the fine offer rather than the repeat-offence attack
    protected static boolean isRepeat(String factionId, LocationAPI where) {
        return HarpoonOffence.isRepeatOffence(factionId, where) && !isAlerted(factionId, where);
    }

    protected void send(CampaignFleetAPI patrol, String factionId) {
        dispatch(patrol, factionId);

        if (!chasing.contains(patrol)) chasing.add(patrol);
    }

    protected static void dispatch(CampaignFleetAPI patrol, String factionId) {
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, true, CHASE_DAYS);

        mem.set(PATROL_FLAG, true, CHASE_DAYS);
        mem.set(PATROL_FACTION_KEY, factionId, CHASE_DAYS);

        // computed here rather than in the rules-driven conversation, which can only read a number
        mem.set(FINE_KEY, FINE, CHASE_DAYS);
        mem.set(FINE_TEXT_KEY, Misc.getWithDGS(FINE), CHASE_DAYS);
        mem.set(REPEAT_KEY, isRepeat(factionId, patrol.getContainingLocation()), CHASE_DAYS);
        mem.set(COUNT_KEY, HarpoonOffence.getIncidentCount(factionId,
                patrol.getContainingLocation()), CHASE_DAYS);
    }

    protected void maintainChase(CampaignFleetAPI patrol) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();

        if (mem.getBoolean(DEALT_WITH_KEY)) {
            collect(patrol);
            endChase(patrol);
            return;
        }

        if (!patrol.isAlive() || player == null) {
            endChase(patrol);
            return;
        }

        if (!mem.getBoolean(PATROL_FLAG)) {
            endChase(patrol);
            return;
        }

        if (player.isInHyperspace() || player.isInHyperspaceTransition()) {
            endChase(patrol);
            return;
        }

        // a gate can leave the patrol in a different system without either side giving up
        if (patrol.getContainingLocation() != player.getContainingLocation()) {
            endChase(patrol);
            return;
        }

        String factionId = mem.getString(PATROL_FACTION_KEY);
        if (factionId == null || !HarpoonOffence.isOutstanding(factionId,
                patrol.getContainingLocation())) {
            endChase(patrol);
            return;
        }

        mem.set(REPEAT_KEY, isRepeat(factionId, patrol.getContainingLocation()), CHASE_DAYS);
        mem.set(COUNT_KEY, HarpoonOffence.getIncidentCount(factionId,
                patrol.getContainingLocation()), CHASE_DAYS);

        if (patrol.isHostileTo(player)) {
            endChase(patrol);
            return;
        }

        if (player.getVisibilityLevelTo(patrol) != VisibilityLevel.NONE) {
            Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
        }
    }

    protected void collect(CampaignFleetAPI patrol) {
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();

        String factionId = mem.getString(PATROL_FACTION_KEY);
        if (factionId == null) return;

        // set before settle() clears what's owed, so it still reflects everything answered for
        mem.set(ANSWERED_KEY, HarpoonOffence.getIncidentCount(factionId,
                patrol.getContainingLocation()), DEALT_WITH_DAYS);

        HarpoonOffence.settle(factionId, patrol.getContainingLocation());

        if (mem.getBoolean(PAID_KEY)) {
            mem.unset(PAID_KEY);
            return;
        }

        // forced (second offence in the window) was never offered a price, so nothing was evaded
        if (mem.getBoolean(FORCED_KEY)) {
            mem.unset(FORCED_KEY);
            return;
        }

        HarpoonOffence.noteEvasion(factionId);
    }

    protected void endChase(CampaignFleetAPI patrol) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();

        FleetAssignmentDataAPI assignment = patrol.getCurrentAssignment();
        if (assignment != null && assignment.getAssignment() == FleetAssignment.INTERCEPT
                && assignment.getTarget() == player) {
            patrol.removeFirstAssignmentIfItIs(assignment.getAssignment());
        }

        patrol.setInteractionTarget(null);

        if (patrol.getAI() instanceof ModularFleetAIAPI) {
            ModularFleetAIAPI ai = (ModularFleetAIAPI) patrol.getAI();
            if (ai.getTacticalModule().getTarget() == player) ai.getTacticalModule().setTarget(null);
        }

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, false, 0f);

        // read before the keys below clear it
        String factionId = mem.getString(PATROL_FACTION_KEY);
        if (factionId != null) {
            Global.getSector().getMemoryWithoutUpdate().set(
                    retryKey(factionId, patrol.getContainingLocation()), true, RETRY_DAYS);
        }

        // must be cleared, or the next patrol sent would read it as already dealt with
        mem.unset(DEALT_WITH_KEY);

        mem.unset(PATROL_FLAG);
        mem.unset(PATROL_FACTION_KEY);
        mem.unset(FINE_KEY);
        mem.unset(FINE_TEXT_KEY);
        mem.unset(REPEAT_KEY);

        chasing.remove(patrol);
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
