package catchrelease.tools;

import catchrelease.campaign.fish.fisherman.FishermanMapIcon;
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
            System.out.println("Fleet map visibility: radar levels, restoration, navigation and teardown passed");
        } finally {
            Global.setSector(original);
        }
    }

    private static class Fixture {

        private final List<CustomCampaignEntityAPI> entities = new ArrayList<>();
        private final MemoryAPI systemMemory = memory();
        private final Vector2f fleetPosition = new Vector2f(100f, 200f);
        private final Vector2f markerPosition = new Vector2f();
        private final FishermanMapIcon plugin = new FishermanMapIcon();

        private VisibilityLevel visibility = VisibilityLevel.NONE;
        private boolean localPlayer = true;
        private SectorEntityToken course;

        private final StarSystemAPI system;
        private final CampaignFleetAPI fleet;
        private final CustomCampaignEntityAPI marker;
        private final SectorAPI sector;

        private Fixture() {
            system = proxy(StarSystemAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getMemoryWithoutUpdate" -> systemMemory;
                case "getCustomEntities" -> entities;
                case "addEntity" -> entities.add((CustomCampaignEntityAPI) args[0]);
                case "removeEntity" -> entities.remove(args[0]);
                default -> throw new AssertionError(method);
            });
            fleet = proxy(CampaignFleetAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getContainingLocation", "getStarSystem" -> system;
                case "getLocation" -> fleetPosition;
                case "getVisibilityLevelToPlayerFleet" -> visibility;
                case "isAlive" -> true;
                case "isExpired" -> false;
                default -> throw new AssertionError(method);
            });
            marker = proxy(CustomCampaignEntityAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getCustomPlugin" -> plugin;
                case "getCustomEntityType" -> FishermanMapIcon.ENTITY_ID;
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
            case "set" -> values.put((String) args[0], args[1]);
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
