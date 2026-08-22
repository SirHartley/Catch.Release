package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;


public class CoreFisherSpawner implements EveryFrameScript {

    protected final IntervalUtil interval =
            new IntervalUtil(FishermanConstants.CORE_CHECK_DAYS * 0.8f,
                    FishermanConstants.CORE_CHECK_DAYS * 1.2f);


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


    @Override
    public void advance(float amount) {
        boolean arrived = hasJustArrived();

        interval.advance(Global.getSector().getClock().convertToDays(amount));
        if (!interval.intervalElapsed() && swept && !arrived) return;

        swept = true;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!OuterReaches.isPopulated(system)) continue;

            if (catchrelease.campaign.fish.FishingTaboo.holds(system)) continue;

            FishermanSpawner.reconcileSystem(system);

            if (getAnyBoat(system) != null) continue;

            post(system);
        }
    }

    protected transient boolean swept = false;


    protected transient com.fs.starfarer.api.campaign.LocationAPI lastLocation;
    protected transient boolean placed = false;


    protected boolean hasJustArrived() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        com.fs.starfarer.api.campaign.LocationAPI where = player.getContainingLocation();

        if (!placed) {
            placed = true;
            lastLocation = where;
            return false;
        }

        if (where == lastLocation) return false;

        lastLocation = where;

        return true;
    }


    public static CampaignFleetAPI getBoat(StarSystemAPI system) {
        if (system == null) return null;

        for (CampaignFleetAPI fleet : FishermanSpawner.getLiveFishermen(system)) {
            if (FishermanSpawner.isVisiting(fleet)) continue;
            return fleet;
        }

        return null;
    }


    public static CampaignFleetAPI ensureBoat(StarSystemAPI system) {
        FishermanSpawner.reconcileSystem(system);
        CampaignFleetAPI existing = getAnyBoat(system);

        return existing != null ? existing : post(system);
    }


    public static CampaignFleetAPI getAnyBoat(StarSystemAPI system) {
        if (system == null) return null;

        return FishermanSpawner.chooseSystemBoat(system,
                FishermanSpawner.getLiveFishermen(system));
    }


    protected static CampaignFleetAPI post(StarSystemAPI system) {
        FishermanSpawner.reconcileSystem(system);
        CampaignFleetAPI existing = getAnyBoat(system);
        if (existing != null) return existing;

        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(
                FishermanConstants.FACTION, FishermanConstants.FLEET_NAME, true);

        for (String variant : FishermanConstants.CORE_SHIPS) {
            fleet.getFleetData().addFleetMember(
                    Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant));
        }

        fleet.getFleetData().sort();
        fleet.forceSync();
        fleet.setTransponderOn(true);

        fleet.getMemoryWithoutUpdate().set(FishermanConstants.FLEET_FLAG, true);
        fleet.getMemoryWithoutUpdate().set(FishermanConstants.SHARED_SHELF_FLAG, true);

        // the same man at the wheel of this one too, which is the part nobody is meant to explain
        FishermanIdentity.crew(fleet);

        system.addEntity(fleet);

        Vector2f at = MathUtils.getPointOnCircumference(OuterReaches.center(system),
                MathUtils.getRandomNumberInRange(OuterReaches.getInnerLimit(system),
                        OuterReaches.getOuterLimit(system)),
                MathUtils.getRandomNumberInRange(0f, 360f));

        fleet.setLocation(at.x, at.y);

        fleet.addScript(new CoreFisherBehavior(fleet));

        return fleet;
    }
}
