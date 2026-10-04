package catchrelease.tools;

import catchrelease.campaign.fish.fisherman.FishermanMapIcon;
import catchrelease.campaign.fish.fisherman.FishermanBehavior;
import catchrelease.campaign.fish.jobs.fleet.FleetQuest;
import catchrelease.campaign.fish.jobs.fleet.FleetQuestMapIcon;
import catchrelease.campaign.fish.map.FleetMapVisibility;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FleetMapVisibilityCheck {

    private FleetMapVisibilityCheck() {
    }

    public static void main(String[] args) {
        SectorAPI original = Global.getSector();
        try {
            Fixture f = new Fixture();
            Global.setSector(f.sector);
            require(FishermanMapIcon.findOrAddStanding(f.system, f.fleet) == f.marker, "Legacy marker adoption");
            for (VisibilityLevel level : VisibilityLevel.values()) {
                f.visibility = level;
                f.course = f.marker;
                f.plugin.syncVisibility();
                require(f.entities.contains(f.marker) == (level == VisibilityLevel.NONE),
                        "Wrong radar suppression at " + level);
                require(FishermanMapIcon.findStanding(f.system) == f.marker, "Lost standing token");
                if (level != VisibilityLevel.NONE) require(f.course == f.fleet, "Lost autopilot target");
            }

            f.visibility = VisibilityLevel.NONE;
            f.fleetPosition.set(2300f, -1700f);
            f.plugin.syncVisibility();
            f.plugin.syncVisibility();
            require(f.entities.size() == 1, "Duplicate restored marker");
            require(f.markerPosition.equals(f.fleetPosition), "Stale marker position");

            f.visibility = VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS;
            f.localPlayer = false;
            f.plugin.syncVisibility();
            require(f.entities.size() == 1, "Off-location logical visibility hid posting");
            f.localPlayer = true;
            f.plugin.syncVisibility();
            require(f.entities.isEmpty(), "Marker not removed from location");
            require(f.marker.getContainingLocation() == f.system, "Fixture lost native location semantics");
            f.plugin.detach();
            require(f.entities.size() == 1, "Unloading did not restore posting");

            FishermanMapIcon.findOrAddStanding(f.system, f.fleet);
            require(f.entities.isEmpty(), "Reattachment duplicated visible fleet");
            FishermanMapIcon.removeFor(f.fleet);
            require(f.entities.size() == 1, "Hidden standing token missed teardown");
            require(FishermanMapIcon.findStanding(f.system) == f.marker, "Teardown forgot posting");
            checkBoatOwner();
            checkQuest();
            System.out.println("Fleet map visibility: radar levels, restoration, navigation and teardown passed");
        } finally {
            Global.setSector(original);
        }
    }

    private static void checkBoatOwner() {
        Fixture f = new Fixture();
        Global.setSector(f.sector);
        MarkerOwner owner = new MarkerOwner(f.fleet);
        owner.update();
        f.visibility = VisibilityLevel.SENSOR_CONTACT;
        f.plugin.syncVisibility();
        require(f.entities.isEmpty(), "Temporary marker did not hide");
        f.visibility = VisibilityLevel.NONE;
        owner.update();
        require(f.entities.size() == 1, "Temporary owner did not restore hidden marker");

        FishermanMapIcon.findOrAddStanding(f.system, f.fleet);
        f.visibility = VisibilityLevel.SENSOR_CONTACT;
        owner.update();
        require(f.entities.isEmpty(), "Attached posting did not hide");
        f.visibility = VisibilityLevel.NONE;
        owner.update();
        require(f.entities.size() == 1, "Non-standing boat did not restore its posting");
    }

    private static class MarkerOwner extends FishermanBehavior {

        private MarkerOwner(CampaignFleetAPI fleet) {
            super(fleet);
        }

        private void update() {
            keepMarker(true);
        }
    }

    private static void checkQuest() {
        Fixture f = new Fixture(true);
        Global.setSector(f.sector);
        require(FleetQuestMapIcon.findOrAdd(f.fleet) == f.marker, "Quest marker adoption");
        for (VisibilityLevel level : VisibilityLevel.values()) {
            f.visibility = level;
            f.course = f.marker;
            require(FleetQuestMapIcon.findOrAdd(f.fleet) == f.marker, "Quest token changed");
            require(f.entities.contains(f.marker) == (level == VisibilityLevel.NONE),
                    "Quest radar suppression at " + level);
        }
        f.visibility = VisibilityLevel.NONE;
        FleetQuestMapIcon.findOrAdd(f.fleet);
        FleetQuestMapIcon.findOrAdd(f.fleet);
        require(f.entities.size() == 1, "Duplicate quest marker");
        f.visibility = VisibilityLevel.SENSOR_CONTACT;
        FleetQuestMapIcon.findOrAdd(f.fleet);
        f.localPlayer = false;
        FleetQuestMapIcon.findOrAdd(f.fleet);
        require(f.entities.size() == 1, "Quest marker lost on player departure");
        f.localPlayer = true;
        FleetQuestMapIcon.findOrAdd(f.fleet);
        require(f.entities.isEmpty(), "Quest marker not hidden on reentry");
        FleetQuestMapIcon.removeFor(f.fleet);
        f.fleetMemory.set(FleetQuest.QUEST_FLAG, false);
        f.visibility = VisibilityLevel.NONE;
        require(FleetQuestMapIcon.findOrAdd(f.fleet) == null, "Completed quest recreated marker");
        require(f.entities.isEmpty(), "Completed quest retained marker");

        f.entities.add(f.marker);
        f.fleetMemory.set(FleetQuest.QUEST_FLAG, true);
        FleetQuestMapIcon.findOrAdd(f.fleet);
        f.fleetMemory.set(FleetQuest.TAKEN_FLAG, false);
        require(FleetQuestMapIcon.findOrAdd(f.fleet) == null, "Unaccepted quest kept marker");
        require(f.entities.isEmpty(), "Visible quest cleanup failed");
    }

    private static class Fixture {

        private final List<CustomCampaignEntityAPI> entities = new ArrayList<>();
        private final MemoryAPI systemMemory = memory();
        private final MemoryAPI fleetMemory = memory();
        private final Vector2f fleetPosition = new Vector2f(100f, 200f);
        private final Vector2f markerPosition = new Vector2f();
        private final FishermanMapIcon plugin = new FishermanMapIcon();
        private final FleetQuestMapIcon questPlugin = new FleetQuestMapIcon();

        private VisibilityLevel visibility = VisibilityLevel.NONE;
        private boolean localPlayer = true;
        private SectorEntityToken course;

        private final StarSystemAPI system;
        private final CampaignFleetAPI fleet;
        private final CustomCampaignEntityAPI marker;
        private final SectorAPI sector;

        private Fixture() {
            this(false);
        }

        private Fixture(boolean quest) {
            fleetMemory.set(FleetQuest.QUEST_FLAG, true);
            fleetMemory.set(FleetQuest.TAKEN_FLAG, true);
            system = proxy(StarSystemAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getMemoryWithoutUpdate" -> systemMemory;
                case "getCustomEntities" -> entities;
                case "addEntity" -> entities.add((CustomCampaignEntityAPI) args[0]);
                case "removeEntity" -> entities.remove(args[0]);
                default -> throw new AssertionError(method);
            });
            fleet = proxy(CampaignFleetAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getContainingLocation", "getStarSystem" -> system;
                case "getMemoryWithoutUpdate" -> fleetMemory;
                case "getLocation" -> fleetPosition;
                case "getVisibilityLevelToPlayerFleet" -> visibility;
                case "isAlive" -> true;
                case "isExpired" -> false;
                default -> throw new AssertionError(method);
            });
            marker = proxy(CustomCampaignEntityAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getCustomPlugin" -> quest ? questPlugin : plugin;
                case "getCustomEntityType" -> quest ? FleetQuestMapIcon.ENTITY_ID : FishermanMapIcon.ENTITY_ID;
                case "getContainingLocation" -> system;
                case "getLocation" -> markerPosition;
                case "isAlive" -> entities.contains(self);
                case "isExpired" -> false;
                case "setLocation" -> {
                    markerPosition.set((float) args[0], (float) args[1]);
                    yield null;
                }
                case "setDiscoverable", "setSensorProfile" -> null;
                default -> throw new AssertionError(method);
            });
            entities.add(marker);
            plugin.init(marker, fleet);
            questPlugin.init(marker, fleet);

            CampaignFleetAPI player = proxy(CampaignFleetAPI.class, (self, method, args) -> {
                if (method.getName().equals("getContainingLocation")) return localPlayer ? system : null;
                throw new AssertionError(method);
            });
            CampaignUIAPI ui = proxy(CampaignUIAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getUltimateCourseTarget" -> course;
                case "layInCourseForNextStep" -> course = (SectorEntityToken) args[0];
                default -> throw new AssertionError(method);
            });
            sector = proxy(SectorAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getPlayerFleet" -> player;
                case "getCampaignUI" -> ui;
                case "getAllLocations" -> List.of(system);
                default -> throw new AssertionError(method);
            });
        }
    }

    private static MemoryAPI memory() {
        Map<String, Object> values = new HashMap<>();
        return proxy(MemoryAPI.class, (self, method, args) -> switch (method.getName()) {
            case "get" -> values.get(args[0]);
            case "getBoolean" -> Boolean.TRUE.equals(values.get(args[0]));
            case "set" -> values.put((String) args[0], args[1]);
            case "unset" -> values.remove(args[0]);
            default -> throw new AssertionError(method);
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (self, method, args) -> switch (method.getName()) {
                    case "equals" -> self == args[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> type.getSimpleName();
                    default -> handler.invoke(self, method, args);
                }));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
