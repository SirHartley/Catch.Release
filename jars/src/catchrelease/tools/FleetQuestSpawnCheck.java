package catchrelease.tools;

import catchrelease.campaign.fish.jobs.fleet.FleetQuest;
import catchrelease.campaign.fish.jobs.fleet.FleetQuestSpawner;
import catchrelease.campaign.fish.jobs.fleet.FleetQuestType;
import catchrelease.campaign.fish.jobs.fleet.ProspectingRouteManager;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FleetQuestSpawnCheck {

    private FleetQuestSpawnCheck() {
    }

    public static void main(String[] args) {
        SectorAPI originalSector = Global.getSector();
        SettingsAPI originalSettings = Global.getSettings();
        Global.setSettings(proxy(SettingsAPI.class, (self, method, values) -> switch (method.getName()) {
            case "getFloat" -> 1f;
            case "getInt" -> 1;
            case "getBoolean" -> false;
            case "getColor" -> Color.WHITE;
            default -> throw new AssertionError(method);
        }));
        try {
            Fixture f = new Fixture();
            Global.setSector(f.sector);
            f.checkSpawn("None of that type are present");
            f.fleets.add(f.giver);
            f.hostile = true;
            f.checkSpawn("hostile to the player");
            f.hostile = false;
            f.fleetValues.put(MemFlags.ENTITY_MISSION_IMPORTANT, true);
            f.checkSpawn("marked important for a mission");
            f.fleetValues.remove(MemFlags.ENTITY_MISSION_IMPORTANT);
            f.checkSpawn("tutorial is not complete");

            f.tutorialStage = FishingIntro.DONE;
            f.fleetValues.put(FleetQuest.QUEST_FLAG, true);
            List<String> errors = new ArrayList<>();
            require(FleetQuest.startOn(f.giver, FleetQuestType.CLAIM_ASSAY, errors::add) == null,
                    "Started a second quest on one fleet");
            require(errors.size() == 1 && errors.get(0).contains("already carries"), "Lost start rejection");
            require(FleetQuest.startOn(f.giver, FleetQuestType.CLAIM_ASSAY) == null,
                    "Legacy caller changed behavior");

            require(!FleetQuestType.CALIBRATION_PAIR.canSpawnIn(null), "Missing low-coherence gate");
            require(FleetQuestType.CALIBRATION_PAIR.getSpawnFailure(null).contains("low-coherence"),
                    "Missing low-coherence reason");
            errors.clear();
            require(FleetQuestSpawner.spawnForTesting(FleetQuestType.CALIBRATION_PAIR, errors::add) == null,
                    "Spawned Calibration Pair at full coherence");
            require(errors.size() == 1 && errors.get(0).contains("100.0%")
                    && errors.get(0).contains("45.0%"), "Lost current coherence or threshold: " + errors);
            require(FleetQuestType.CLAIM_ASSAY.canSpawnIn(null), "Added a system gate to Claim Assay");
            require(FleetQuestType.PARLEY_FISH.getSpawnFailure(f.system).contains("Core system"),
                    "Lost Core-system exclusion");
            System.out.println("Fleet quest spawn diagnostics: missing/rejected givers, tutorial, duplicate quest,"
                    + " location gates and legacy caller passed");
        } finally {
            Global.setSector(originalSector);
            Global.setSettings(originalSettings);
        }
    }

    private static final class Fixture {

        private final List<CampaignFleetAPI> fleets = new ArrayList<>();
        private final Map<String, Object> fleetValues = new HashMap<>();
        private final Map<String, Object> persistentData = new HashMap<>();
        private boolean hostile;
        private int tutorialStage;

        private final MemoryAPI fleetMemory = proxy(MemoryAPI.class,
                (self, method, values) -> switch (method.getName()) {
                    case "getBoolean" -> Boolean.TRUE.equals(fleetValues.get(values[0]));
                    case "getString" -> (String) fleetValues.get(values[0]);
                    default -> throw new AssertionError(method);
                });
        private final MemoryAPI globalMemory = proxy(MemoryAPI.class,
                (self, method, values) -> {
                    if (method.getName().equals("getInt")) return tutorialStage;
                    throw new AssertionError(method);
                });
        private final StarSystemAPI system = proxy(StarSystemAPI.class,
                (self, method, values) -> switch (method.getName()) {
                    case "getFleets" -> fleets;
                    case "getLocation" -> null;
                    case "hasTag" -> Tags.THEME_CORE.equals(values[0]);
                    default -> throw new AssertionError(method);
                });
        private final CampaignFleetAPI player = proxy(CampaignFleetAPI.class,
                (self, method, values) -> {
                    if (method.getName().equals("getContainingLocation")) return system;
                    throw new AssertionError(method);
                });
        private final FactionAPI faction = proxy(FactionAPI.class,
                (self, method, values) -> switch (method.getName()) {
                    case "getId" -> Factions.TRITACHYON;
                    case "isPlayerFaction" -> false;
                    default -> throw new AssertionError(method);
                });
        private final PersonAPI commander = proxy(PersonAPI.class,
                (self, method, values) -> { throw new AssertionError(method); });
        private final CampaignFleetAPI giver = proxy(CampaignFleetAPI.class,
                (self, method, values) -> switch (method.getName()) {
                    case "getFaction" -> faction;
                    case "getMemoryWithoutUpdate" -> fleetMemory;
                    case "getContainingLocation" -> system;
                    case "isAlive" -> true;
                    case "isHostileTo" -> hostile;
                    case "isExpired", "isEmpty", "isStationMode", "isHidden",
                            "isDespawning", "isInHyperspaceTransition" -> false;
                    case "getBattle", "getCurrentAssignment" -> null;
                    case "getCommander" -> commander;
                    default -> throw new AssertionError(method);
                });
        private final IntelManagerAPI intel = proxy(IntelManagerAPI.class,
                (self, method, values) -> {
                    if (method.getName().equals("getIntel")) return new ArrayList<>();
                    throw new AssertionError(method);
                });
        private final SectorAPI sector = proxy(SectorAPI.class,
                (self, method, values) -> switch (method.getName()) {
                    case "getPlayerFleet" -> player;
                    case "getScripts" -> new ArrayList<>();
                    case "getIntelManager" -> intel;
                    case "getMemoryWithoutUpdate" -> globalMemory;
                    case "getPersistentData" -> persistentData;
                    default -> throw new AssertionError(method);
                });

        private Fixture() {
            fleetValues.put(ProspectingRouteManager.FLEET_FLAG, true);
        }

        private void checkSpawn(String expected) {
            List<String> errors = new ArrayList<>();
            require(FleetQuestSpawner.spawnForTesting(FleetQuestType.CLAIM_ASSAY, errors::add) == null,
                    "Unexpected successful spawn");
            require(errors.size() == 1 && errors.get(0).contains(expected),
                    "Expected " + expected + ", got " + errors);
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
