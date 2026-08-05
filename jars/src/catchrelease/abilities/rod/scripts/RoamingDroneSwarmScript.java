package catchrelease.abilities.rod.scripts;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.abilities.rod.entities.FishingDroneEntityPlugin;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * A swarm with no pond under it: drones flying a screen around the fleet, going after whatever the
 * breach lamps have found down there.
 * <p>
 * The lamps are what makes this possible and what limits it. A beam with a breach lamp on it does not
 * merely sweep - it burns a window - and a window is something a drone can go through, which is the
 * only reason anything buried is reachable at all. So the whole mode lives and dies with the lights:
 * cast while they are on, recalled the moment they go off.
 * <p>
 * What it is fishing is the other half of the difference. A cast waits on motes drifting into a ring
 * dropped on the water; this one hunts things that are still on the far side of the fabric and have
 * been lit up, pulls them through on contact, and then plays the same catch as anything else. Nothing
 * about the flying, the catching, or the upgrades is new - the drone count, the reach, the follow-on
 * margin, the rarity priority, the speed and the steering are read exactly where they always were.
 * The only things that changed are where the middle is and what counts as a fish.
 */
public class RoamingDroneSwarmScript extends FishingDroneSwarmScript {

    /** Sends a screen out around the fleet, recalling whatever was already out there. */
    public static RoamingDroneSwarmScript dispatch() {
        return launch(new RoamingDroneSwarmScript());
    }

    public RoamingDroneSwarmScript() {
        super(null, null);
    }

    /** Around the fleet, and read live - the drones are escorting it, not holding a spot. */
    @Override
    protected FishingDroneEntityPlugin.Params createDroneParams(float slotAngle) {
        return new FishingDroneEntityPlugin.Params(null, slotAngle, RodConstants.DRONE_COLOR, true);
    }

    /**
     * The fleet, which is what everything here is measured from.
     * <p>
     * Its own location vector rather than a copy of it, so the reach, the break-off and the ring the
     * player sees all follow the fleet without anybody having to keep them up to date.
     */
    @Override
    public Vector2f getSearchCenter() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        return fleet == null ? null : fleet.getLocation();
    }

    /**
     * As far as the lights can see, and then as far again as the rod can reach past them.
     * <p>
     * Not the cast ring on its own, which is the trap this mode is one line away from at all times.
     * A cast ring is a hundred and fifty units because it is dropped onto water the player aimed at;
     * the beams throw three times their own radius, and everything this mode fishes is by definition
     * something a beam found. Gated on the ring alone the drones would circle the fleet forever
     * while every mote they could have had sat lit and several hundred units outside the line.
     * <p>
     * So the lights set the leash and the rod extends it - which leaves both upgrades doing the same
     * thing they do on a cast, one widening what is worth going after and the other how far past
     * that a drone will still follow.
     */
    @Override
    protected float getReach() {
        return Searchlight.getMaxReach() + super.getReach();
    }

    /** The leash itself out here, since it is the only line there is to draw. */
    @Override
    public float getRingDrawRadius() {
        return getReach();
    }

    @Override
    public float getPatrolRadius() {
        return RodConstants.DRONE_ROAM_RADIUS;
    }

    /**
     * What is buried nearby, rather than what is swimming. An ordinary mote out here is somebody
     * else's - the harpoon's, or a bomb's - and this rig has no business taking it off them.
     */
    @Override
    protected List<SectorEntityToken> getSearchArea() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getContainingLocation() == null) return new ArrayList<>();

        return fleet.getContainingLocation().getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG);
    }

    /**
     * Lit, and only lit. A buried mote nobody has swept over is not somewhere a drone could go even
     * if it knew where to look: there is no window through to it, and the fleet has no idea it is
     * there. Being found is the whole of what makes one reachable, and the lights forget - so this
     * goes false again on its own when a mark fades, and whoever was chasing it turns back.
     */
    @Override
    protected boolean isReachable(SectorEntityToken mote) {
        return SearchlightAbilityPlugin.isLit(mote);
    }

    /**
     * Through the window and into the game.
     * <p>
     * A drone reaching one of these has not caught anything yet - it has arrived somewhere something
     * is buried. Pulling it through is what the trip was for, and what comes up is an ordinary mote
     * standing exactly where the drone is, so the catch that follows is the catch every other rig
     * plays and none of it had to be written twice.
     */
    @Override
    protected void onMoteReached(SectorEntityToken drone, SectorEntityToken mote) {
        if (mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin) {
            //marked before it is opened rather than after: the entity is on its way out either way,
            //and leaving it unmarked lets the same one be picked up again while it fades
            handled.add(mote.getId());

            SectorEntityToken surfaced = ((BuriedMoteEntityPlugin) mote.getCustomPlugin()).unearth();

            FishingDroneEntityPlugin plugin = getPlugin(drone);

            if (surfaced == null) {
                if (plugin != null) plugin.returnToOrbit();
                return;
            }

            //moved onto what came up before the catch is offered, so a UI that was too busy to take
            //it leaves the drone holding the mote rather than holding a hole in the fabric
            if (plugin != null) plugin.chase(surfaced);

            mote = surfaced;
        }

        super.onMoteReached(drone, mote);
    }

    /**
     * Home when the windows close, when there is no fleet left to fly around, or when the fleet has
     * gone somewhere the drones cannot.
     * <p>
     * The last of those is not a case a cast ever really meets - a swarm dropped on a pond is
     * recalled the moment the player travels far enough from it, long before they leave the system.
     * This one travels with the fleet by design, so it is still out and still working at the moment
     * the fleet jumps, and drones left behind in the system it jumped out of have no way home.
     */
    @Override
    protected boolean shouldRecall() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || !SearchlightAbilityPlugin.isBreaching()) return true;

        for (SectorEntityToken drone : drones) {
            if (drone.getContainingLocation() != fleet.getContainingLocation()) return true;
        }

        return false;
    }
}
