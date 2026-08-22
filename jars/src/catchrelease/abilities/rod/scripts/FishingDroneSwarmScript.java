package catchrelease.abilities.rod.scripts;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.abilities.rod.entities.FishingDroneEntityPlugin;
import catchrelease.abilities.rod.rendering.FishingDroneDebugRenderer;
import catchrelease.abilities.rod.rendering.FishingRingRenderer;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
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


public class FishingDroneSwarmScript implements EveryFrameScript {

    protected SectorEntityToken pond;
    protected Vector2f target;

    protected List<SectorEntityToken> drones = new ArrayList<>();


    protected int plannedDroneCount = 0;
    protected int nextDroneIndex = 0;
    protected float nextDroneLaunchIn = 0f;
    protected IntervalUtil searchInterval = new IntervalUtil(RodConstants.DRONE_SEARCH_INTERVAL, RodConstants.DRONE_SEARCH_INTERVAL);


    protected Set<String> handled = new HashSet<>();

    protected boolean recalling = false;
    protected boolean done = false;


    protected int recallCount = 0;


    protected boolean moteInRing = false;


    public static FishingDroneSwarmScript dispatch(SectorEntityToken pond, Vector2f target) {
        return launch(new FishingDroneSwarmScript(pond, target));
    }


    protected static <T extends FishingDroneSwarmScript> T launch(T script) {
        recallExisting();

        script.beginDroneLaunches();

        Global.getSector().addScript(script);

        LunaCampaignRenderer.addRenderer(new FishingRingRenderer(script));

        // dev mode: shows each drone's intended position, so flight bugs are visible rather than inferred from how it looks
        if (Global.getSettings().isDevMode()) {
            LunaCampaignRenderer.addRenderer(new FishingDroneDebugRenderer(script));
        }

        return script;
    }


    public static void recallExisting() {
        for (EveryFrameScript script : new ArrayList<>(Global.getSector().getScripts())) {
            if (script instanceof FishingDroneSwarmScript) {
                ((FishingDroneSwarmScript) script).recall();
            }
        }
    }


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


    protected void beginDroneLaunches() {
        plannedDroneCount = getDroneCount();
        nextDroneIndex = 0;
        nextDroneLaunchIn = 0f;

        launchNextDrone();
        nextDroneLaunchIn = Math.max(0f, RodConstants.DRONE_LAUNCH_OFFSET);
    }


    protected boolean launchNextDrone() {
        if (!hasPendingDrones()) return false;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getContainingLocation() == null) {
            plannedDroneCount = nextDroneIndex;
            return false;
        }

        float slotAngle = 360f / plannedDroneCount * nextDroneIndex;

        SectorEntityToken drone = fleet.getContainingLocation().addCustomEntity(
                Misc.genUID(), null, FishingDroneEntityPlugin.ENTITY_ID, null,
                createDroneParams(slotAngle));

        drone.setLocation(fleet.getLocation().x, fleet.getLocation().y);
        drones.add(drone);
        nextDroneIndex++;

        Global.getSoundPlayer().playSound(RodConstants.SOUND_DRONE_LAUNCH, 1f, 1f,
                drone.getLocation(), drone.getVelocity());

