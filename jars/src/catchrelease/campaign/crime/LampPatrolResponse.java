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

import java.util.ArrayList;
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
 * Every patrol with eyes on a near-world offence comes at once. The first one to make contact owns
 * the conversation; its stopped flag settles that burn and sends every other responder back to the
 * assignment which the intercept interrupted. What the winner says, and what the ladder costs, is
 * {@link LampOffence} and {@code rules.csv}.
 * <p>
 * Transient: rebuilt on every load, all state in game memory, the responding patrols re-found by
 * their flags.
 */
public class LampPatrolResponse implements EveryFrameScript {

    /** Reason key for the pursuit flags, distinct from the harpoon patrol's own. */
    public static final String REASON = "catchreleaseLamps";

    /** Which faction this crew is stopping the player on behalf of. */
    public static final String FACTION_KEY = "$catchrelease_lampPatrolFaction";

    /** The system whose law produced this stop, retained if the fleet or player leaves it. */
    public static final String SYSTEM_KEY = "$catchrelease_lampPatrolSystem";

    /** Days one crew will keep after the player about it before giving up. */
    public static final float CHASE_DAYS = 8f;

    /** Days before that faction retries a stop that ended while the same burn remained active. */
    public static final float RETRY_DAYS = 3f;

    /** Per-system-and-faction wait key stem for an unresolved burn. */
    public static final String RETRY_KEY = "$catchrelease_lampPatrolWait";

    /** Matches vanilla's own fleet-search cadence. */
    protected final IntervalUtil interval = new IntervalUtil(0.1f, 0.3f);

    /** Every patrol currently committed to this burn; rebuilt from their flags on load. */
    protected final List<CampaignFleetAPI> stopping = new ArrayList<>();

    /** Whether the lamps were burning last time this looked, so the moment they light is catchable. */
    protected transient boolean lit = false;

    public static void register() {
        Global.getSector().addTransientScript(new LampPatrolResponse());
    }

    @Override
    public void advance(float amount) {
        interval.advance(Global.getSector().getClock().convertToDays(amount));

        if (!stopping.isEmpty()) maintain();

        if (!interval.intervalElapsed()) return;

        //Flag reacquisition covers save/load and stays on the search interval; an idle script
        //should not walk every fleet once per frame.
        if (stopping.isEmpty()) {
            reacquire();
            if (!stopping.isEmpty()) maintain();
        }

        look();
    }

