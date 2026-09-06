package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class CoreFisherSpawner implements EveryFrameScript {

    protected final IntervalUtil interval =
            new IntervalUtil(FishermanConstants.CORE_CHECK_DAYS * 0.8f,
                    FishermanConstants.CORE_CHECK_DAYS * 1.2f);
    protected transient boolean swept = false;
    protected transient com.fs.starfarer.api.campaign.LocationAPI lastLocation;
    protected transient boolean placed = false;

    public static void register() {
        CoreFisherSpawner spawner = new CoreFisherSpawner();
        spawner.prepareMarkers();
        Global.getSector().addTransientScript(spawner);
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
            if (!isEligible(system) && FishermanMapIcon.findStanding(system) == null) continue;

            FishermanSpawner.reconcileSystem(system);
            CampaignFleetAPI boat = getBoat(system);
            prepareMarker(system, boat);

            if (system.isCurrentLocation()) {
                if (getAnyBoat(system) == null) post(system);
            } else if (boat != null && boat.getBattle() == null
                    && !boat.getMemoryWithoutUpdate().contains(FishermanConstants.TUTORIAL_TARGET_KEY)) {
                unload(boat);
            }
        }
    }

    protected void prepareMarkers() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!isEligible(system)) continue;

            FishermanSpawner.reconcileSystem(system);
            prepareMarker(system, getBoat(system));
        }
    }

    protected static void prepareMarker(StarSystemAPI system, CampaignFleetAPI boat) {
        if (system.hasTag(Tags.THEME_CORE) || system.isCurrentLocation()
                || FishermanMapIcon.findStanding(system) != null
                || (boat != null && system.isEnteredByPlayer())) {
            FishermanMapIcon.findOrAddStanding(system, boat);
        }
    }

    public static boolean isEligible(StarSystemAPI system) {
        return system != null && OuterReaches.isPopulated(system)
                && !catchrelease.campaign.fish.FishingTaboo.holds(system);
    }

    public static boolean isStanding(CampaignFleetAPI fleet) {
        return FishermanSpawner.isLiveFisherman(fleet) && !FishermanSpawner.isVisiting(fleet)
                && !fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.TUTORIAL_TEMPORARY_KEY)
                && fleet.getContainingLocation() instanceof StarSystemAPI system
                && (isEligible(system) || FishermanMapIcon.findStanding(system) != null);
    }

    protected static void unload(CampaignFleetAPI fleet) {
        SectorEntityToken marker = FishermanMapIcon.findStanding(fleet.getStarSystem());
        if (marker != null && Global.getSector().getCampaignUI().getUltimateCourseTarget() == fleet) {
            Global.getSector().getCampaignUI().layInCourseForNextStep(marker);
        }
        FishermanMapIcon.detachStanding(fleet);
        for (EveryFrameScript script : fleet.getScripts()) {
            if (script instanceof FishermanBehavior behavior) behavior.unload();
        }
        fleet.despawn();
    }

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

        FishermanIdentity.crew(fleet);

        system.addEntity(fleet);

        SectorEntityToken marker = FishermanMapIcon.findStanding(system);
        Vector2f at = marker == null ? pickLocation(system) : new Vector2f(marker.getLocation());

        fleet.setLocation(at.x, at.y);
        if (isStanding(fleet)) FishermanMapIcon.findOrAddStanding(system, fleet);

        CoreFisherBehavior behavior = new CoreFisherBehavior(fleet);
        behavior.keepWorking();
        fleet.addScript(behavior);

        return fleet;
    }

    protected static Vector2f pickLocation(StarSystemAPI system) {
        return MathUtils.getPointOnCircumference(OuterReaches.center(system),
                MathUtils.getRandomNumberInRange(OuterReaches.getInnerLimit(system),
                        OuterReaches.getOuterLimit(system)),
                MathUtils.getRandomNumberInRange(0f, 360f));
    }
}
