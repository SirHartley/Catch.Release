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
 * A drone swarm with no pond under it: drones screen the fleet and go after whatever the breach
 * lamps ({@link SearchlightAbilityPlugin}) have lit up as buried nearby, pulling it through on
 * contact and playing the same catch as any other rig.
 * <p>
 * Depends entirely on the lamps - a breach lamp burns a window a drone can pass through, which is
 * the only way anything buried is reachable, so this swarm is cast while the lights are on and
 * recalled the moment they go off. Drone count, reach, rarity priority, speed and steering are
 * unchanged from the normal swarm; only the search center and target type differ.
 */
public class RoamingDroneSwarmScript extends FishingDroneSwarmScript {

    /** Sends a screen out around the fleet, recalling whatever was already out there. */
    public static RoamingDroneSwarmScript dispatch() {
        return launch(new RoamingDroneSwarmScript());
    }

    public RoamingDroneSwarmScript() {
        super(null, null);
    }

    /** Drones escort the fleet live rather than holding a fixed spot. */
    @Override
    protected FishingDroneEntityPlugin.Params createDroneParams(float slotAngle) {
        return new FishingDroneEntityPlugin.Params(null, slotAngle, RodConstants.DRONE_COLOR, true);
    }

    /** The fleet's own location vector, not a copy, so reach/break-off/ring all track it live. */
    @Override
    public Vector2f getSearchCenter() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        return fleet == null ? null : fleet.getLocation();
    }

    /**
     * As far as the lights can see, plus the rod's own reach past them. Must include the lamp
     * range - everything this mode fishes was found by a lamp, and the lamps throw far past the
     * normal cast ring, so gating on the cast ring alone would leave drones circling the fleet
     * while lit motes sit unreachable outside it.
     */
    @Override
    protected float getReach() {
        return Searchlight.getMaxReach() + super.getReach();
    }

    @Override
    public float getRingDrawRadius() {
        return getReach();
    }

    @Override
    public float getPatrolRadius() {
        return RodConstants.DRONE_ROAM_RADIUS;
    }

    /** Buried motes only - an ordinary swimming mote belongs to the harpoon, not this rig. */
    @Override
    protected List<SectorEntityToken> getSearchArea() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getContainingLocation() == null) return new ArrayList<>();

        return fleet.getContainingLocation().getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG);
    }

    /**
     * Only motes currently lit by a lamp - unlit ones have no window through and go unreachable
     * again as the mark fades.
     * <p>
     * Lit outright, never merely dented. A dent is the fabric bruising near a beam rather than a
     * hole burned through it, and there is nothing there for a drone to fly into; taking one
     * anyway is the harpoon's Fathom Head, which is the whole of what that module is for. A rig
     * that could do it unaided would leave nothing to buy.
     * <p>
     * Asked of buried motes only, which is everything this swarm goes looking for. A drone still
     * holding the ordinary mote it has just unearthed is asked on ordinary terms instead: that one
     * is through the fabric and swimming, and the lamp that showed it has no further say over it.
     */
    @Override
    protected boolean isReachable(SectorEntityToken mote) {
        if (mote != null && mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin) {
            return SearchlightAbilityPlugin.isLit(mote);
        }

        return super.isReachable(mote);
    }

    /** Reaching a buried mote unearths it into an ordinary mote, then plays the normal catch. */
    @Override
    protected void onMoteReached(SectorEntityToken drone, SectorEntityToken mote) {
        if (mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin) {
            //marked before unearthing so a fading entity can't be picked up twice
            handled.add(mote.getId());

            SectorEntityToken surfaced = ((BuriedMoteEntityPlugin) mote.getCustomPlugin()).unearth();

            FishingDroneEntityPlugin plugin = getPlugin(drone);

            if (surfaced == null) {
                if (plugin != null) plugin.returnToOrbit();
                return;
            }

            //drone re-targeted onto the surfaced mote before the catch is offered, so a busy UI
            //leaves it holding the mote rather than a hole in the fabric
            if (plugin != null) plugin.chase(surfaced);

            mote = surfaced;
        }

        super.onMoteReached(drone, mote);
    }

    /**
     * Recalls when the lamps go off, the fleet is gone, or the fleet has jumped to a system a
     * drone isn't in - a case a pond-anchored swarm never hits, since it's recalled by distance
     * long before the player leaves the system.
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
