package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.crime.HarpoonOffence;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
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
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RuinsFleetRouteManager;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.impl.campaign.procgen.themes.ScavengerFleetAssignmentAI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public class FleetQuestSpawner implements EveryFrameScript {

    public static final float CHECK_MIN_DAYS = 3f;
    public static final float CHECK_MAX_DAYS = 7f;

    public static final float CHANCE = 0.07f;
    public static final int MAX_ACTIVE = 1;

    public static final String COOLDOWN_KEY = "$catchrelease_fleetQuestCooldown";
    public static final float COOLDOWN_DAYS = 45f;

    // Nexerelin's MiningFleetManagerV2 writes this $fleetType as a plain literal.
    public static final String NEX_MINING_FLEET_TYPE = "exerelinMiningFleet";

    public static final String TEST_ROUTE_SOURCE = "catchrelease_test_fleet_quest";
    public static final float TEST_ROUTE_DAYS = 60f;
    public static final float TEST_SPAWN_DISTANCE = 1200f;

    protected IntervalUtil interval = new IntervalUtil(CHECK_MIN_DAYS, CHECK_MAX_DAYS);
    protected Random random = new Random();

    private static class TestRouteSpawner implements RouteFleetSpawner {

        private final StarSystemAPI system;
        private final FleetQuestType type;
        private transient String failureReason;

        private TestRouteSpawner(StarSystemAPI system, FleetQuestType type) {
            this.system = system;
            this.type = type;
        }

        @Override
        public CampaignFleetAPI spawnFleet(RouteData route) {
            failureReason = null;
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
            if (fleet == null) {
                failureReason = type.usesTradeConvoy()
                        ? "The fleet factory returned no trade convoy."
                        : "The scavenger factory returned no fleet or an empty fleet.";
                return null;
            }

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

            FleetQuest quest = FleetQuest.startOn(fleet, type, reason -> failureReason = reason);
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

        StarSystemAPI system = (StarSystemAPI) Global.getSector().getPlayerFleet()
                .getContainingLocation();
        FleetQuestType type = pickType(system);
        if (type == null) return;

        if (adopt(type)) markOffered(type);
    }

    protected boolean canOffer() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        if (!(player.getContainingLocation() instanceof StarSystemAPI)) return false;

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(COOLDOWN_KEY)) return false;

        return countActive() < MAX_ACTIVE;
    }

    // A dedicated offer is only rolled while one of its fleets is here, so it does not use up
    // the roll of the offers that any scavenger or convoy can carry.
    protected FleetQuestType pickType(StarSystemAPI system) {
        WeightedRandomPicker<FleetQuestType> picker = new WeightedRandomPicker<>(random);
        for (FleetQuestType type : FleetQuestType.getLocalOffers()) {
            if (!type.canSpawnIn(system)) continue;
            if (type.hasDedicatedGivers() && findDedicatedGivers(system, type).isEmpty()) continue;

            picker.add(type, type.getOfferWeight());
        }

        return picker.pick();
    }

    protected void markOffered(FleetQuestType type) {
        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_KEY, true,
                type == null ? COOLDOWN_DAYS : type.getOfferCooldownDays());
    }

    public static int countActive() {
        int count = FleetQuestEncounter.countLive();
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager()
                .getIntel(FleetQuest.class)) {
            FleetQuest quest = (FleetQuest) intel;
            if (quest.isActiveRequest()) count++;
        }
        return count;
    }

    public static CampaignFleetAPI spawnForTesting(FleetQuestType type) {
        return spawnForTesting(type, null);
    }

    public static CampaignFleetAPI spawnForTesting(FleetQuestType type, Consumer<String> onFailure) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !(player.getContainingLocation() instanceof StarSystemAPI system)) {
            return failSpawn(onFailure, "SpawnFleetQuest is only available inside a star system.");
        }
        if (type == null || !FleetQuestType.getLocalOffers().contains(type)) {
            return failSpawn(onFailure, "The quest type is not a local fleet offer.");
        }
        if (countActive() > 0) {
            return failSpawn(onFailure, "A fleet quest offer or accepted fleet quest is already active.");
        }
        String failure = type.getSpawnFailure(system);
        if (failure != null) return failSpawn(onFailure, failure);

        if (type.hasDedicatedGivers()) {
            List<CampaignFleetAPI> givers = findDedicatedGivers(system, type);
            if (givers.isEmpty()) return failSpawn(onFailure, describeMissingGiver(system, type));

            CampaignFleetAPI giver = givers.get(new Random().nextInt(givers.size()));
            FleetQuest quest = FleetQuest.startOn(giver, type, onFailure);
            if (quest == null) return null;

            FleetQuestEncounter.attach(giver, quest);
            return giver;
        }

        RuinsFleetRouteManager scavengers = new RuinsFleetRouteManager(system);
        MarketAPI source = scavengers.pickSourceMarket();
        if (source == null) {
            return failSpawn(onFailure, "No source market is available: it must be visible, inside a system,"
                    + " and non-hostile to the Independents.");
        }

        RouteManager routes = RouteManager.getInstance();
        OptionalFleetData optional = new OptionalFleetData(source);
        TestRouteSpawner spawner = new TestRouteSpawner(system, type);
        RouteData route = routes.addRoute(TEST_ROUTE_SOURCE, source, Misc.genRandomSeed(),
                optional, spawner);
        route.addSegment(new RouteSegment(TEST_ROUTE_DAYS, system.getCenter()));

        // RouteManager owns activeFleet; a zero-day advance keeps the test hull on its normal lifecycle.
        routes.advance(0f);

        CampaignFleetAPI fleet = route.getActiveFleet();
        if (fleet == null) {
            routes.removeRoute(route);
            return failSpawn(onFailure, spawner.failureReason != null ? spawner.failureReason
                    : "RouteManager did not activate the test fleet during its spawn pass.");
        }
        return fleet;
    }

    private static CampaignFleetAPI failSpawn(Consumer<String> onFailure, String reason) {
        if (onFailure != null) onFailure.accept(reason);
        return null;
    }

    private static String describeMissingGiver(StarSystemAPI system, FleetQuestType type) {
        String required = switch (type) {
            case FOLLOWER -> "a Hegemony trade convoy";
            case STATE_DINNER -> "a Sindrian Diktat trade convoy";
            case CLAIM_ASSAY -> "a Tri-Tachyon prospecting fleet or Nexerelin mining fleet";
            case MANDATE -> "an Academy science expedition";
            case PARLEY_FISH -> "a patrol from a procedurally generated pirate base";
            default -> "a matching quest fleet";
        };
        Map<String, Integer> rejected = new LinkedHashMap<>();
        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (!fitsDedicatedGiver(fleet, type)) continue;
            String reason = getGiverFailure(fleet);
            if (reason != null) rejected.merge(reason, 1, Integer::sum);
        }
        String message = "Requires " + required + ". This command only uses an existing fleet for this quest.";
        if (rejected.isEmpty()) return message + " None of that type are present in this system.";

        StringBuilder detail = new StringBuilder(message).append(" Matching fleets were rejected:");
        for (Map.Entry<String, Integer> entry : rejected.entrySet()) {
            detail.append("\n").append(entry.getValue()).append(" fleet(s): ").append(entry.getKey());
        }
        return detail.toString();
    }

    protected boolean adopt(FleetQuestType type) {
        LocationAPI location = Global.getSector().getPlayerFleet().getContainingLocation();

        if (type.hasDedicatedGivers()) {
            List<CampaignFleetAPI> givers = findDedicatedGivers(location, type);
            if (givers.isEmpty()) return false;

            return startOffer(givers.get(random.nextInt(givers.size())), type);
        }

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

        return startOffer(pool.get(random.nextInt(pool.size())), type);
    }

    protected boolean startOffer(CampaignFleetAPI giver, FleetQuestType type) {
        FleetQuest quest = FleetQuest.startOn(giver, type);
        if (quest == null) return false;

        FleetQuestEncounter.attach(giver, quest);

        return true;
    }

    protected static List<CampaignFleetAPI> findDedicatedGivers(LocationAPI location,
                                                               FleetQuestType type) {
        List<CampaignFleetAPI> givers = new ArrayList<>();
        if (location == null) return givers;

        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (fitsDedicatedGiver(fleet, type) && isAvailableGiver(fleet)) givers.add(fleet);
        }

        return givers;
    }

    protected static boolean fitsDedicatedGiver(CampaignFleetAPI fleet, FleetQuestType type) {
        if (fleet == null || fleet.getFaction() == null) return false;

        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        String faction = fleet.getFaction().getId();
        String fleetType = memory.getString(MemFlags.MEMORY_KEY_FLEET_TYPE);

        switch (type) {
            case FOLLOWER:
                return Factions.HEGEMONY.equals(faction) && isTradeConvoy(fleetType);
            case STATE_DINNER:
                return Factions.DIKTAT.equals(faction) && isTradeConvoy(fleetType);
            case CLAIM_ASSAY:
                return Factions.TRITACHYON.equals(faction)
                        && (memory.getBoolean(ProspectingRouteManager.FLEET_FLAG)
                        || NEX_MINING_FLEET_TYPE.equals(fleetType));
            case MANDATE:
                return memory.getBoolean(ExpeditionRouteManager.FLEET_FLAG);
            case PARLEY_FISH:
                return isPirateBasePatrol(fleet, fleetType);
            default:
                return false;
        }
    }

    protected static boolean isTradeConvoy(String fleetType) {
        return FleetTypes.TRADE.equals(fleetType) || FleetTypes.TRADE_SMALL.equals(fleetType);
    }

    // Only procgen bases carry the flag; the pirate markets of the Core never do.
    protected static boolean isPirateBasePatrol(CampaignFleetAPI fleet, String fleetType) {
        if (!FleetTypes.PATROL_SMALL.equals(fleetType) && !FleetTypes.PATROL_MEDIUM.equals(fleetType)
                && !FleetTypes.PATROL_LARGE.equals(fleetType)) return false;

        MarketAPI source = Misc.getSourceMarket(fleet);
        return source != null && source.getMemoryWithoutUpdate().getBoolean(PirateBaseIntel.MEM_FLAG);
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

        return !HarpoonOffence.isCombatCrew(fleet) && isAvailableGiver(fleet);
    }

    // Pirate-base patrols pass through here too, so the combat-crew filter stays with the callers.
    protected static boolean isAvailableGiver(CampaignFleetAPI fleet) {
        return getGiverFailure(fleet) == null;
    }

    private static String getGiverFailure(CampaignFleetAPI fleet) {
        if (catchrelease.campaign.fish.fisherman.FishermanSpawner.isFisherman(fleet)) {
            return "The Fisherman cannot carry this quest.";
        }

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (fleet == null) return "No fleet was supplied.";
        if (fleet == player) return "This is the player's fleet.";
        if (fleet.isExpired()) return "The fleet has expired.";
        if (!fleet.isAlive()) return "The fleet is no longer alive.";
        if (fleet.isEmpty()) return "The fleet has no ships.";
        if (fleet.isStationMode()) return "The fleet is a station.";
        if (fleet.isHidden()) return "The fleet is hidden.";
        if (fleet.isDespawning()) return "The fleet is despawning.";
        if (fleet.getBattle() != null) return "The fleet is in battle.";
        if (fleet.isInHyperspaceTransition()) return "The fleet is making a hyperspace transition.";

        if (fleet.getFaction() == null) return "The fleet has no faction.";
        if (fleet.getFaction().isPlayerFaction()) return "The fleet belongs to the player faction.";
        if (fleet.isHostileTo(player)) return "The fleet is hostile to the player.";

        // Church and Path fleets do not offer fishing work.
        if (catchrelease.campaign.fish.FishingTaboo.isTaboo(fleet.getFaction().getId())) {
            return "The fleet's faction does not offer fishing work.";
        }

        if (fleet.getCommander() == null) return "The fleet has no commander.";

        if (FleetQuest.isQuestFleet(fleet)) return "The fleet already carries a fishing quest.";
        if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.ENTITY_MISSION_IMPORTANT)) {
            return "The fleet is marked important for a mission.";
        }

        return Misc.isFleetReturningToDespawn(fleet)
                ? "The fleet is returning to despawn." : null;
    }
}
