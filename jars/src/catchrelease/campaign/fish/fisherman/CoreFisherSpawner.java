package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * One trawler to every inhabited system, kept there.
 * <p>
 * The wanderer is an event - he turns up, he is worth going to find, and he is gone. That is a poor
 * shape for the only place to buy a chart, because a player who wants one has nothing to do but wait
 * and travel. The core's boats are the other half of that: always somewhere, never far, and all
 * selling off the one shelf, so the trade reads as a trade rather than as a series of lucky
 * encounters. What is left worth crossing the sector for is the wanderer's own stock and his
 * rumors - see {@link FishermanShelf} and {@link FishRumors}.
 * <p>
 * They are posted rather than spawned: the sweep re-posts a system that has lost its boat, so one
 * killed in a fight is replaced in a week rather than leaving a hole for the rest of the campaign.
 */
public class CoreFisherSpawner implements EveryFrameScript {

    protected final IntervalUtil interval =
            new IntervalUtil(FishermanConstants.CORE_CHECK_DAYS * 0.8f,
                    FishermanConstants.CORE_CHECK_DAYS * 1.2f);

    /** Registered every load; transient, so a save never carries the watcher. */
    public static void register() {
        Global.getSector().addTransientScript(new CoreFisherSpawner());
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    /** Swept on the first tick after a load as well as weekly, so a new campaign posts them at once. */
    @Override
    public void advance(float amount) {
        interval.advance(Global.getSector().getClock().convertToDays(amount));
        if (!interval.intervalElapsed() && swept) return;

        swept = true;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!OuterReaches.isPopulated(system)) continue;
            if (getBoat(system) != null) continue;

            post(system);
        }
    }

    protected transient boolean swept = false;

    /** The system's own trawler, if it still has one. */
    public static CampaignFleetAPI getBoat(StarSystemAPI system) {
        if (system == null) return null;

        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (!FishermanSpawner.isFisherman(fleet)) continue;
            if (FishermanSpawner.isWanderer(fleet)) continue;

            if (!fleet.isExpired() && fleet.isAlive()) return fleet;
        }

        return null;
    }

    /**
     * A small working boat, started somewhere in its own band.
     * <p>
     * Placed out in the reaches from the first frame rather than at the edge and told to travel -
     * these are not arriving from anywhere, they have been out there the whole time, and a fleet
     * flying in from the rim on the first day of the campaign says the opposite.
     */
    protected void post(StarSystemAPI system) {
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(
                FishermanConstants.FACTION, FishermanConstants.CORE_FLEET_NAME, true);

        for (String variant : FishermanConstants.CORE_SHIPS) {
            fleet.getFleetData().addFleetMember(
                    Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant));
        }

        fleet.getFleetData().sort();
        fleet.forceSync();
        fleet.setTransponderOn(true);

        fleet.getMemoryWithoutUpdate().set(FishermanConstants.FLEET_FLAG, true);
        fleet.getMemoryWithoutUpdate().set(FishermanConstants.SHARED_SHELF_FLAG, true);

        system.addEntity(fleet);

        Vector2f at = MathUtils.getPointOnCircumference(OuterReaches.center(system),
                MathUtils.getRandomNumberInRange(OuterReaches.getInnerLimit(system),
                        OuterReaches.getOuterLimit(system)),
                MathUtils.getRandomNumberInRange(0f, 360f));

        fleet.setLocation(at.x, at.y);

        fleet.addScript(new CoreFisherBehavior(fleet));
    }
}
