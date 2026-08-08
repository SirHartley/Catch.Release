package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.shop.FishCurrency;
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

import java.util.Map;


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

        //asked before the lock is spent: one boat at a time is a sector-wide rule, and a system
        //passed over because he is already out somewhere else has not had its question answered
        if (getLiveFleet() != null) return;
        if (!isEligible(system)) return;

        if (isLocked(system)) return;
        lock(system);

        if (MathUtils.getRandomNumberInRange(0f, 1f) > getChance(system)) return;

        spawn(system, player.getLocation());
    }

    /** Where the player was last time this looked, so an arrival can be told from standing still. */
    protected transient LocationAPI lastLocation;
    protected transient boolean placed = false;

    /**
     * Whether this system has already been asked recently.
     * <p>
     * Kept on the system rather than in a table of our own, with the month as the memory key's own
     * expiry - so the lock ages out by itself and nothing has to sweep for stale entries.
     */
    protected boolean isLocked(StarSystemAPI system) {
        return system.getMemoryWithoutUpdate().getBoolean(FishermanConstants.SPAWN_LOCK_KEY);
    }

    /** Spent on the roll, not on the result: a system that said no is answered for the month too,
     *  which is the whole point - jumping out and back must not be a re-roll. */
    protected void lock(StarSystemAPI system) {
        system.getMemoryWithoutUpdate().set(FishermanConstants.SPAWN_LOCK_KEY, true,
                FishermanConstants.SPAWN_LOCK_DAYS);
    }

    /**
     * The odds for one arrival: a small base, leaned on by a hold worth selling and by not having
     * seen him in a long while, and eased off inside the core where the fishing is not.
     */
    protected float getChance(StarSystemAPI system) {
        float chance = FishermanConstants.SPAWN_BASE_CHANCE;

        int aboard = 0;
        for (Map.Entry<catchrelease.campaign.fish.data.FishRarity, Integer> entry
                : FishCurrency.count().entrySet()) {
            aboard += entry.getValue();
        }
        if (aboard >= FishermanConstants.CARGO_FISH_THRESHOLD) {
            chance *= FishermanConstants.CARGO_FULL_MULT;
        }

        Object last = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.LAST_SEEN_KEY);
        boolean overdue = !(last instanceof Long)
                || Global.getSector().getClock().getElapsedDaysSince((Long) last)
                >= FishermanConstants.OVERDUE_DAYS;
        if (overdue) chance *= FishermanConstants.OVERDUE_MULT;

        SectorRegion at = SectorRegion.of(system);
        if (at != null && at.isCore()) chance *= FishermanConstants.CORE_SPAWN_MULT;

        return Math.min(1f, chance);
    }

    /** Restarts the "last seen" clock the overdue multiplier reads. Written on arrival as well as
     *  departure - meeting him counts as seeing him. */
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

        //every placement of a boat goes through the same gate, even one that cannot currently trip
        //it - this spawner only works uninhabited systems, where OuterReaches has no band to
        //enforce and hands the point straight back. Asking anyway is what keeps "a boat is only
        //ever put where the reaches allow" a rule rather than a coincidence of who calls what
        at = OuterReaches.place(system, at);

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
