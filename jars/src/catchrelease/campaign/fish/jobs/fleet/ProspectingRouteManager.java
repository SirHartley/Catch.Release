package catchrelease.campaign.fish.jobs.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.Random;

// Tri-Tachyon prospecting fleets that sit on an ore world in an unclaimed system. They carry the
// Claim Assay offer. With Nexerelin, its Tri-Tachyon mining fleets carry the offer instead and this
// manager adds no routes.
public class ProspectingRouteManager extends QuestRouteManager {

    public static final String FLEET_TYPE = "catchrelease_prospectingFleet";
    public static final String FLEET_FLAG = "$catchrelease_prospectingFleet";
    public static final String ROUTE_SOURCE = "catchrelease_prospectingFleets";
    public static final String NEXERELIN_ID = "nexerelin";

    public static final int MAX_FLEETS = 2;
    public static final float CHANCE_PER_TICK = 0.08f;
    public static final float MAX_RANGE_LY = 12f;

    public static void register() {
        register(ProspectingRouteManager.class, new ProspectingRouteManager());
    }

    @Override
    protected boolean isActive() {
        return !Global.getSettings().getModManager().isModEnabled(NEXERELIN_ID);
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
        MarketAPI source = pickSourceMarket(random);
        if (source == null) return;

        PlanetAPI claim = pickClaim(source, random);
        if (claim == null) return;

        RouteData route = RouteManager.getInstance().addRoute(ROUTE_SOURCE, source,
                random.nextLong(), new OptionalFleetData(source, Factions.TRITACHYON), this);

        float travelDays = 2f + Misc.getDistanceLY(source.getLocationInHyperspace(),
                claim.getLocationInHyperspace()) * 1.5f;

        route.addSegment(new RouteSegment(2f + random.nextFloat() * 3f, source.getPrimaryEntity()));
        route.addSegment(new RouteSegment(travelDays, source.getPrimaryEntity(), claim));
        route.addSegment(new RouteSegment(30f + random.nextFloat() * 20f, claim));
        route.addSegment(new RouteSegment(travelDays, claim, source.getPrimaryEntity()));
        route.addSegment(new RouteSegment(3f + random.nextFloat() * 3f, source.getPrimaryEntity()));
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        MarketAPI source = route.getMarket();
        if (source == null) return null;

        Random random = route.getRandom();
        FleetParamsV3 params = new FleetParamsV3(source, source.getLocationInHyperspace(),
                Factions.TRITACHYON, null, FLEET_TYPE,
                8f + random.nextInt(6), 4f, 2f, 0f, 0f, 4f, 0f);
        params.timestamp = route.getTimestamp();
        params.random = random;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setName("Prospecting Fleet");
        fleet.getMemoryWithoutUpdate().set(FLEET_FLAG, true);
        fleet.addScript(new ProspectingAI(fleet, route));

        return fleet;
    }

    protected static MarketAPI pickSourceMarket(Random random) {
        WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>(random);
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.TRITACHYON.equals(market.getFactionId())) continue;
            if (market.isHidden() || !market.hasSpaceport()) continue;
            if (market.getContainingLocation() == null
                    || market.getContainingLocation().isHyperspace()) continue;

            picker.add(market, market.getSize());
        }

        return picker.pick();
    }

    // An ore world nobody has settled, in a system without markets, so the survey rights are open.
    protected static PlanetAPI pickClaim(MarketAPI source, Random random) {
        WeightedRandomPicker<PlanetAPI> picker = new WeightedRandomPicker<>(random);
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!isOrdinaryDestination(system)) continue;
            if (!Misc.getMarketsInLocation(system).isEmpty()) continue;
            if (Misc.getDistanceLY(source.getLocationInHyperspace(), system.getLocation())
                    > MAX_RANGE_LY) continue;

            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar() || !hasOre(planet)) continue;

                picker.add(planet);
            }
        }

        return picker.pick();
    }

    protected static boolean hasOre(PlanetAPI planet) {
        MarketAPI market = planet.getMarket();
        if (market == null || !market.isPlanetConditionMarketOnly()) return false;

        for (MarketConditionAPI condition : market.getConditions()) {
            String id = condition.getId();
            if (id.startsWith("ore_") || id.startsWith("rare_ore_")) return true;
        }

        return false;
    }

    public static class ProspectingAI extends RouteFleetAssignmentAI {

        public ProspectingAI(CampaignFleetAPI fleet, RouteData route) {
            super(fleet, route);
        }

        @Override
        protected String getInSystemActionText(RouteSegment segment) {
            return segment.from == null ? "prospecting" : "surveying " + segment.from.getName();
        }
    }
}
