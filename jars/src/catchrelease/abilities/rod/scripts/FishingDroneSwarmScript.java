package catchrelease.abilities.rod.scripts;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.abilities.rod.entities.FishingDroneEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.entities.MaskedFishingPondEntityPlugin;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * One cast: sends drones to the spot the rod was aimed at, keeps them circling it while it watches
 * for a mote to drift inside the ring, and brings them home when the trip is over.
 * <p>
 * Only one swarm is out at a time - a new cast recalls the old one. Deliberately does not run while
 * paused, so the drones hold still while the minigame is up.
 */
public class FishingDroneSwarmScript implements EveryFrameScript {

    protected SectorEntityToken pond;
    protected Vector2f target;

    protected List<SectorEntityToken> drones = new ArrayList<>();
    protected IntervalUtil searchInterval = new IntervalUtil(RodConstants.DRONE_SEARCH_INTERVAL, RodConstants.DRONE_SEARCH_INTERVAL);

    protected boolean recalling = false;
    protected boolean done = false;

    /**
     * Sends a swarm to a spot, recalling whatever was already out there. The drone count is the
     * {@link StatIds#FISHING_DRONE_COUNT} upgrade.
     */
    public static FishingDroneSwarmScript dispatch(SectorEntityToken pond, Vector2f target) {
        recallExisting();

        FishingDroneSwarmScript script = new FishingDroneSwarmScript(pond, target);
        script.spawnDrones();

        Global.getSector().addScript(script);
        return script;
    }

    /** Sends any swarm already out back to the fleet, so a new cast never leaves strays behind. */
    public static void recallExisting() {
        for (EveryFrameScript script : new ArrayList<>(Global.getSector().getScripts())) {
            if (script instanceof FishingDroneSwarmScript) {
                ((FishingDroneSwarmScript) script).recall();
            }
        }
    }

    public FishingDroneSwarmScript(SectorEntityToken pond, Vector2f target) {
        this.pond = pond;
        this.target = target;
    }

    protected void spawnDrones() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        int count = getDroneCount();

        for (int i = 0; i < count; i++) {
            //spread the slots evenly so the drones do not stack up on the same arc
            float slotAngle = 360f / count * i;

            SectorEntityToken drone = fleet.getContainingLocation().addCustomEntity(
                    Misc.genUID(), null, FishingDroneEntityPlugin.ENTITY_ID, null,
                    new FishingDroneEntityPlugin.Params(target, slotAngle, RodConstants.DRONE_COLOR));

            drone.setLocation(fleet.getLocation().x, fleet.getLocation().y);
            drones.add(drone);
        }
    }

    public static int getDroneCount() {
        UpgradeManager upgrades = UpgradeManager.getInstance();

        if (!upgrades.hasStat(StatIds.FISHING_DRONE_COUNT)) return RodConstants.DRONE_COUNT_FALLBACK;

        return Math.max(1, Math.round(upgrades.getCurrentValue(StatIds.FISHING_DRONE_COUNT)));
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        dropExpiredDrones();

        //everything is home
        if (drones.isEmpty()) {
            done = true;
            return;
        }

        if (!recalling && shouldRecall()) {
            recall();
            return;
        }

        if (recalling) return;

        searchInterval.advance(amount);
        if (searchInterval.intervalElapsed()) lookForCatch();
    }

    /** The trip is over once the player leaves the pond behind, or the pond closes under them. */
    protected boolean shouldRecall() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || pond == null) return true;

        if (!pond.isInCurrentLocation()) return true;
        if (fleet.getContainingLocation() != pond.getContainingLocation()) return true;

        if (pond.getCustomPlugin() instanceof MaskedFishingPondEntityPlugin
                && !((MaskedFishingPondEntityPlugin) pond.getCustomPlugin()).isActive()) {
            return true;
        }

        return Misc.getDistance(fleet.getLocation(), pond.getLocation())
                > pond.getRadius() * PondConstants.POND_INTERACT_RANGE_MULT;
    }

    /**
     * Looks for a mote inside the ring for an idle drone to work on. Motes drift, so the answer is
     * usually "not yet" - the swarm simply keeps circling until one wanders in.
     */
    protected void lookForCatch() {
        if (pond == null || target == null) return;

        SectorEntityToken drone = getIdleDrone();
        if (drone == null) return;

        for (SectorEntityToken mote : pond.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (mote.isExpired()) continue;
            if (Misc.getDistance(mote.getLocation(), target) > RodConstants.DRONE_ORBIT_RADIUS) continue;

            onMoteFound(drone, mote);
            return;
        }
    }

    /**
     * A mote drifted into the ring with a drone free to take it. The minigame hangs off here; until
     * it exists the swarm just notes the fish and carries on circling.
     */
    protected void onMoteFound(SectorEntityToken drone, SectorEntityToken mote) {
        FishEntityPlugin plugin = mote.getCustomPlugin() instanceof FishEntityPlugin
                ? (FishEntityPlugin) mote.getCustomPlugin()
                : null;

        String fish = plugin == null || plugin.getFishSpec() == null
                ? "nothing in particular"
                : plugin.getFishSpec().getDisplayName();

        Global.getLogger(FishingDroneSwarmScript.class).info("Drone found a mote in the ring: " + fish);
    }

    /** A drone that is on station and not already carrying something home. */
    protected SectorEntityToken getIdleDrone() {
        for (SectorEntityToken drone : drones) {
            FishingDroneEntityPlugin plugin = getPlugin(drone);
            if (plugin != null && plugin.isOrbiting()) return drone;
        }

        return null;
    }

    /** Sends every drone home. They despawn as they arrive, and the script ends with the last one. */
    public void recall() {
        recalling = true;

        for (SectorEntityToken drone : drones) {
            FishingDroneEntityPlugin plugin = getPlugin(drone);
            if (plugin != null && !plugin.isReturning()) plugin.recall(null);
        }
    }

    protected void dropExpiredDrones() {
        for (SectorEntityToken drone : new ArrayList<>(drones)) {
            if (drone.isExpired() || !drone.isAlive()) drones.remove(drone);
        }
    }

    protected FishingDroneEntityPlugin getPlugin(SectorEntityToken drone) {
        if (drone == null || !(drone.getCustomPlugin() instanceof FishingDroneEntityPlugin)) return null;

        return (FishingDroneEntityPlugin) drone.getCustomPlugin();
    }

    public List<SectorEntityToken> getDrones() {
        return drones;
    }

    public Vector2f getTarget() {
        return target;
    }
}
