package catchrelease.campaign.fish.jobs.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.DistressCallIntel;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A fleet that cannot come to the player, so the player is told where it is instead.
 * <p>
 * Anything whose problem is that it cannot move has no way to arrive - a dead drive does not wander
 * into view - and materialising one next to the player is the thing that reads as scenery appearing
 * out of nothing. Vanilla already has the answer to this and it is the same answer it uses for its
 * own stranded ships: the call comes over hyperspace from a system some way off, and the player
 * goes and looks.
 * <p>
 * The intel is vanilla's own {@link DistressCallIntel}, so this arrives with the sound, the icon and
 * the wording the player already associates with somebody in trouble, and it points at a system
 * rather than at a hull - there is nothing to know until you get there.
 * <p>
 * The system is chosen the way vanilla chooses one for the same event, down to skipping pulsars,
 * hidden systems and anywhere with a market: a distress call out of a populated system is a call
 * somebody nearer would have answered.
 */
public class FleetDistressCall {

    /** How far the call carries, in light years. Vanilla's own setting for its distress calls. */
    public static final String RANGE_SETTING = "distressCallEventRangeLY";
    public static final float RANGE_FALLBACK = 3f;

    /** How far off the jump point the fleet sits - near it, not on top of it. */
    public static final float JUMP_POINT_OFFSET_MIN = 400f;
    public static final float JUMP_POINT_OFFSET_MAX = 900f;

    /**
     * Puts a stranded fleet in a system near the player and calls for help.
     *
     * @return the job that was started, or null if there was nowhere to put one
     */
    public static FleetQuest raise(FleetQuestType type, Random random) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || type == null) return null;

        StarSystemAPI system = pickSystem(player, random);
        if (system == null) return null;

        JumpPointAPI jumpPoint = Misc.getDistressJumpPoint(system);
        if (jumpPoint == null) return null;

        CampaignFleetAPI fleet = createFleet(player, type);
        if (fleet == null) return null;

        float offset = JUMP_POINT_OFFSET_MIN
                + random.nextFloat() * (JUMP_POINT_OFFSET_MAX - JUMP_POINT_OFFSET_MIN);

        Vector2f at = Misc.getPointAtRadius(jumpPoint.getLocation(), offset);

        system.addEntity(fleet);
        fleet.setLocation(at.x, at.y);

        FleetQuest quest = FleetQuest.startOn(fleet, type);

        //nothing took it, so nothing should be left standing out there
        if (quest == null) {
            fleet.despawn();
            return null;
        }

        FleetQuestEncounter.attach(fleet, quest);

        new DistressCallIntel(system);

        return quest;
    }

    /**
     * A system near enough to hear the call and empty enough that nobody nearer would have answered
     * it first. Never the one the player is standing in - the point is being told where to go.
     */
    protected static StarSystemAPI pickSystem(CampaignFleetAPI player, Random random) {
        float range = Global.getSettings().getFloat(RANGE_SETTING);
        if (range <= 0f) range = RANGE_FALLBACK;

        List<StarSystemAPI> candidates = new ArrayList<>();

        for (StarSystemAPI system : Misc.getNearbyStarSystems(player, range)) {
            if (system == player.getContainingLocation()) continue;

            //vanilla's own exclusions for this event
            if (system.hasPulsar()) continue;
            if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            if (system.hasTag(Tags.THEME_HIDDEN)) continue;

            //somebody lives there, and they would have gone themselves
            if (!Misc.getMarketsInLocation(system).isEmpty()) continue;

            if (Misc.getDistressJumpPoint(system) == null) continue;

            candidates.add(system);
        }

        if (candidates.isEmpty()) return null;

        return candidates.get(random.nextInt(candidates.size()));
    }

    /** A hauler with nothing worth fighting over - somebody asking for help, not lying in wait. */
    protected static CampaignFleetAPI createFleet(CampaignFleetAPI player, FleetQuestType type) {
        FleetParamsV3 params = new FleetParamsV3(
                null,
                player.getLocationInHyperspace(),
                Factions.INDEPENDENT,
                null,
                type.fleetType,
                0f,   //combat
                6f,   //freighter
                2f,   //tanker
                0f, 0f, 0f, 0f);

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        //on, because a fleet asking for help is not hiding from anybody
        fleet.setTransponderOn(true);

        return fleet;
    }
}
