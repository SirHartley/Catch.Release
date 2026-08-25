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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FishermanSpawner implements EveryFrameScript {

    protected transient LocationAPI lastLocation;
    protected transient boolean placed = false;

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

        // the first tick of a load is not an arrival - it is wherever the save was left
        if (!placed) {
            reconcileLegacyFleets();
            FishermanMapIcon.removeOutside(where);
            placed = true;
            lastLocation = where;
            return;
        }

        if (where == lastLocation) return;
        lastLocation = where;

        FishermanMapIcon.removeOutside(where);

        if (!(where instanceof StarSystemAPI)) return;
        StarSystemAPI system = (StarSystemAPI) where;

        reconcileSystem(system);
        if (CoreFisherSpawner.getAnyBoat(system) != null) return;

        if (getLiveFleet() != null) return;
        if (!isEligible(system)) return;

        if (isLocked(system)) return;
        lock(system);

        if (MathUtils.getRandomNumberInRange(0f, 1f) > getChance(system)) return;

        spawn(system, player.getLocation());
    }

    protected boolean isLocked(StarSystemAPI system) {
        return system.getMemoryWithoutUpdate().getBoolean(FishermanConstants.SPAWN_LOCK_KEY);
    }

    protected void lock(StarSystemAPI system) {
        system.getMemoryWithoutUpdate().set(FishermanConstants.SPAWN_LOCK_KEY, true,
                FishermanConstants.SPAWN_LOCK_DAYS);
    }

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

    protected void stamp() {
        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.LAST_SEEN_KEY,
                Global.getSector().getClock().getTimestamp());
    }

    protected CampaignFleetAPI getLiveFleet() {
        Object stored = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.ACTIVE_KEY);

        if (stored instanceof CampaignFleetAPI) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) stored;
            if (isLiveFisherman(fleet) && isVisiting(fleet)) return fleet;
        }

        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : location.getFleets()) {
                if (!isLiveFisherman(fleet) || !isVisiting(fleet)) continue;

                Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.ACTIVE_KEY, fleet);
                return fleet;
            }
        }

        Global.getSector().getMemoryWithoutUpdate().unset(FishermanConstants.ACTIVE_KEY);

        return null;
    }

    public static List<CampaignFleetAPI> getLiveFishermen(LocationAPI location) {
        List<CampaignFleetAPI> boats = new ArrayList<>();
        if (location == null) return boats;

        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (isLiveFisherman(fleet)) boats.add(fleet);
        }

        return boats;
    }

    public static boolean isLiveFisherman(CampaignFleetAPI fleet) {
        return isFisherman(fleet)
                && !fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.RETIRE_KEY)
                // the Longliner's boat talks like a fisherman but is never booked as one,
                // or reconciliation would retire it - or worse, retire the real boat
                && !catchrelease.campaign.fish.legendary.LonglinerDecoy.isDecoyBoat(fleet)
                && !fleet.isExpired() && fleet.isAlive();
    }

    public static void reconcileLegacyFleets() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            reconcileSystem(system);
        }

        CampaignFleetAPI canonical = null;
        Object stored = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.ACTIVE_KEY);
        if (stored instanceof CampaignFleetAPI fleet
                && isLiveFisherman(fleet) && isVisiting(fleet)) {
            canonical = fleet;
        }

        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : getLiveFishermen(location)) {
                if (!isVisiting(fleet)) continue;
                if (canonical == null) canonical = fleet;
                else if (fleet != canonical) retireDuplicate(fleet);
            }
        }

        if (canonical == null) {
            Global.getSector().getMemoryWithoutUpdate().unset(FishermanConstants.ACTIVE_KEY);
        } else {
            Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.ACTIVE_KEY, canonical);
        }
    }

    public static void reconcileSystem(StarSystemAPI system) {
        List<CampaignFleetAPI> boats = getLiveFishermen(system);
        if (boats.size() < 2) return;

        CampaignFleetAPI canonical = chooseSystemBoat(system, boats);
        for (CampaignFleetAPI boat : boats) {
            if (boat != canonical) retireDuplicate(boat);
        }
    }

    public static CampaignFleetAPI chooseSystemBoat(StarSystemAPI system,
                                                      List<CampaignFleetAPI> boats) {
        if (boats == null || boats.isEmpty()) return null;

        for (CampaignFleetAPI boat : boats) {
            Object reserved = boat.getMemoryWithoutUpdate().get(FishermanConstants.TUTORIAL_TARGET_KEY);
            if (system != null && system.getId().equals(reserved)) return boat;
        }

        boolean populated = system != null && OuterReaches.isPopulated(system);
        for (CampaignFleetAPI boat : boats) {
            if (isVisiting(boat) != populated) return boat;
        }

        return boats.get(0);
    }

    public static void retireDuplicate(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.RETIRE_KEY)) {
            return;
        }

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != fleet.getContainingLocation()) {
            fleet.despawn();
            return;
        }

        fleet.getMemoryWithoutUpdate().set(FishermanConstants.RETIRE_KEY, true);
    }

    protected boolean isEligible(StarSystemAPI system) {
        if (!system.isProcgen()) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
        if (system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) return false;

        if (OuterReaches.isPopulated(system)) return false;

        return true;
    }

    public static CampaignFleetAPI spawnNow(StarSystemAPI system, Vector2f near) {
        if (system == null || near == null || Global.getSector() == null) return null;

        reconcileSystem(system);
        CampaignFleetAPI local = CoreFisherSpawner.getAnyBoat(system);
        if (local != null) return local;

        FishermanSpawner spawner = new FishermanSpawner();
        CampaignFleetAPI visitor = spawner.getLiveFleet();
        if (visitor != null && visitor.getContainingLocation() != system) {
            FishermanShelf.releaseFor(visitor);
            FishermanMapIcon.removeFor(visitor);
            retireDuplicate(visitor);
        }

        FishermanMapIcon.removeOutside(system);
        return spawner.spawn(system, near);
    }

    protected CampaignFleetAPI spawn(StarSystemAPI system, Vector2f near) {
        reconcileSystem(system);
        CampaignFleetAPI local = CoreFisherSpawner.getAnyBoat(system);
        if (local != null) return local;

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

        // the same man at the wheel every time - see FishermanIdentity
        FishermanIdentity.crew(fleet);

        // beyond anything's sensors, same as the quest fleets: it arrives, it does not appear
        float distance = Math.max(FishermanConstants.SPAWN_DISTANCE_MIN,
                Global.getSettings().getSensorRangeMax() * 1.3f)
                + MathUtils.getRandomNumberInRange(0f, FishermanConstants.SPAWN_DISTANCE_SPREAD);

        Vector2f at = MathUtils.getPointOnCircumference(near, distance,
                MathUtils.getRandomNumberInRange(0f, 360f));

        at = OuterReaches.place(system, at);

        system.addEntity(fleet);
        fleet.setLocation(at.x, at.y);

        // the wander is vanilla's patrol; the two weeks and the leaving belong to the behaviour
        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, system.getCenter(),
                FishermanConstants.STAY_DAYS + 2f, "fishing the deep");

        fleet.addScript(new FishermanBehavior(fleet));

        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.ACTIVE_KEY, fleet);

        stamp();

        return fleet;
    }

    public static boolean isFisherman(CampaignFleetAPI fleet) {
        return fleet != null
                && fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.FLEET_FLAG);
    }

    public static boolean isVisiting(CampaignFleetAPI fleet) {
        return fleet != null
                && fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.VISITING_FLAG);
    }
}