        return true;
    }


    protected void advanceDroneLaunches(float amount) {
        if (!hasPendingDrones()) return;

        nextDroneLaunchIn -= amount;
        if (nextDroneLaunchIn > 0f) return;

        if (launchNextDrone()) {
            nextDroneLaunchIn += Math.max(0f, RodConstants.DRONE_LAUNCH_OFFSET);
        }
    }

    protected boolean hasPendingDrones() {
        return nextDroneIndex < plannedDroneCount;
    }

    protected FishingDroneEntityPlugin.Params createDroneParams(float slotAngle) {
        return new FishingDroneEntityPlugin.Params(target, slotAngle, RodConstants.DRONE_COLOR);
    }

    public static int getDroneCount() {
        return Math.max(1, Math.round(UpgradeManager.getValue(
                StatIds.FISHING_DRONE_COUNT, RodConstants.DRONE_COUNT_FALLBACK)));
    }


    public static float getRingRadius() {
        return UpgradeManager.getValue(StatIds.DRONE_CATCH_AREA, RodConstants.RING_RADIUS_FALLBACK);
    }


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

        if (!recalling && shouldRecall()) {
            recall();
            return;
        }

        if (recalling) {
            if (drones.isEmpty()) done = true;
            return;
        }

        advanceDroneLaunches(amount);

        // an empty live list is not the end while upgraded drones are still queued
        if (drones.isEmpty() && !hasPendingDrones()) {
            done = true;
            return;
        }

        // checked every frame so a caught-up mote is not left waiting a tick
        checkChasers();

        searchInterval.advance(amount);
        if (searchInterval.intervalElapsed()) lookForCatch();
    }


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


    protected void lookForCatch() {
        if (getSearchCenter() == null) return;

        moteInRing = false;

        List<SectorEntityToken> candidates = new ArrayList<>();

        for (SectorEntityToken mote : getSearchArea()) {
            if (!isCatchable(mote)) continue;

            moteInRing = true;
            if (isTaken(mote)) continue;

            candidates.add(mote);
        }

        sortByPriority(candidates);

        for (SectorEntityToken mote : candidates) {
            SectorEntityToken drone = getClosestIdleDrone(mote);
            if (drone == null) return;

            getPlugin(drone).chase(mote);
        }
    }


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
        FishSpec spec = null;

        if (mote.getCustomPlugin() instanceof FishEntityPlugin) {
            spec = ((FishEntityPlugin) mote.getCustomPlugin()).getFishSpec();
        } else if (mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin) {
            spec = ((BuriedMoteEntityPlugin) mote.getCustomPlugin()).getFishSpec();
        }

        return spec == null ? 0 : spec.rarity.rank;
    }


    public Vector2f getSearchCenter() {
        return pond == null ? null : target;
    }


    protected float getReach() {
        return getRingRadius() + getChaseMargin();
    }


    public float getRingDrawRadius() {
        return getRingRadius();
    }


    public float getPatrolRadius() {
        return RodConstants.DRONE_ORBIT_RADIUS;
    }

    protected List<SectorEntityToken> getSearchArea() {
        if (pond == null) return new ArrayList<>();

        return pond.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG);
    }


    protected boolean isReachable(SectorEntityToken mote) {
        return FishEntityPlugin.isAvailable(mote);
    }


    protected boolean isCatchable(SectorEntityToken mote) {
        if (mote.isExpired() || !mote.isAlive()) return false;
        if (handled.contains(mote.getId())) return false;

        if (!isReachable(mote)) return false;

        Vector2f center = getSearchCenter();
        if (center == null) return false;

        return Misc.getDistance(mote.getLocation(), center) <= getReach();
    }

    protected boolean isTaken(SectorEntityToken mote) {
        for (SectorEntityToken drone : drones) {
            FishingDroneEntityPlugin plugin = getPlugin(drone);
            if (plugin != null && plugin.getChaseTarget() == mote) return true;
        }

        return false;
    }

    protected void checkChasers() {
        Vector2f center = getSearchCenter();

        for (SectorEntityToken drone : new ArrayList<>(drones)) {
            FishingDroneEntityPlugin plugin = getPlugin(drone);
            if (plugin == null || !plugin.isChasing()) continue;

            SectorEntityToken mote = plugin.getChaseTarget();
            if (mote == null) continue;

            if (!isReachable(mote)) {
                plugin.returnToOrbit();
                continue;
            }

            // measured against reach, not the tighter patrol orbit, or every chase past that inner circle would be called off immediately
            if (center == null || Misc.getDistance(mote.getLocation(), center) > getReach()) {
                plugin.returnToOrbit();
                continue;
            }

            if (Misc.getDistance(drone.getLocation(), mote.getLocation()) <= RodConstants.DRONE_CATCH_DISTANCE) {
                onMoteReached(drone, mote);
            }
        }
    }


    protected void onMoteReached(final SectorEntityToken drone, final SectorEntityToken mote) {
        FishEntityPlugin plugin = mote.getCustomPlugin() instanceof FishEntityPlugin
                ? (FishEntityPlugin) mote.getCustomPlugin()
                : null;

        FishSpec fish = plugin == null ? null : plugin.getFishSpec();

        if (fish == null) {
            playMoteHit(mote);
            handled.add(mote.getId());
            getPlugin(drone).recall(mote);
            return;
        }

        boolean opened = FishingMinigameDialogPlugin.open(getCatchAnchor(mote), mote, fish,
                FishLogEntry.Method.DRONE, new FishingMinigameDialogPlugin.Callback() {
            @Override
            public void onCatchResolved(FishCatch landed) {
                if (landed != null) FishItems.addToPlayerCargo(landed);

                resolveCatch(drone, mote, landed != null);
            }
        });

        // the UI was busy - the drone stays on the mote and it comes round again next tick
        if (!opened) return;

        playMoteHit(mote);
        handled.add(mote.getId());
    }


    protected void playMoteHit(SectorEntityToken mote) {
        Global.getSoundPlayer().playSound(RodConstants.SOUND_MOTE_CAUGHT, 1f, 1f,
                mote.getLocation(), mote.getVelocity());
    }


    protected SectorEntityToken getCatchAnchor(SectorEntityToken mote) {
        return pond == null ? mote : pond;
    }


    protected void resolveCatch(SectorEntityToken drone, SectorEntityToken mote, boolean caught) {
        FishingDroneEntityPlugin plugin = getPlugin(drone);
        if (plugin == null) return;

        plugin.recall(caught ? mote : null);

        if (!caught && !mote.isExpired()) Misc.fadeAndExpire(mote, 1f);

        Global.getLogger(FishingDroneSwarmScript.class).info(caught ? "Landed a catch" : "The fish got away");
    }

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


    public void recall() {
        if (recalling) return;

        recalling = true;

        // nothing new may leave after a recall, whether it was manual or automatic
        plannedDroneCount = nextDroneIndex;
        recallCount = drones.size();

        for (SectorEntityToken drone : drones) {
            FishingDroneEntityPlugin plugin = getPlugin(drone);
            if (plugin != null && !plugin.isReturning()) plugin.recall(null);
        }
    }


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


    public boolean hasRecallableDrones() {
        if (hasPendingDrones()) return true;

        for (SectorEntityToken drone : drones) {
            FishingDroneEntityPlugin plugin = getPlugin(drone);
            if (plugin != null && !plugin.isReturning()) return true;
        }

        return false;
    }

    public boolean isMoteInRing() {
        return moteInRing;
    }

    public Vector2f getTarget() {
        return target;
    }
}
