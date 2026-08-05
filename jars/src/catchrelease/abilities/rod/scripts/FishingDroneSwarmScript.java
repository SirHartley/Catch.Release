package catchrelease.abilities.rod.scripts;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.abilities.rod.entities.FishingDroneEntityPlugin;
import catchrelease.abilities.rod.rendering.FishingDroneDebugRenderer;
import catchrelease.abilities.rod.rendering.FishingRingRenderer;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.minigame.FishingMinigameDialogPlugin;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /** Motes already dealt with, so a drone does not intercept the same one over and over. */
    protected Set<String> handled = new HashSet<>();

    protected boolean recalling = false;
    protected boolean done = false;

    /** How many drones were still out when the recall started, so the trip home can be measured. */
    protected int recallCount = 0;

    /** Whether anything catchable is currently inside the ring - drives the ring's own brightening. */
    protected boolean moteInRing = false;

    /**
     * Sends a swarm to a spot, recalling whatever was already out there. The drone count is the
     * {@link StatIds#FISHING_DRONE_COUNT} upgrade.
     */
    public static FishingDroneSwarmScript dispatch(SectorEntityToken pond, Vector2f target) {
        recallExisting();

        FishingDroneSwarmScript script = new FishingDroneSwarmScript(pond, target);
        script.spawnDrones();

        Global.getSector().addScript(script);

        //shows where a mote has to drift to for the swarm to do anything about it
        LunaCampaignRenderer.addRenderer(new FishingRingRenderer(script));

        //and, for us, where each drone thinks it should be, so flight problems are visible rather
        //than inferred from how it looks
        if (Global.getSettings().isDevMode()) {
            LunaCampaignRenderer.addRenderer(new FishingDroneDebugRenderer(script));
        }

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

    /**
     * The swarm currently out, or null if the rod is idle.
     * <p>
     * The sector's own script list is the register - a swarm retires itself once the last drone is
     * home, so anything still in there is still out there.
     */
    public static FishingDroneSwarmScript getExisting() {
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (!(script instanceof FishingDroneSwarmScript)) continue;

            FishingDroneSwarmScript swarm = (FishingDroneSwarmScript) script;
            if (!swarm.isDone()) return swarm;
        }

        return null;
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
        return Math.max(1, Math.round(UpgradeManager.getValue(
                StatIds.FISHING_DRONE_COUNT, RodConstants.DRONE_COUNT_FALLBACK)));
    }

    /**
     * How far out the swarm will fish. The ring the player sees is drawn from the same number, so
     * buying a bigger one is visible before anything is caught with it.
     */
    public static float getRingRadius() {
        return UpgradeManager.getValue(StatIds.DRONE_CATCH_AREA, RodConstants.RING_RADIUS_FALLBACK);
    }

    /**
     * How far past the ring a drone will notice something, and follow it.
     * <p>
     * Not zero by default any more. A ring that was a hard boundary meant a drone only moved once a
     * mote was already inside it, and by then the mote is drifting across ground the drone still has
     * to cover - the swarm read as slow because it was always starting late. A little reach past the
     * line lets one set off to meet a mote on its way in.
     * <p>
     * Buying it up widens that reach, and lets a drone finish a chase that leaves the ring instead
     * of turning back on the line.
     */
    public static float getChaseMargin() {
        return UpgradeManager.getValue(StatIds.DRONE_CHASE_MARGIN,
                RodConstants.CHASE_MARGIN_FALLBACK);
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

        //chasers are checked every frame - a mote it has run down should not sit there for a tick
        checkChasers();

        searchInterval.advance(amount);
        if (searchInterval.intervalElapsed()) lookForCatch();
    }

    /** The trip is over once the player leaves the pond behind, or the pond closes under them. */
    protected boolean shouldRecall() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || pond == null) return true;

        if (!pond.isInCurrentLocation()) return true;
        if (fleet.getContainingLocation() != pond.getContainingLocation()) return true;

        MaskedFishingPondTerrainPlugin plugin = MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
        if (plugin != null && !plugin.isActive()) return true;

        return Misc.getDistance(fleet.getLocation(), pond.getLocation())
                > pond.getRadius() * PondConstants.POND_INTERACT_RANGE_MULT;
    }

    /**
     * Sends idle drones after any mote inside the ring. Motes drift, so mostly there is nothing to
     * send anyone after and the swarm just keeps circling until one wanders in.
     */
    protected void lookForCatch() {
        if (pond == null || target == null) return;

        moteInRing = false;

        List<SectorEntityToken> candidates = new ArrayList<>();

        for (SectorEntityToken mote : pond.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (!isCatchable(mote)) continue;

            moteInRing = true;
            if (isTaken(mote)) continue;

            candidates.add(mote);
        }

        //rarest first, if the rig has been taught to care. Without the upgrade the order is whatever
        //the location handed back, which is what it always was
        sortByPriority(candidates);

        for (SectorEntityToken mote : candidates) {
            SectorEntityToken drone = getClosestIdleDrone(mote);
            if (drone == null) return; //everyone is busy, the rest can wait for a free one

            getPlugin(drone).chase(mote);
        }
    }

    /**
     * Puts the rarest first, in proportion to how much the rig has been taught to care.
     * <p>
     * At zero the order is left alone, which is what it always was. Bought up, a swarm with one free
     * drone and two motes in the ring sends it after the better one rather than the nearer one.
     */
    protected void sortByPriority(List<SectorEntityToken> motes) {
        final float priority = UpgradeManager.getValue(StatIds.DRONE_RARE_PRIORITY, 0f);
        if (priority <= 0f || motes.size() < 2) return;

        motes.sort(new java.util.Comparator<SectorEntityToken>() {
            @Override
            public int compare(SectorEntityToken a, SectorEntityToken b) {
                return Integer.compare(getRarityOrdinal(b), getRarityOrdinal(a));
            }
        });
    }

    protected static int getRarityOrdinal(SectorEntityToken mote) {
        if (!(mote.getCustomPlugin() instanceof FishEntityPlugin)) return 0;

        FishSpec spec = ((FishEntityPlugin) mote.getCustomPlugin()).getFishSpec();

        return spec == null ? 0 : spec.rarity.ordinal();
    }

    /** A mote is worth going after while it is alive, inside the ring, and not already dealt with. */
    protected boolean isCatchable(SectorEntityToken mote) {
        if (mote.isExpired() || !mote.isAlive()) return false;
        if (handled.contains(mote.getId())) return false;

        //under the fabric, where a drone has nothing to close on. It comes back up in a moment and
        //is picked up again then, which is the whole of what a dive costs the drones
        if (!FishEntityPlugin.isAvailable(mote)) return false;

        //the ring, plus however far past it this rig will follow something
        return Misc.getDistance(mote.getLocation(), target) <= getRingRadius() + getChaseMargin();
    }

    /** Whether some drone is already on this one. */
    protected boolean isTaken(SectorEntityToken mote) {
        for (SectorEntityToken drone : drones) {
            FishingDroneEntityPlugin plugin = getPlugin(drone);
            if (plugin != null && plugin.getChaseTarget() == mote) return true;
        }

        return false;
    }

    /** Watches the drones that are running something down, and calls it once one catches up. */
    protected void checkChasers() {
        for (SectorEntityToken drone : new ArrayList<>(drones)) {
            FishingDroneEntityPlugin plugin = getPlugin(drone);
            if (plugin == null || !plugin.isChasing()) continue;

            SectorEntityToken mote = plugin.getChaseTarget();
            if (mote == null) continue;

            //drifted back out of the ring - let it go rather than chasing it across the system.
            //Measured against the ring the swarm fishes, which is what sent the drone out in the
            //first place: tested against the orbit instead, every chase beyond that tight inner
            //circle was called off the frame after it began, so a mote that wandered in was picked
            //up and dropped rather than caught
            if (Misc.getDistance(mote.getLocation(), target) > getRingRadius() + getChaseMargin()) {
                plugin.returnToOrbit();
                continue;
            }

            if (Misc.getDistance(drone.getLocation(), mote.getLocation()) <= RodConstants.DRONE_CATCH_DISTANCE) {
                onMoteReached(drone, mote);
            }
        }
    }

    /**
     * A drone has caught up with a mote, so the catch begins. Opening the dialog pauses the campaign,
     * which stops this script and holds the drones still until it is done with.
     */
    protected void onMoteReached(final SectorEntityToken drone, final SectorEntityToken mote) {
        FishEntityPlugin plugin = mote.getCustomPlugin() instanceof FishEntityPlugin
                ? (FishEntityPlugin) mote.getCustomPlugin()
                : null;

        FishSpec fish = plugin == null ? null : plugin.getFishSpec();

        //nothing to play against - an unidentified mote is simply taken
        if (fish == null) {
            handled.add(mote.getId());
            getPlugin(drone).recall(mote);
            return;
        }

        boolean opened = FishingMinigameDialogPlugin.open(pond, fish, FishLogEntry.Method.DRONE, new FishingMinigameDialogPlugin.Callback() {
            @Override
            public void onCatchResolved(FishCatch landed) {
                if (landed != null) FishItems.addToPlayerCargo(landed);

                resolveCatch(drone, mote, landed != null);
            }
        });

        //the UI was busy - the drone stays on the mote and it comes round again next tick
        if (!opened) return;

        handled.add(mote.getId());
    }

    /**
     * The catch is over. A landed fish rides home on the drone that took it; one that got away takes
     * its mote with it. Either way that drone is done for this trip - the rest keep fishing.
     */
    protected void resolveCatch(SectorEntityToken drone, SectorEntityToken mote, boolean caught) {
        FishingDroneEntityPlugin plugin = getPlugin(drone);
        if (plugin == null) return;

        plugin.recall(caught ? mote : null);

        if (!caught && !mote.isExpired()) Misc.fadeAndExpire(mote, 1f);

        Global.getLogger(FishingDroneSwarmScript.class).info(caught ? "Landed a catch" : "The fish got away");
    }

    /** The free drone with least distance to cover to reach a given mote. */
    protected SectorEntityToken getClosestIdleDrone(SectorEntityToken mote) {
        SectorEntityToken closest = null;
        float closestDistance = Float.MAX_VALUE;

        for (SectorEntityToken drone : drones) {
            FishingDroneEntityPlugin plugin = getPlugin(drone);
            if (plugin == null || !plugin.isAvailable()) continue;

            float distance = Misc.getDistance(drone.getLocation(), mote.getLocation());
            if (distance >= closestDistance) continue;

            closest = drone;
            closestDistance = distance;
        }

        return closest;
    }

    /** Sends every drone home. They despawn as they arrive, and the script ends with the last one. */
    public void recall() {
        //a second call would restart the count and make the trip home look longer than it is
        if (recalling) return;

        recalling = true;
        recallCount = drones.size();

        for (SectorEntityToken drone : drones) {
            FishingDroneEntityPlugin plugin = getPlugin(drone);
            if (plugin != null && !plugin.isReturning()) plugin.recall(null);
        }
    }

    /**
     * How much of the trip home is done, 0 to 1, counted in drones rather than seconds.
     * <p>
     * There is no honest time to count down to - a drone that was chasing something across the ring
     * has further to come than one that never left its slot. Drones landed is the wait the player is
     * actually waiting on, and it moves every time one of them arrives.
     */
    public float getRecallProgress() {
        if (!recalling || recallCount <= 0) return 1f;

        return 1f - (float) drones.size() / (float) recallCount;
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

    public boolean isRecalling() {
        return recalling;
    }

    public boolean isMoteInRing() {
        return moteInRing;
    }

    public Vector2f getTarget() {
        return target;
    }
}
