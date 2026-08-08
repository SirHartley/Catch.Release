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

/**
 * One cast: sends drones to the aimed spot, keeps them circling while watching for a mote to drift
 * into the ring, and brings them home when the trip ends. Only one swarm is out at a time - a new
 * cast recalls the old one. Does not run while paused, so drones hold still during the minigame.
 * <p>
 * Four hooks define a trip: {@link #getSearchCenter()}, {@link #getSearchArea()},
 * {@link #isReachable(SectorEntityToken)}, {@link #shouldRecall()} - overridden by
 * {@link RoamingDroneSwarmScript} to fish without a pond.
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
        return launch(new FishingDroneSwarmScript(pond, target));
    }

    /**
     * Puts a swarm out: recalls anything already out, spawns it, registers it, and hangs its
     * renderers on it. The recall lives here rather than in each caller so a subclass cannot forget it.
     */
    protected static <T extends FishingDroneSwarmScript> T launch(T script) {
        recallExisting();

        script.spawnDrones();

        Global.getSector().addScript(script);

        LunaCampaignRenderer.addRenderer(new FishingRingRenderer(script));

        //dev mode: shows each drone's intended position, so flight bugs are visible rather than
        //inferred from how it looks
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

    /** The swarm currently out, or null if idle; a swarm retires itself once the last drone is home. */
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
                    createDroneParams(slotAngle));

            drone.setLocation(fleet.getLocation().x, fleet.getLocation().y);
            drones.add(drone);
        }
    }

    protected FishingDroneEntityPlugin.Params createDroneParams(float slotAngle) {
        return new FishingDroneEntityPlugin.Params(target, slotAngle, RodConstants.DRONE_COLOR);
    }

    public static int getDroneCount() {
        return Math.max(1, Math.round(UpgradeManager.getValue(
                StatIds.FISHING_DRONE_COUNT, RodConstants.DRONE_COUNT_FALLBACK)));
    }

    /** How far out the swarm will fish; the ring the player sees is drawn from the same number. */
    public static float getRingRadius() {
        return UpgradeManager.getValue(StatIds.DRONE_CATCH_AREA, RodConstants.RING_RADIUS_FALLBACK);
    }

    /** How far past the ring a drone will notice and chase something, letting it meet a mote
     * drifting in or finish a chase that carries past the line. */
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

        if (drones.isEmpty()) {
            done = true;
            return;
        }

        if (!recalling && shouldRecall()) {
            recall();
            return;
        }

        if (recalling) return;

        //checked every frame so a caught-up mote is not left waiting a tick
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

    /** Sends idle drones after any mote inside the ring. */
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
            if (drone == null) return; //everyone is busy, the rest can wait for a free one

            getPlugin(drone).chase(mote);
        }
    }

    /** Sorts candidates rarest-first when the priority upgrade is bought; at 0 the order is untouched. */
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

    /** Reads the species off either kind of mote, so the sort works on whatever is being fished. */
    protected static int getRarityOrdinal(SectorEntityToken mote) {
        FishSpec spec = null;

        if (mote.getCustomPlugin() instanceof FishEntityPlugin) {
            spec = ((FishEntityPlugin) mote.getCustomPlugin()).getFishSpec();
        } else if (mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin) {
            spec = ((BuriedMoteEntityPlugin) mote.getCustomPlugin()).getFishSpec();
        }

        return spec == null ? 0 : spec.rarity.ordinal();
    }

    /** The middle of the water being fished. Not always the cast point - a roaming swarm overrides
     * this to follow the fleet. Null means nothing to fish around this tick. */
    public Vector2f getSearchCenter() {
        return pond == null ? null : target;
    }

    /** Ring radius plus chase margin - the one number search and break-off are both measured against. */
    protected float getReach() {
        return getRingRadius() + getChaseMargin();
    }

    /** The circle drawn for the player - the ring, not the reach; the chase margin stays undrawn. */
    public float getRingDrawRadius() {
        return getRingRadius();
    }

    /** The circle the drones themselves fly, as opposed to the water they are fishing. */
    public float getPatrolRadius() {
        return RodConstants.DRONE_ORBIT_RADIUS;
    }

    protected List<SectorEntityToken> getSearchArea() {
        if (pond == null) return new ArrayList<>();

        return pond.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG);
    }

    /**
     * False while dived under the fabric; it resurfaces and can be picked up again then. Asked
     * for the whole of a chase and not only when one is picked, since what a drone can reach is
     * a thing that changes under it while it flies.
     */
    protected boolean isReachable(SectorEntityToken mote) {
        return FishEntityPlugin.isAvailable(mote);
    }

    /** A mote is worth going after while it is alive, inside the ring, and not already dealt with. */
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

            //break off on the same terms the chase was taken on. Reachability used to be asked
            //once, at pick-up, and never again - so a lamp mark that faded while the drone was in
            //the air, or a specimen that dived to shake the line, was still landed on contact.
            //Both are exactly the reach the harpoon's deep-strike head is sold for, and no rig
            //without it has any business having it
            if (!isReachable(mote)) {
                plugin.returnToOrbit();
                continue;
            }

            //measured against reach, not the tighter patrol orbit, or every chase past that inner
            //circle would be called off immediately
            if (center == null || Misc.getDistance(mote.getLocation(), center) > getReach()) {
                plugin.returnToOrbit();
                continue;
            }

            if (Misc.getDistance(drone.getLocation(), mote.getLocation()) <= RodConstants.DRONE_CATCH_DISTANCE) {
                onMoteReached(drone, mote);
            }
        }
    }

    /** Opens the catch minigame; the dialog pauses the campaign, holding the drones still until done. */
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

        boolean opened = FishingMinigameDialogPlugin.open(getCatchAnchor(mote), fish, FishLogEntry.Method.DRONE, new FishingMinigameDialogPlugin.Callback() {
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

    /** Drives the minigame's aberration/region colouring - the pond for a cast, the mote itself
     * when there is no pond (a roaming swarm). */
    protected SectorEntityToken getCatchAnchor(SectorEntityToken mote) {
        return pond == null ? mote : pond;
    }

    /** A landed fish rides home on its drone; one that got away takes its mote with it. Either way
     * that drone is done for this trip. */
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

    /** 0 to 1, counted in drones landed rather than time. */
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
