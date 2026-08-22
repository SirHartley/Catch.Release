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


public class LampPatrolResponse implements EveryFrameScript {


    public static final String REASON = "catchreleaseLamps";


    public static final String FACTION_KEY = "$catchrelease_lampPatrolFaction";


    public static final String SYSTEM_KEY = "$catchrelease_lampPatrolSystem";


    public static final float CHASE_DAYS = 8f;


    public static final float RETRY_DAYS = 3f;


    public static final String RETRY_KEY = "$catchrelease_lampPatrolWait";


    protected final IntervalUtil interval = new IntervalUtil(0.1f, 0.3f);


    protected final List<CampaignFleetAPI> stopping = new ArrayList<>();


    protected transient boolean lit = false;

    public static void register() {
        Global.getSector().addTransientScript(new LampPatrolResponse());
    }

    @Override
    public void advance(float amount) {
        interval.advance(Global.getSector().getClock().convertToDays(amount));

        if (!stopping.isEmpty()) maintain();

        if (!interval.intervalElapsed()) return;

        // Flag reacquisition covers save/load and stays on the search interval; an idle script should not walk every fleet once per frame.
        if (stopping.isEmpty()) {
            reacquire();
            if (!stopping.isEmpty()) maintain();
        }

        look();
    }


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


    protected void look() {
        final CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !player.isAlive()) return;
        if (player.isInHyperspace() || player.isInHyperspaceTransition()) return;

        if (!SearchlightAbilityPlugin.isBreaching()) {
            lit = false;
            return;
        }

        if (!lit && !stopping.isEmpty()) return;

        if (!lit) {
            lit = true;
            LampOffence.beginRun();
        }

        if (LampOffence.isRunResolved()) return;

        for (CampaignFleetAPI curr
                : new ArrayList<>(player.getContainingLocation().getFleets())) {
            if (canObject(curr, player)) send(curr);
        }
    }


    protected static boolean canObject(CampaignFleetAPI curr, CampaignFleetAPI player) {
        FactionAPI faction = curr.getFaction();
        if (faction == null || faction.isPlayerFaction()) return false;

        if (curr.isStationMode()) return false;
        if (curr.getBattle() != null) return false;
        if (curr.isHostileTo(player)) return false;

        MemoryAPI mem = curr.getMemoryWithoutUpdate();
        if (mem.getBoolean(LampOffence.SAW_KEY)) return false;

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

        // last, because it is the only test that asks the sensor model anything
        return player.getVisibilityLevelTo(curr) != VisibilityLevel.NONE;
    }

    protected void send(CampaignFleetAPI patrol) {
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();

        mem.unset(LampOffence.STOPPED_KEY);

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, 1f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, true, CHASE_DAYS);

        // a patrol on a route would otherwise fly straight past somebody it has no business with
        mem.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true, CHASE_DAYS);
        mem.set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true, CHASE_DAYS);

        mem.set(LampOffence.SAW_KEY, true, CHASE_DAYS);
        mem.set(FACTION_KEY, patrol.getFaction().getId(), CHASE_DAYS);
        mem.set(SYSTEM_KEY, patrol.getContainingLocation().getId(), CHASE_DAYS);

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        patrol.addAssignmentAtStart(FleetAssignment.INTERCEPT, player, CHASE_DAYS, null);

        if (!stopping.contains(patrol)) stopping.add(patrol);
    }


    protected void maintain() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (!SearchlightAbilityPlugin.isBreaching()) lit = false;

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


    protected void end(CampaignFleetAPI patrol) {
        if (patrol == null) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();
        boolean lampsStillBurning = SearchlightAbilityPlugin.isBreaching();

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
