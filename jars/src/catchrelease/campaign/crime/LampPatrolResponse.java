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
 * can see them doing. So there is nothing to dispatch about - the sweep asks only whether the lamps
 * are burning, whether this is somebody's space, and whether anybody is looking. Once somebody has
 * seen it, however, the stop is committed. Putting the lamps out ends that burn, not the patrol's
 * approach; the warning or consequence still has to be delivered.
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

    /** Days before that faction retries a stop that ended while the same burn remained active. */
    public static final float RETRY_DAYS = 3f;

    /** Per-faction wait for an unresolved burn, in sector memory so it survives a reload. */
    public static final String RETRY_KEY = "$catchrelease_lampPatrolWait";

    /** Matches vanilla's own fleet-search cadence. */
    protected final IntervalUtil interval = new IntervalUtil(0.1f, 0.3f);

    protected CampaignFleetAPI stopping = null;

    /** Whether the lamps were burning last time this looked, so the moment they light is catchable. */
    protected transient boolean lit = false;

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

        if (!SearchlightAbilityPlugin.isBreaching()) {
            lit = false;
            return;
        }

        //the lamps coming on is what starts an offence, so the transition is what is watched rather
        //than the state - staying lit for a week is one burn, not a week of them
        if (!lit) {
            lit = true;
            LampOffence.beginRun();
        }

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
        if (curr.getBattle() != null) return false;
        if (curr.isHostileTo(player)) return false;

        MemoryAPI mem = curr.getMemoryWithoutUpdate();
        if (mem.getBoolean(LampOffence.SAW_KEY)) return false;

        //not "have they ever stopped you" but "have they stopped you about this burn" - see
        //LampOffence.RUN_KEY. Putting the lamps out settles a burn; lighting them again starts one
        if (LampOffence.hasBeenTold(mem)) return false;
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

        //The boolean is only the latch that says the current encounter has happened. Per-burn
        //history lives in STOPPED_RUN_KEY. Clear an expired latch before a new run so the rules
        //row can open again, including for old saves where its original 30-day timer remains.
        mem.unset(LampOffence.STOPPED_KEY);

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

        //Vanilla's pursuit flags tell the tactical AI that it may pick the player, but an active
        //assignment such as STANDING_DOWN can still keep the assignment module in charge of the
        //course. This stop is already committed, so replace the patrol's work with the intercept;
        //its route AI will supply ordinary patrol work again after the stop is cleaned up.
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        patrol.clearAssignments();
        patrol.addAssignment(FleetAssignment.INTERCEPT, player, CHASE_DAYS);

        stopping = patrol;
    }

    /**
     * Ends the stop on death, hostility, expiry, hyperspace, a location split, or the conversation
     * having happened. The lamps going out is deliberately not on that list: like a patrol that has
     * already seen the transponder violation, this crew still comes over to settle what it saw.
     */
    protected void maintain() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = stopping.getMemoryWithoutUpdate();

        //Lights-out is still the boundary between burns, even though it no longer calls off a stop
        //which has already been committed. If they are relit before this crew arrives, leave this
        //false; look() will start the new run after the current encounter is settled.
        if (!SearchlightAbilityPlugin.isBreaching()) lit = false;

        //first, before isAlive(): refusing turns them hostile and the fight happens inside the same
        //paused dialog, so by the time the script looks again the crew may already be dead
        if (mem.getBoolean(LampOffence.STOPPED_KEY)) {
            //the sheet says the conversation happened; which burn it was about is this side's to
            //record, since the sheet has no idea one is being counted
            LampOffence.markTold(mem);

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

        if (player.getVisibilityLevelTo(stopping) != VisibilityLevel.NONE) {
            Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
        }
    }

    /**
     * Calls the crew off. The assignment and tactical target go by hand since neither is on a clock
     * and would otherwise keep them flying at the player after the reason for it is gone.
     * <p>
     * Putting the lamps out closes the current burn immediately without closing the committed stop.
     * A later relight is therefore a fresh run after this encounter has been settled.
     */
    protected void end() {
        if (stopping == null) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = stopping.getMemoryWithoutUpdate();
        boolean lampsStillBurning = SearchlightAbilityPlugin.isBreaching();

        //Do not wait for the next interval sweep to notice the off transition. The player can put
        //them out during the paused conversation and relight before look() gets an off frame.
        if (!lampsStillBurning) lit = false;

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
            MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
            String retryKey = RETRY_KEY + factionId;

            if (lampsStillBurning) sector.set(retryKey, true, RETRY_DAYS);
            else sector.unset(retryKey);
        }

        mem.unset(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED);
        mem.unset(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER);
        mem.unset(LampOffence.SAW_KEY);
        mem.unset(LampOffence.STOPPED_KEY);
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