    /** Re-finds every in-progress stop by its flag, since the stop lives in memory rather than here. */
    protected void reacquire() {
        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : location.getFleets()) {
                if (fleet.getMemoryWithoutUpdate().getBoolean(LampOffence.SAW_KEY)
                        && !stopping.contains(fleet)) {
                    stopping.add(fleet);
                }
            }
        }
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

        //Lights-out starts a new burn, but not in the middle of the committed stop about the old
        //one. This also keeps a load during an approach from incrementing the persisted run.
        if (!lit && !stopping.isEmpty()) return;

        //the lamps coming on is what starts an offence, so the transition is what is watched rather
        //than the state - staying lit for a week is one burn, not a week of them
        if (!lit) {
            lit = true;
            LampOffence.beginRun();
        }

        //The first responder already delivered this burn's stop. Fresh patrols can see the lamps,
        //but do not form a second queue behind a conversation which has already happened.
        if (LampOffence.isRunResolved()) return;

        //Visibility is the range limit. A fixed-radius prefilter could exclude a patrol whose
        //sensor strength genuinely lets it see the player's very detectable lit fleet.
        for (CampaignFleetAPI curr
                : new ArrayList<>(player.getContainingLocation().getFleets())) {
            if (canObject(curr, player)) send(curr);
        }
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

        String retryKey = retryKey(player.getContainingLocation(), faction.getId());
        if (retryKey != null
                && Global.getSector().getMemoryWithoutUpdate().getBoolean(retryKey)) return false;

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
        mem.set(SYSTEM_KEY, patrol.getContainingLocation().getId(), CHASE_DAYS);

        //Vanilla's pursuit flags tell the tactical AI that it may pick the player, but an active
        //assignment such as STANDING_DOWN can still keep the assignment module in charge of the
        //course. Put the intercept first so it takes control now without destroying the work which
        //must resume if another responder reaches the player first.
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        patrol.addAssignmentAtStart(FleetAssignment.INTERCEPT, player, CHASE_DAYS, null);

        if (!stopping.contains(patrol)) stopping.add(patrol);
    }

    /**
     * Ends the stop on death, hostility, expiry, hyperspace, a location split, or the conversation
     * having happened. The lamps going out is deliberately not on that list: like a patrol that has
     * already seen the transponder violation, this crew still comes over to settle what it saw.
     */
    protected void maintain() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        //Lights-out is still the boundary between burns, even though it no longer calls off a stop
        //which has already been committed. If they are relit before this crew arrives, leave this
        //false; look() will start the new run after the current encounter is settled.
        if (!SearchlightAbilityPlugin.isBreaching()) lit = false;

        //First, before liveness or hostility: refusing can turn the winner hostile and resolve the
        //fight inside the paused dialog. Its stopped flag still claims the encounter for the group.
        for (CampaignFleetAPI patrol : new ArrayList<>(stopping)) {
            if (!patrol.getMemoryWithoutUpdate().getBoolean(LampOffence.STOPPED_KEY)) continue;

            LampOffence.markRunResolved();
            for (CampaignFleetAPI responder : new ArrayList<>(stopping)) {
                LampOffence.markTold(responder.getMemoryWithoutUpdate());
                end(responder);
            }
            return;
        }

        for (CampaignFleetAPI patrol : new ArrayList<>(stopping)) {
            MemoryAPI mem = patrol.getMemoryWithoutUpdate();

            if (!patrol.isAlive() || player == null
                    || !mem.getBoolean(LampOffence.SAW_KEY)
                    || player.isInHyperspace() || player.isInHyperspaceTransition()
                    || patrol.getContainingLocation() != player.getContainingLocation()
                    || patrol.isHostileTo(player)) {
                end(patrol);
                continue;
            }

            if (player.getVisibilityLevelTo(patrol) != VisibilityLevel.NONE) {
                Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
            }
        }
    }

    /**
     * Calls the crew off. The assignment and tactical target go by hand since neither is on a clock
     * and would otherwise keep them flying at the player after the reason for it is gone.
     * <p>
     * Putting the lamps out closes the current burn immediately without closing the committed stop.
     * A later relight is therefore a fresh run after this encounter has been settled.
     */
    protected void end(CampaignFleetAPI patrol) {
        if (patrol == null) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();
        boolean lampsStillBurning = SearchlightAbilityPlugin.isBreaching();

        //Do not wait for the next interval sweep to notice the off transition. The player can put
        //them out during the paused conversation and relight before look() gets an off frame.
        if (!lampsStillBurning) lit = false;

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

        String factionId = mem.getString(FACTION_KEY);
        String systemId = mem.getString(SYSTEM_KEY);
        String retryKey = retryKey(systemId, factionId);
        if (retryKey != null) {
            MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();

            if (lampsStillBurning && !LampOffence.isRunResolved()) {
                sector.set(retryKey, true, RETRY_DAYS);
            } else {
                sector.unset(retryKey);
            }
        }

        mem.unset(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED);
        mem.unset(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER);
        mem.unset(LampOffence.SAW_KEY);
        mem.unset(LampOffence.STOPPED_KEY);
        mem.unset(FACTION_KEY);
        mem.unset(SYSTEM_KEY);

        stopping.remove(patrol);
    }

    /** One retry slot per enforcing faction per system; neither dimension may leak into another. */
    protected static String retryKey(LocationAPI location, String factionId) {
        return location == null ? null : retryKey(location.getId(), factionId);
    }

    protected static String retryKey(String systemId, String factionId) {
        if (systemId == null || factionId == null) return null;
        return RETRY_KEY + "_" + systemId + "_" + factionId;
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
