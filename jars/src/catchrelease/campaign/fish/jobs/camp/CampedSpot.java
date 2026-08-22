package catchrelease.campaign.fish.jobs.camp;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

public class CampedSpot {

    public static final String CAMP_FLAG = "$catchrelease_campFleet";
    public static final String WHO_KEY = "$catchreleaseCampWho";

    public static final String BRIBE_KEY = "$catchrelease_campBribe";
    public static final String BRIBE_TEXT_KEY = "$catchrelease_campBribeDGS";

    public static final String CLEARED_FLAG = "$catchrelease_campCleared";
    public static final String WARNED_FLAG = "$catchrelease_campWarned";
    public static final String CLOSING_FLAG = "$catchrelease_campClosing";
    public static final float WARNING_CHASE_DAYS = 3f;
    public static final String POND_BLOCKED_FLAG = "$catchrelease_campedPond";
    public static final String CAMP_SPECIES_KEY = "$catchrelease_campedSpecies";
    public static final float OFFSET = 350f;
    public static final float HOLD_DAYS = 100000f;

    public static CampaignFleetAPI spawn(CampType type, CampSize size, SectorEntityToken pond,
                                         Random random) {
        if (type == null || size == null || pond == null) return null;

        LocationAPI where = pond.getContainingLocation();
        if (where == null) return null;

        float fp = size.rollFP(random);

        FleetParamsV3 params = new FleetParamsV3(
                null,
                where.getLocation(),
                type.factionId,
                null,
                type.fleetType,
                fp,
                0f,
                fp * 0.1f,
                0f, 0f, 0f, 0f);

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setNoFactionInName(true);
        fleet.setName(type.fleetName);

        fleet.setTransponderOn(false);

        where.addEntity(fleet);

        Vector2f at = MathUtils.getPointOnCircumference(pond.getLocation(), OFFSET,
                MathUtils.getRandomNumberInRange(0f, 360f));
        fleet.setLocation(at.x, at.y);

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();

        mem.set(CAMP_FLAG, true);
        mem.set(WHO_KEY, type.token);
        mem.set(BRIBE_KEY, type.getBribe(size));
        mem.set(BRIBE_TEXT_KEY, Misc.getWithDGS(type.getBribe(size)));
        mem.set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);

        // they came here to sit on this, and a fleet that wandered off after the first passing freighter would make the job describe something that is no longer true
        mem.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        mem.set(MemFlags.MEMORY_KEY_NO_JUMP, true);

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, pond, HOLD_DAYS, "Sitting on the rupture");

        return fleet;
    }

    public static void allowPlayerToLeave(CampaignFleetAPI fleet, SectorEntityToken pond) {
        if (fleet == null) return;

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);

        if (fleet.getBattle() != null || fleet.getAI() == null) return;

        FleetAssignmentDataAPI current = fleet.getAI().getCurrentAssignment();
        if (current == null || current.getAssignment() != FleetAssignment.ORBIT_AGGRESSIVE) return;

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, pond, HOLD_DAYS, "Sitting on the rupture");
    }

    public static void updateWarningPursuit(CampaignFleetAPI fleet, SectorEntityToken pond) {
        if (fleet == null || pond == null || fleet.getAI() == null) return;

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);

        CampaignFleetAPI player = Global.getSector() == null
                ? null : Global.getSector().getPlayerFleet();

        boolean shouldChase = !mem.getBoolean(WARNED_FLAG)
                && catchrelease.helper.CampaignHelper.isPlayerHere(fleet);

        if (!shouldChase) {
            endWarningPursuit(fleet, pond, player);
            return;
        }

        // Refresh the intent while the player is in-system. The assignment itself is repaired only when needed, so the queue cannot grow one intercept per mission tick.
        mem.set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true, WARNING_CHASE_DAYS);
        mem.set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true, WARNING_CHASE_DAYS);
        mem.set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true, WARNING_CHASE_DAYS);

        FleetAssignmentDataAPI current = fleet.getAI().getCurrentAssignment();
        if (current == null || current.getAssignment() != FleetAssignment.INTERCEPT
                || current.getTarget() != player) {
            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.INTERCEPT, player, WARNING_CHASE_DAYS,
                    "Closing to warn your fleet away");
        }

        if (!mem.getBoolean(CLOSING_FLAG)) {
            mem.set(CLOSING_FLAG, true);
            Global.getSector().getCampaignUI().addMessage(fleet.getName()
                    + " is closing on your position.", Misc.getHighlightColor());
        }
    }

    protected static void endWarningPursuit(CampaignFleetAPI fleet, SectorEntityToken pond,
                                            CampaignFleetAPI player) {
        if (fleet.getBattle() != null || fleet.getAI() == null) return;

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        FleetAssignmentDataAPI current = fleet.getAI().getCurrentAssignment();
        boolean wasWarningIntercept = current != null
                && current.getAssignment() == FleetAssignment.INTERCEPT
                && current.getTarget() == player;

        if (wasWarningIntercept || current == null
                || current.getAssignment() == FleetAssignment.ORBIT_AGGRESSIVE) {
            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, pond, HOLD_DAYS,
                    "Sitting on the rupture");
        }

        fleet.setInteractionTarget(null);
        if (fleet.getAI() instanceof ModularFleetAIAPI) {
            ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
            if (ai.getTacticalModule().getTarget() == player) {
                ai.getTacticalModule().setTarget(null);
            }
        }

        mem.unset(MemFlags.MEMORY_KEY_PURSUE_PLAYER);
        mem.unset(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE);
        // DO_NOT_GET_SIDETRACKED and NO_JUMP are permanent camp duties; only the temporary player-targeting flag belongs to this chase.
        mem.unset(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER);
    }

    public static void setPondBlocked(SectorEntityToken pond, boolean blocked) {
        setPondBlocked(pond, blocked, null);
    }

    public static void setPondBlocked(SectorEntityToken pond, boolean blocked, String speciesId) {
        if (pond == null) return;

        if (blocked) {
            pond.getMemoryWithoutUpdate().set(POND_BLOCKED_FLAG, true);
            if (speciesId != null) pond.getMemoryWithoutUpdate().set(CAMP_SPECIES_KEY, speciesId);
            else pond.getMemoryWithoutUpdate().unset(CAMP_SPECIES_KEY);
        } else {
            pond.getMemoryWithoutUpdate().unset(POND_BLOCKED_FLAG);
            pond.getMemoryWithoutUpdate().unset(CAMP_SPECIES_KEY);
        }
    }

    public static boolean isPondBlocked(SectorEntityToken pond) {
        return pond != null && pond.getMemoryWithoutUpdate().getBoolean(POND_BLOCKED_FLAG);
    }

    public static String getCampedSpecies(SectorEntityToken pond) {
        if (!isPondBlocked(pond)) return null;

        return pond.getMemoryWithoutUpdate().getString(CAMP_SPECIES_KEY);
    }

    public static boolean isGone(CampaignFleetAPI camper) {
        if (camper == null) return true;
        if (camper.isExpired() || !camper.isAlive()) return true;
        if (camper.getContainingLocation() == null) return true;

        return camper.getMemoryWithoutUpdate().getBoolean(CLEARED_FLAG);
    }

    public static void despawn(CampaignFleetAPI camper) {
        if (camper == null || camper.isExpired()) return;

        camper.getMemoryWithoutUpdate().unset(CAMP_FLAG);
        camper.despawn();
    }

    public static boolean hasSector() {
        return Global.getSector() != null;
    }
}
