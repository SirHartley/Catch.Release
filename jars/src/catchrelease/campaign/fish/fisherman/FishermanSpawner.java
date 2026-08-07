package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.SectorRegion;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;


/**
 * Puts the Fisherman in the sky: a wandering independent boat that fishes the player's system
 * and serves as an upgrade and trade stop while it does. It stays a fortnight of the player's
 * absence rather than a fortnight outright - see FishermanBehavior.
 * <p>
 * Spawning is a daily roll in whatever system the player is standing in, leaned on by a hold
 * full of fish and by the boat not having come by in a couple of months. It never spawns in
 * hyperspace, in systems cut off from it, in the abyss, or in special or hand-made systems - and
 * never in an inhabited one, which has a trawler posted to it already (see
 * {@link CoreFisherSpawner}). Of what is left it would rather work the rim than the sector's
 * middle. One boat at a time, sector-wide, and always the same one - see {@link FishermanIdentity}.
 */
public class FishermanSpawner implements EveryFrameScript {

    /** Registered every load; transient, so a save never carries the watcher. */
    public static void register() {
        Global.getSector().addTransientScript(new FishermanSpawner());
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;

        LocationAPI where = player.getContainingLocation();

        //the first tick of a load is not an arrival - it is wherever the save was left
        if (!placed) {
            placed = true;
            lastLocation = where;
            return;
        }

        if (where == lastLocation) return;
        lastLocation = where;

        if (!(where instanceof StarSystemAPI)) return;
        StarSystemAPI system = (StarSystemAPI) where;

        //going back to the core is going back to where he is not: the month starts again, so a
        //player who resupplies mid-search does not walk straight back out into him
        SectorRegion at = SectorRegion.of(system);
        if (at != null && at.isCore()) {
            stamp();
            return;
        }

        if (getLiveFleet() != null) return;
        if (!isEligible(system)) return;
        if (!isOverdue()) return;

        if (MathUtils.getRandomNumberInRange(0f, 1f) > FishermanConstants.SPAWN_ENTRY_CHANCE) return;

        spawn(system, player.getLocation());
    }

    /** Where the player was last time this looked, so an arrival can be told from standing still. */
    protected transient LocationAPI lastLocation;
    protected transient boolean placed = false;

    /** Whether the month since he was last seen is up. Never seen at all counts as up. */
    protected boolean isOverdue() {
        Object last = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.LAST_SEEN_KEY);

        if (!(last instanceof Long)) return true;

        return Global.getSector().getClock().getElapsedDaysSince((Long) last)
                >= FishermanConstants.SPAWN_COOLDOWN_DAYS;
    }

    /** Restarts the month. Written on arrival as well as departure - meeting him counts as seeing
     *  him, and without this the same arrival could be rolled again the moment he left. */
    protected void stamp() {
        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.LAST_SEEN_KEY,
                Global.getSector().getClock().getTimestamp());
    }

    /** The live boat if there is one anywhere, cleaned out of memory the moment it is not. */
    protected CampaignFleetAPI getLiveFleet() {
        Object stored = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.ACTIVE_KEY);

        if (stored instanceof CampaignFleetAPI) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) stored;
            if (!fleet.isExpired() && fleet.isAlive()) return fleet;
        }

        Global.getSector().getMemoryWithoutUpdate().unset(FishermanConstants.ACTIVE_KEY);

        return null;
    }

    /** Where the boat will and will not work. */
    protected boolean isEligible(StarSystemAPI system) {
        if (!system.isProcgen()) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
        if (system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) return false;

        //an inhabited system already has a boat posted to it, and two sellers in one place would
        //make the arrival nothing - the point of it is being the only one out here
        if (OuterReaches.isPopulated(system)) return false;

        return true;
    }


    /** The boat itself: one cruiser, a few logistics hulls, lamps and manners fitted by script. */
    protected void spawn(StarSystemAPI system, Vector2f near) {
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(
                FishermanConstants.FACTION, FishermanConstants.FLEET_NAME, true);

        for (String variant : FishermanConstants.SHIPS) {
            fleet.getFleetData().addFleetMember(
                    Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant));
        }

        fleet.getFleetData().sort();
        fleet.forceSync();
        fleet.setTransponderOn(true);
        fleet.getMemoryWithoutUpdate().set(FishermanConstants.FLEET_FLAG, true);
        fleet.getMemoryWithoutUpdate().set(FishermanConstants.VISITING_FLAG, true);

        //the same man at the wheel every time - see FishermanIdentity
        FishermanIdentity.crew(fleet);

        //beyond anything's sensors, same as the quest fleets: it arrives, it does not appear
        float distance = Math.max(FishermanConstants.SPAWN_DISTANCE_MIN,
                Global.getSettings().getSensorRangeMax() * 1.3f)
                + MathUtils.getRandomNumberInRange(0f, FishermanConstants.SPAWN_DISTANCE_SPREAD);

        Vector2f at = MathUtils.getPointOnCircumference(near, distance,
                MathUtils.getRandomNumberInRange(0f, 360f));

        system.addEntity(fleet);
        fleet.setLocation(at.x, at.y);

        //the wander is vanilla's patrol; the two weeks and the leaving belong to the behaviour
        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, system.getCenter(),
                FishermanConstants.STAY_DAYS + 2f, "fishing the deep");

        fleet.addScript(new FishermanBehavior(fleet));

        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.ACTIVE_KEY, fleet);

        stamp();
    }

    /** Whether a fleet is one of the trade's boats at all, for anything that routes on it. */
    public static boolean isFisherman(CampaignFleetAPI fleet) {
        return fleet != null
                && fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.FLEET_FLAG);
    }

    /**
     * Whether this boat is only passing through, rather than one of the standing ones.
     * <p>
     * A question about the schedule and nothing else. It is the same person on every one.
     */
    public static boolean isVisiting(CampaignFleetAPI fleet) {
        return fleet != null
                && fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.VISITING_FLAG);
    }
}
