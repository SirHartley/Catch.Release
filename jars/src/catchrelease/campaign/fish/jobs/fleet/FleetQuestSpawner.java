package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.crime.HarpoonOffence;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RuinsFleetRouteManager;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.impl.campaign.procgen.themes.ScavengerFleetAssignmentAI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FleetQuestSpawner implements EveryFrameScript {

    public static final float CHECK_MIN_DAYS = 3f;
    public static final float CHECK_MAX_DAYS = 7f;

    public static final float CHANCE = 0.07f;
    public static final int MAX_ACTIVE = 1;

    public static final String COOLDOWN_KEY = "$catchrelease_fleetQuestCooldown";
    public static final float COOLDOWN_DAYS = 45f;

    public static final String TEST_ROUTE_SOURCE = "catchrelease_test_fleet_quest";
    public static final float TEST_ROUTE_DAYS = 60f;
    public static final float TEST_SPAWN_DISTANCE = 1200f;

    protected IntervalUtil interval = new IntervalUtil(CHECK_MIN_DAYS, CHECK_MAX_DAYS);
    protected Random random = new Random();

    private static class TestRouteSpawner implements RouteFleetSpawner {

        private final StarSystemAPI system;
        private final FleetQuestType type;

        private TestRouteSpawner(StarSystemAPI system, FleetQuestType type) {
            this.system = system;
            this.type = type;
        }

        @Override
        public CampaignFleetAPI spawnFleet(RouteData route) {
            Random random = route.getRandom();
            CampaignFleetAPI fleet;
            if (type.usesTradeConvoy()) {
                FleetParamsV3 params = new FleetParamsV3(route.getMarket(), null,
                        Factions.INDEPENDENT, null, FleetTypes.TRADE_SMALL,
                        8f, 10f, 0f, 0f, 0f, 0f, 0f);
                params.maxShipSize = 2;
                params.random = random;
                fleet = FleetFactoryV3.createFleet(params);
            } else {
                fleet = RuinsFleetRouteManager.createScavenger(
                        null, system.getLocation(), route, route.getMarket(), false, random);
            }
            if (fleet == null) return null;

            if (type.usesTradeConvoy()) {
                fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
                fleet.addScript(new RouteFleetAssignmentAI(fleet, route));
            } else {
                fleet.addScript(new ScavengerFleetAssignmentAI(fleet, route, false));
            }

            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player != null && player.getContainingLocation() == system) {
                Vector2f location = Misc.getPointAtRadius(
                        player.getLocation(), TEST_SPAWN_DISTANCE, random);
                fleet.setLocation(location.x, location.y);
            }

            FleetQuest quest = FleetQuest.startOn(fleet, type);
            if (quest == null) {
                Misc.fadeAndExpire(fleet);
                return null;
            }

            FleetQuestEncounter.attach(fleet, quest);
            return fleet;
        }

        @Override
        public boolean shouldCancelRouteAfterDelayCheck(RouteData route) {
            return false;
        }

        @Override
        public boolean shouldRepeat(RouteData route) {
            return false;
        }

        @Override
        public void reportAboutToBeDespawnedByRouteManager(RouteData route) {
        }
    }

    public static void register() {
        Global.getSector().addTransientScript(new FleetQuestSpawner());
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
        interval.advance(Global.getSector().getClock().convertToDays(amount));
        if (!interval.intervalElapsed()) return;

        if (!canOffer()) return;
        if (!FishingIntro.isComplete()) return;

        if (random.nextFloat() > CHANCE) return;

        FleetQuestType type = FleetQuestType.rollAny(random);
        if (type == null) return;

        if (adopt(type)) markOffered();
    }

    protected boolean canOffer() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        if (!(player.getContainingLocation() instanceof StarSystemAPI)) return false;

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(COOLDOWN_KEY)) return false;

        return countActive() < MAX_ACTIVE;
    }

    protected void markOffered() {
        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_KEY, true, COOLDOWN_DAYS);
    }

    public static int countActive() {
        return Global.getSector().getIntelManager().getIntel(FleetQuest.class).size()
                + FleetQuestEncounter.countLive();
    }

    public static CampaignFleetAPI spawnForTesting(FleetQuestType type) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !(player.getContainingLocation() instanceof StarSystemAPI system)) {
            return null;
        }
        if (type == null || !FleetQuestType.getLocalOffers().contains(type) || countActive() > 0) {
            return null;
        }

        RuinsFleetRouteManager scavengers = new RuinsFleetRouteManager(system);
        MarketAPI source = scavengers.pickSourceMarket();
        if (source == null) return null;

        RouteManager routes = RouteManager.getInstance();
        OptionalFleetData optional = new OptionalFleetData(source);
        RouteData route = routes.addRoute(TEST_ROUTE_SOURCE, source, Misc.genRandomSeed(),
                optional, new TestRouteSpawner(system, type));
        route.addSegment(new RouteSegment(TEST_ROUTE_DAYS, system.getCenter()));

        // RouteManager owns activeFleet; a zero-day advance keeps the test hull on its normal lifecycle.
        routes.advance(0f);

        CampaignFleetAPI fleet = route.getActiveFleet();
        if (fleet == null) routes.removeRoute(route);
        return fleet;
    }

    protected boolean adopt(FleetQuestType type) {
        LocationAPI location = Global.getSector().getPlayerFleet().getContainingLocation();

        List<CampaignFleetAPI> any = new ArrayList<>();
        List<CampaignFleetAPI> matching = new ArrayList<>();

        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (!canCarryAnOffer(fleet, type)) continue;
            if (type.requiresIndependentFleet()
                    && !Factions.INDEPENDENT.equals(fleet.getFaction().getId())) continue;

            any.add(fleet);

            if (type.fleetType.equals(fleet.getMemoryWithoutUpdate()
                    .getString(MemFlags.MEMORY_KEY_FLEET_TYPE))) {
                matching.add(fleet);
            }
        }

        List<CampaignFleetAPI> pool = type.requiresIndependentFleet()
                ? matching : (matching.isEmpty() ? any : matching);
        if (pool.isEmpty()) return false;

        CampaignFleetAPI chosen = pool.get(random.nextInt(pool.size()));

        FleetQuest quest = FleetQuest.startOn(chosen, type);
        if (quest == null) return false;

        FleetQuestEncounter.attach(chosen, quest);

        return true;
    }

    protected static boolean isScavenger(CampaignFleetAPI fleet) {
        String type = fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE);

        return FleetTypes.SCAVENGER_SMALL.equals(type)
                || FleetTypes.SCAVENGER_MEDIUM.equals(type)
                || FleetTypes.SCAVENGER_LARGE.equals(type);
    }

    protected boolean canCarryAnOffer(CampaignFleetAPI fleet, FleetQuestType type) {
        String fleetType = fleet == null ? null : fleet.getMemoryWithoutUpdate()
                .getString(MemFlags.MEMORY_KEY_FLEET_TYPE);
        if (type.usesTradeConvoy()) {
            if (!FleetTypes.TRADE_SMALL.equals(fleetType)) return false;
            if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET)) {
                return false;
            }
        } else if (!isScavenger(fleet)) {
            return false;
        }

        if (catchrelease.campaign.fish.fisherman.FishermanSpawner.isFisherman(fleet)) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (fleet == null || fleet == player) return false;
        if (fleet.isExpired() || !fleet.isAlive() || fleet.isEmpty()) return false;
        if (fleet.isStationMode() || fleet.isHidden() || fleet.isDespawning()) return false;
        if (fleet.getBattle() != null || fleet.isInHyperspaceTransition()) return false;

        if (fleet.getFaction() == null || fleet.getFaction().isPlayerFaction()) return false;
        if (fleet.isHostileTo(player)) return false;

        // a Church or Path hull does not stop a passing stranger to ask for a fish, whatever else it might stop them for - see FishingTaboo
        if (catchrelease.campaign.fish.FishingTaboo.isTaboo(fleet.getFaction().getId())) return false;

        if (fleet.getCommander() == null) return false;

        if (HarpoonOffence.isCombatCrew(fleet)) return false;

        if (FleetQuest.isQuestFleet(fleet)) return false;
        if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.ENTITY_MISSION_IMPORTANT)) {
            return false;
        }

        return !Misc.isFleetReturningToDespawn(fleet);
    }
}
