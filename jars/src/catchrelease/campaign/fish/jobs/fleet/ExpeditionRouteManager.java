package catchrelease.campaign.fish.jobs.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Galatia Academy science expeditions: out to a few systems at the abyssal edge and back. They carry
// the Mandate offer. Vanilla academy fleets only fly from ordinary markets to the Academy.
public class ExpeditionRouteManager extends QuestRouteManager {

    public static final String FLEET_TYPE = "catchrelease_scienceExpedition";
    public static final String FLEET_FLAG = "$catchrelease_scienceExpedition";
    public static final String ROUTE_SOURCE = "catchrelease_scienceExpeditions";
    // Custom entity; its "ga_market" is not in the economy, so routes start from the entity.
    public static final String ACADEMY_ID = "station_galatia_academy";

    public static final int MAX_FLEETS = 2;
    public static final float CHANCE_PER_TICK = 0.1f;
    public static final int MIN_STOPS = 2;
    public static final int MAX_STOPS = 3;
    public static final float MAX_HOP_LY = 8f;

    public static void register() {
        register(ExpeditionRouteManager.class, new ExpeditionRouteManager());
    }

    @Override
    protected String getRouteSourceId() {
        return ROUTE_SOURCE;
    }

    @Override
    protected int getMaxFleets() {
        return MAX_FLEETS;
    }

    @Override
    protected float getChancePerTick() {
        return CHANCE_PER_TICK;
    }

    @Override
    protected void addRoute(Random random) {
        SectorEntityToken academy = getAcademy();
        if (academy == null) return;

        List<StarSystemAPI> stops = pickStops(random);
        if (stops.size() < MIN_STOPS) return;

        OptionalFleetData extra = new OptionalFleetData();
        extra.factionId = Factions.INDEPENDENT;
        extra.fleetType = FLEET_TYPE;

        RouteData route = RouteManager.getInstance().addRoute(ROUTE_SOURCE, null,
                random.nextLong(), extra, this);

        route.addSegment(new RouteSegment(3f + random.nextFloat() * 3f, academy));

        SectorEntityToken previous = academy;
        for (StarSystemAPI stop : stops) {
            SectorEntityToken center = stop.getCenter();
            route.addSegment(new RouteSegment(travelDays(previous, center), previous, center));
            route.addSegment(new RouteSegment(10f + random.nextFloat() * 6f, center));
            previous = center;
        }

        route.addSegment(new RouteSegment(travelDays(previous, academy), previous, academy));
        route.addSegment(new RouteSegment(3f + random.nextFloat() * 2f, academy));
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        SectorEntityToken academy = getAcademy();
        if (academy == null) return null;

        Random random = route.getRandom();
        FleetParamsV3 params = new FleetParamsV3(null, academy.getLocationInHyperspace(),
                Factions.INDEPENDENT, 0.5f, FLEET_TYPE,
                12f + random.nextInt(8), 0f, 4f, 0f, 4f, 4f, 0f);
        params.timestamp = route.getTimestamp();
        params.random = random;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setName("Science Expedition");
        fleet.setNoFactionInName(true);
        fleet.getMemoryWithoutUpdate().set(FLEET_FLAG, true);
        fleet.addScript(new ExpeditionAI(fleet, route, academy));

        return fleet;
    }

    protected static SectorEntityToken getAcademy() {
        SectorEntityToken academy = Global.getSector().getEntityById(ACADEMY_ID);
        if (academy == null || !academy.isAlive()) return null;
        if (!(academy.getContainingLocation() instanceof StarSystemAPI system)) return null;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return null;

        return academy;
    }

    protected static List<StarSystemAPI> pickStops(Random random) {
        List<StarSystemAPI> candidates = new ArrayList<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!isOrdinaryDestination(system) || system.hasTag(Tags.THEME_CORE)) continue;
            if (FleetQuestType.isNearAbyssal(system)) candidates.add(system);
        }

        List<StarSystemAPI> stops = new ArrayList<>();
        if (candidates.isEmpty()) return stops;

        StarSystemAPI current = candidates.get(random.nextInt(candidates.size()));
        int wanted = MIN_STOPS + random.nextInt(MAX_STOPS - MIN_STOPS + 1);
        while (current != null) {
            stops.add(current);
            candidates.remove(current);
            if (stops.size() >= wanted) break;

            WeightedRandomPicker<StarSystemAPI> next = new WeightedRandomPicker<>(random);
            for (StarSystemAPI system : candidates) {
                if (Misc.getDistanceLY(current.getLocation(), system.getLocation()) <= MAX_HOP_LY) {
                    next.add(system);
                }
            }
            current = next.pick();
        }

        return stops;
    }

    protected static float travelDays(SectorEntityToken from, SectorEntityToken to) {
        return 2f + Misc.getDistanceLY(from.getLocationInHyperspace(), to.getLocationInHyperspace())
                * 1.5f;
    }

    public static class ExpeditionAI extends RouteFleetAssignmentAI {

        protected SectorEntityToken academy;

        public ExpeditionAI(CampaignFleetAPI fleet, RouteData route, SectorEntityToken academy) {
            super(fleet, route);
            this.academy = academy;
        }

        @Override
        protected String getTravelActionText(RouteSegment segment) {
            return segment.to == academy ? "returning to Galatia Academy" : "on a science expedition";
        }

        @Override
        protected String getInSystemActionText(RouteSegment segment) {
            if (segment.from != academy) return "surveying near the abyss";

            return route.getSegments().indexOf(segment) == 0
                    ? "preparing to depart Galatia Academy" : "unloading at Galatia Academy";
        }

        @Override
        protected String getStartingActionText(RouteSegment segment) {
            return getInSystemActionText(segment);
        }

        @Override
        protected String getEndingActionText(RouteSegment segment) {
            return "returning to Galatia Academy";
        }
    }
}
