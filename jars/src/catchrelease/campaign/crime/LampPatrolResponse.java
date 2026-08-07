package catchrelease.campaign.crime;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
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
 * Patrols coming over to have a word about the breach lamps, while they are lit somewhere they are
 * not allowed to be.
 * <p>
 * The shape is vanilla's transponder response and not the harpoon patrol's, because the offence is
 * the same shape as the transponder's: not an incident on somebody's books that a patrol is sent
 * about days later, but a thing the player is doing <i>right now</i> that anybody in line of sight
 * can see them doing. So there is nothing to remember and nothing to dispatch about - the sweep asks
 * only whether the lamps are burning, whether this is somebody's space, and whether anybody is
 * looking. Turn them off before the patrol arrives and the whole thing goes away, which is the
 * point.
 * <p>
 * One at a time, like the harpoon patrol, so a busy system does not produce a queue of crews all
 * wanting the same conversation. What they actually say, and what the ladder costs, is
 * {@link LampOffence} and {@code rules.csv}.
 * <p>
 * Transient: rebuilt on every load, all state in game memory, the stopped patrol re-found by its
 * flag.
 */
public class LampPatrolResponse implements EveryFrameScript {

    /** Reason key for the pursuit flags, distinct from the harpoon patrol's own. */
    public static final String REASON = "catchreleaseLamps";

    /** Which faction this crew is stopping the player on behalf of. */
    public static final String FACTION_KEY = "$catchrelease_lampPatrolFaction";

    /** How far from the player a patrol can be and still notice. */
    public static final float SEARCH_RANGE = 2500f;

    /** Days one crew will keep after the player about it before giving up. */
    public static final float CHASE_DAYS = 8f;

    /** Days after a stop ends before that faction sends anybody else. */
    public static final float RETRY_DAYS = 3f;

    /** Per-faction wait between stops, in sector memory so it survives a reload. */
    public static final String RETRY_KEY = "$catchrelease_lampPatrolWait";

    /** Matches vanilla's own fleet-search cadence. */
    protected final IntervalUtil interval = new IntervalUtil(0.1f, 0.3f);

    protected CampaignFleetAPI stopping = null;

    public static void register() {
        Global.getSector().addTransientScript(new LampPatrolResponse());
    }

    @Override
    public void advance(float amount) {
        interval.advance(Global.getSector().getClock().convertToDays(amount));

        if (stopping != null) {
            maintain();
            return;
        }

        if (!interval.intervalElapsed()) return;

        stopping = reacquire();
        if (stopping != null) return;

        look();
    }

