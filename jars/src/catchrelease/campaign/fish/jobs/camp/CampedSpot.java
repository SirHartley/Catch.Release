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

/**
 * The fleet sitting on somebody's fishing spot, and the flags a conversation with it reads.
 * <p>
 * Spawned rather than borrowed, which is the opposite of what {@code FleetQuestSpawner} does and for
 * a plain reason: a fleet quest hangs an offer on somebody who was already flying, and this is a
 * hull that has to be <i>in a specific place</i> before the job can be described at all. There is no
 * existing fleet parked on a random rupture four jumps out to borrow.
 * <p>
 * They are left hostile if their flag is hostile. That is the point of {@code HailPlayer} in the
 * encounter row - vanilla's gate-camping pirates are hostile too, and you can still talk to them,
 * because the hail is what opens the link rather than the relationship. Nothing here softens
 * anybody: a pirate pack is a pirate pack, and the conversation is a thing you get to have with it
 * rather than a promise about how it ends.
 */
public class CampedSpot {

    /** Set on the camper, and the whole of what the encounter rows gate on. */
    public static final String CAMP_FLAG = "$catchrelease_campFleet";

    /** Which of the three they are, for the rows that pick what they say. */
    public static final String WHO_KEY = "$catchreleaseCampWho";

    /** What they will take to go away, and the same figure formatted for a line of dialogue. */
    public static final String BRIBE_KEY = "$catchrelease_campBribe";
    public static final String BRIBE_TEXT_KEY = "$catchrelease_campBribeDGS";

    /** Set by the conversation once they have agreed to leave, either way. */
    public static final String CLEARED_FLAG = "$catchrelease_campCleared";

    /** Set by the first hail, after the camp has intercepted the player to state its claim. */
    public static final String WARNED_FLAG = "$catchrelease_campWarned";

    /** Prevents the closing-on-position notice repeating if the player leaves before contact. */
    public static final String CLOSING_FLAG = "$catchrelease_campClosing";

    public static final float WARNING_CHASE_DAYS = 3f;

    /** Set on the rupture while its camper remains, for the ROD's fishing lock. */
    public static final String POND_BLOCKED_FLAG = "$catchrelease_campedPond";

    /** The species that made this occupied rupture worth guarding. Kept on the pond, not its mote. */
    public static final String CAMP_SPECIES_KEY = "$catchrelease_campedSpecies";

    /** How far off the water they sit - close enough to be sitting on it, not close enough to be in it. */
    public static final float OFFSET = 350f;

    /** Days the hold assignment runs for. Not meant to be reached; the job outlives it either way. */
    public static final float HOLD_DAYS = 100000f;

    /**
     * Puts a fleet on a rupture and leaves it there.
     *
     * @return the camper, or null if one could not be built
     */
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
                fp,          //combat
                0f,          //freighter
                fp * 0.1f,   //tanker
                0f, 0f, 0f, 0f);

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setNoFactionInName(true);
        fleet.setName(type.fleetName);

        //nobody camping a rupture files a flight plan
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

        //they came here to sit on this, and a fleet that wandered off after the first passing
        //freighter would make the job describe something that is no longer true
        mem.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        mem.set(MemFlags.MEMORY_KEY_NO_JUMP, true);

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, pond, HOLD_DAYS, "Sitting on the rupture");

        return fleet;
    }

    /**
     * Keeps a camper on its water without turning a refused conversation into an inescapable
     * pursuit. The assignment repair also updates campers carried by older saves.
     */
    public static void allowPlayerToLeave(CampaignFleetAPI fleet, SectorEntityToken pond) {
        if (fleet == null) return;

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);

        if (fleet.getBattle() != null || fleet.getAI() == null) return;

        FleetAssignmentDataAPI current = fleet.getAI().getCurrentAssignment();
        if (current == null || current.getAssignment() != FleetAssignment.ORBIT_AGGRESSIVE) return;

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, pond, HOLD_DAYS, "Sitting on the rupture");
    }

    /**
     * Runs the player down once, so the camp states its claim before becoming a passive obstruction.
     * The pursuit only exists while both fleets share a location and before the first hail has fired;
     * after that, the fleet returns to the rupture and the ordinary allow-disengage behavior applies.
     */
    public static void updateWarningPursuit(CampaignFleetAPI fleet, SectorEntityToken pond) {
        if (fleet == null || pond == null || fleet.getAI() == null) return;

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);

        CampaignFleetAPI player = Global.getSector() == null
                ? null : Global.getSector().getPlayerFleet();

        boolean shouldChase = !mem.getBoolean(WARNED_FLAG)
                && player != null
                && fleet.getContainingLocation() != null
                && fleet.getContainingLocation() == player.getContainingLocation();

        if (!shouldChase) {
            endWarningPursuit(fleet, pond, player);
            return;
        }

        //Refresh the intent while the player is in-system. The assignment itself is repaired only
        //when needed, so the queue cannot grow one intercept per mission tick.
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

    /** Clears only this feature's chase and restores the camp's passive hold. */
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
        //DO_NOT_GET_SIDETRACKED and NO_JUMP are permanent camp duties; only the temporary
        //player-targeting flag belongs to this chase.
        mem.unset(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER);
    }

    public static void setPondBlocked(SectorEntityToken pond, boolean blocked) {
        setPondBlocked(pond, blocked, null);
    }

    /**
     * Sets the fishing lock and, while it is held, remembers what the camp was sitting on.
     * <p>
     * The memory belongs to the rupture rather than the planted mote: the mote can be caught or
     * expire before the camp goes away, but the camp is still a meaningful chart-request lead.
     */
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

    /** The active camp's species, or null once the rupture has been released. */
    public static String getCampedSpecies(SectorEntityToken pond) {
        if (!isPondBlocked(pond)) return null;

        return pond.getMemoryWithoutUpdate().getString(CAMP_SPECIES_KEY);
    }

    /**
     * Whether the spot is free again, however it came to be free.
     * <p>
     * Killed, bought off, talked off, or gone for reasons nobody was watching - the job does not
     * care which, and asking one question rather than four is what keeps it from having an opinion
     * about how the player solved it.
     */
    public static boolean isGone(CampaignFleetAPI camper) {
        if (camper == null) return true;
        if (camper.isExpired() || !camper.isAlive()) return true;
        if (camper.getContainingLocation() == null) return true;

        return camper.getMemoryWithoutUpdate().getBoolean(CLEARED_FLAG);
    }

    /** Sends them away for good, for a job that has ended without anybody dealing with them. */
    public static void despawn(CampaignFleetAPI camper) {
        if (camper == null || camper.isExpired()) return;

        camper.getMemoryWithoutUpdate().unset(CAMP_FLAG);
        camper.despawn();
    }

    /** Whether there is a sector at all, for callers running outside a campaign. */
    public static boolean hasSector() {
        return Global.getSector() != null;
    }
}