    /** Re-finds an in-progress stop by its flag, since the stop lives in memory rather than here. */
    protected CampaignFleetAPI reacquire() {
        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : location.getFleets()) {
                if (fleet.getMemoryWithoutUpdate().getBoolean(LampOffence.SAW_KEY)) return fleet;
            }
        }

        return null;
    }

    /** The whole of the trigger: lamps lit, somebody's space, somebody watching. */
    protected void look() {
        final CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !player.isAlive()) return;
        if (player.isInHyperspace() || player.isInHyperspaceTransition()) return;

        if (!SearchlightAbilityPlugin.isBreaching()) return;

        CampaignFleetAPI closest = null;
        float best = Float.MAX_VALUE;

        List<CampaignFleetAPI> nearby = Misc.findNearbyFleets(player, SEARCH_RANGE, new FleetFilter() {
            @Override
            public boolean accept(CampaignFleetAPI curr) {
                return canObject(curr, player);
            }
        });

        for (CampaignFleetAPI curr : nearby) {
            float distance = Misc.getDistance(player.getLocation(), curr.getLocation());
            if (distance >= best) continue;

            best = distance;
            closest = curr;
        }

        if (closest == null) return;

        send(closest);
    }

    /**
     * Whether this crew both would and could come over about it.
     * <p>
     * Patrols only, the same test the harpoon response uses and for the same reason: it is what the
     * fleet AI's own pursuit support reads to decide whether a chase is sustained, and a freighter
     * has no business enforcing anything. Anybody already at war with the player is skipped - there
     * is no conversation left to have with somebody who is going to shoot regardless.
     */
    protected static boolean canObject(CampaignFleetAPI curr, CampaignFleetAPI player) {
        FactionAPI faction = curr.getFaction();
        if (faction == null || faction.isPlayerFaction()) return false;

        if (curr.isStationMode()) return false;
        if (curr.isHostileTo(player)) return false;

        MemoryAPI mem = curr.getMemoryWithoutUpdate();
        if (mem.getBoolean(LampOffence.SAW_KEY)) return false;
        if (mem.getBoolean(LampOffence.STOPPED_KEY)) return false;
        if (!mem.getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) return false;

        if (Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(RETRY_KEY + faction.getId())) return false;

        if (curr.getAI() instanceof ModularFleetAIAPI) {
            ModularFleetAIAPI ai = (ModularFleetAIAPI) curr.getAI();
            if (ai.isFleeing()) return false;
            if (curr.getInteractionTarget() instanceof CampaignFleetAPI) return false;
        }

        if (!LampOffence.isIllegalHere(player, faction.getId())) return false;

        //last, because it is the only test that asks the sensor model anything
        return player.getVisibilityLevelTo(curr) != VisibilityLevel.NONE;
    }

    protected void send(CampaignFleetAPI patrol) {
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();

        //1-day flag, refreshed in maintain() while they still have eyes on the player; letting it
        //lapse is how a crew loses interest
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, true, CHASE_DAYS);

        //a patrol on a route would otherwise fly straight past somebody it has no business with
        mem.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true, CHASE_DAYS);
        mem.set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true, CHASE_DAYS);

        mem.set(LampOffence.SAW_KEY, true, CHASE_DAYS);
        mem.set(FACTION_KEY, patrol.getFaction().getId(), CHASE_DAYS);

        stopping = patrol;
    }

    /**
     * Ends the stop on death, hostility, expiry, hyperspace, a location split, the conversation
     * having happened, or - the one this response has and the harpoon patrol does not - the lamps
     * simply going out.
     */
    protected void maintain() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = stopping.getMemoryWithoutUpdate();

        //first, before isAlive(): refusing turns them hostile and the fight happens inside the same
        //paused dialog, so by the time the script looks again the crew may already be dead
        if (mem.getBoolean(LampOffence.STOPPED_KEY)) {
            end();
            return;
        }

        if (!stopping.isAlive() || player == null) {
            end();
            return;
        }

        if (!mem.getBoolean(LampOffence.SAW_KEY)) {
            end();
            return;
        }

        if (player.isInHyperspace() || player.isInHyperspaceTransition()) {
            end();
            return;
        }

        if (stopping.getContainingLocation() != player.getContainingLocation()) {
            end();
            return;
        }

        if (stopping.isHostileTo(player)) {
            end();
            return;
        }

        //the lamps put out, or the player having flown somewhere this crew has no say over. Nothing
        //is charged for it: what they came about has stopped happening, which is what they wanted
        String factionId = mem.getString(FACTION_KEY);
        if (!SearchlightAbilityPlugin.isBreaching()
                || factionId == null || !LampOffence.isIllegalHere(player, factionId)) {

            end();
            return;
        }

        if (player.getVisibilityLevelTo(stopping) != VisibilityLevel.NONE) {
            Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
        }
    }

    /**
     * Calls the crew off. The assignment and tactical target go by hand since neither is on a clock
     * and would otherwise keep them flying at the player after the reason for it is gone.
     */
    protected void end() {
        if (stopping == null) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = stopping.getMemoryWithoutUpdate();

        FleetAssignmentDataAPI assignment = stopping.getCurrentAssignment();
        if (assignment != null && assignment.getAssignment() == FleetAssignment.INTERCEPT
                && assignment.getTarget() == player) {
            stopping.removeFirstAssignmentIfItIs(assignment.getAssignment());
        }

        stopping.setInteractionTarget(null);

        if (stopping.getAI() instanceof ModularFleetAIAPI) {
            ModularFleetAIAPI ai = (ModularFleetAIAPI) stopping.getAI();
            if (ai.getTacticalModule().getTarget() == player) ai.getTacticalModule().setTarget(null);
        }

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, false, 0f);

        String factionId = mem.getString(FACTION_KEY);
        if (factionId != null) {
            Global.getSector().getMemoryWithoutUpdate().set(RETRY_KEY + factionId, true, RETRY_DAYS);
        }

        mem.unset(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED);
        mem.unset(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER);
        mem.unset(LampOffence.SAW_KEY);
        mem.unset(FACTION_KEY);

        stopping = null;
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
