package catchrelease.tools;

import catchrelease.campaign.fish.fisherman.FishermanBehavior;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.tutorial.FishermanInterception;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.helper.math.ViewportEdge;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.NavigationModulePlugin;
import com.fs.starfarer.api.campaign.ai.TacticalModulePlugin;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FishermanInterceptCheck {

    private FishermanInterceptCheck() {
    }

    public static void main(String[] args) {
        int points = 0;
        for (float width : new float[]{1280f, 1920f, 3440f}) {
            for (float zoom : new float[]{0.5f, 1f, 2f, 4f}) {
                ViewportAPI viewport = viewport(width, 1080f, zoom, zoom * 1.1f);
                for (int angle = 0; angle < 360; angle += 3) {
                    Vector2f at = ViewportEdge.outside(viewport,
                            TutorialConstants.INTERCEPT_VIEWPORT_MARGIN_PX, angle);
                    float xGap = Math.max(-at.x, at.x - width * zoom) / zoom;
                    float yGap = Math.max(-at.y, at.y - 1080f * zoom * 1.1f) / (zoom * 1.1f);
                    require(Math.abs(Math.max(xGap, yGap)
                            - TutorialConstants.INTERCEPT_VIEWPORT_MARGIN_PX) < 0.01f,
                            "Spawn margin at " + width + "/" + zoom + "/" + angle);
                    require(!ViewportEdge.contains(viewport, at, 0f), "Spawn inside viewport");
                    points++;
                }
            }
        }

        require(ViewportEdge.outside(null, 50f, 0f) == null, "Missing viewport");
        require(ViewportEdge.outside(viewport(0f, 1080f, 1f, 1f), 50f, 0f) == null,
                "Zero-sized viewport");
        require(ViewportEdge.outside(viewport(Float.NaN, 1080f, 1f, 1f), 50f, 0f) == null,
                "Invalid viewport");
        ViewportAPI normal = viewport(1920f, 1080f, 1f, 1f);
        require(ViewportEdge.contains(normal, new Vector2f(25f, 40f), 0f), "Off-centre player");
        require(!ViewportEdge.contains(normal, new Vector2f(-10f, 40f), 0f), "Displaced camera");
        require(ViewportEdge.contains(normal, new Vector2f(-10f, 40f), 20f), "Visible fleet radius");

        Map<String, Object> values = new HashMap<>();
        values.put(FishermanInterception.INTERCEPTED_KEY, true);
        values.put(FishermanInterception.CHASING_KEY, true);
        MemoryAPI memory = (MemoryAPI) Proxy.newProxyInstance(MemoryAPI.class.getClassLoader(),
                new Class<?>[]{MemoryAPI.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("unset")) return values.remove(arguments[0]);
                    if (method.getName().equals("getBoolean")) return Boolean.TRUE.equals(values.get(arguments[0]));
                    throw new AssertionError(method);
                });
        CampaignFleetAPI fleet = (CampaignFleetAPI) Proxy.newProxyInstance(
                CampaignFleetAPI.class.getClassLoader(), new Class<?>[]{CampaignFleetAPI.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMemoryWithoutUpdate" -> memory;
                    case "setInteractionTarget" -> null;
                    default -> throw new AssertionError(method);
                });
        FishermanInterception.cancelApproach(fleet);
        require(FishermanInterception.hasIntercepted(fleet), "Cancellation rearmed teleport");
        require(!FishermanInterception.isClosing(fleet), "Cancellation retained chase");
        checkSettlementApproach();
        System.out.println("Fisherman intercept: " + points
                + " edge points, settlement approach and lifecycle checks passed");
    }

    private static void checkSettlementApproach() {
        SectorAPI original = Global.getSector();
        SettingsAPI originalSettings = Global.getSettings();
        Global.setSettings(api(SettingsAPI.class, (self, method, args) -> switch (method.getName()) {
            case "getFloat" -> 1f;
            case "getInt" -> 1;
            case "getBoolean" -> false;
            case "getColor" -> Color.WHITE;
            default -> throw new AssertionError(method);
        }));
        try {
            for (boolean onscreen : new boolean[]{false, true}) {
                Fixture f = new Fixture();
                Global.setSector(f.sector);
                if (onscreen) f.boatPosition.set(100f, 100f);
                Vector2f before = new Vector2f(f.boatPosition);
                require(!OuterReaches.isLegClear(f.system, before, f.playerPosition),
                        "Fixture must put the player inside settlement clearance");
                f.start();
                require(f.assignment == FleetAssignment.INTERCEPT, "Settlement blocked interception");
                require(onscreen ? f.boatPosition.equals(before)
                                : !ViewportEdge.contains(f.view, f.boatPosition, 0f),
                        "Incorrect visible/offscreen placement");
                require(!f.avoided.contains(f.settlement), "Teleport retained settlement avoidance");
                require(f.avoided.contains(f.hazard), "Teleport removed unrelated hazard avoidance");
                f.avoided.add(f.settlement);
                f.work();
                require(f.assignment == FleetAssignment.INTERCEPT, "Route check cancelled tutorial approach");
                require(!f.avoided.contains(f.settlement), "Route check restored settlement avoidance too soon");
                require(f.avoided.contains(f.hazard), "Route check removed unrelated hazard avoidance");

                f.stage = FishingIntro.RODDED;
                f.work();
                require(f.assignment != FleetAssignment.INTERCEPT, "Approach survived tutorial start");
                require(f.avoided.contains(f.settlement), "Normal travel did not restore settlement avoidance");
                require(!FishermanInterception.isClosing(f.boat), "Cancelled approach kept chase flag");
                require(FishermanInterception.hasIntercepted(f.boat), "Cancellation rearmed teleport");
                require(OuterReaches.canTravel(f.system, f.boatPosition, f.target.getLocation()),
                        "Normal travel did not choose a safe escape from the settlement");
            }

            for (String guard : List.of("dialogue", "battle", "offscreen", "invalid viewport")) {
                Fixture f = new Fixture();
                Global.setSector(f.sector);
                switch (guard) {
                    case "dialogue" -> f.showingDialog = true;
                    case "battle" -> f.inBattle = true;
                    case "offscreen" -> f.playerPosition.set(-100f, 0f);
                    case "invalid viewport" -> f.view = null;
                }
                Vector2f before = new Vector2f(f.boatPosition);
                f.start();
                require(f.assignment == null && f.boatPosition.equals(before), "Bypassed " + guard + " guard");
                require(!FishermanInterception.hasIntercepted(f.boat), guard + " guard consumed teleport");
            }
        } finally {
            Global.setSector(original);
            Global.setSettings(originalSettings);
        }
    }

    private static final class Fixture {

        private final Vector2f boatPosition = new Vector2f(-9000f, 600f);
        private final Vector2f playerPosition = new Vector2f(500f, 500f);
        private final Map<String, Object> values = new HashMap<>();
        private final Set<SectorEntityToken> avoided = new HashSet<>();
        private ViewportAPI view = viewport(1920f, 1080f, 1f, 1f);
        private FleetAssignment assignment;
        private SectorEntityToken target;
        private int stage;
        private boolean showingDialog;
        private boolean inBattle;
        private StarSystemAPI system;
        private MarketAPI market;
        private SectorEntityToken settlement;
        private final SectorEntityToken hazard;
        private final CampaignFleetAPI player;
        private final CampaignFleetAPI boat;
        private final SectorAPI sector;

        private Fixture() {
            system = api(StarSystemAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getAllEntities" -> List.of(settlement);
                case "getCenter" -> settlement;
                case "createToken" -> token((float) args[0], (float) args[1]);
                default -> throw new AssertionError(method);
            });
            settlement = token(500f, 600f);
            hazard = token(1700f, 300f);
            market = api(MarketAPI.class, (self, method, args) -> switch (method.getName()) {
                case "isPlanetConditionMarketOnly" -> false;
                case "getPrimaryEntity" -> settlement;
                case "getConnectedEntities" -> Set.of();
                default -> throw new AssertionError(method);
            });
            EconomyAPI economy = api(EconomyAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getMarkets" -> List.of(market);
                default -> throw new AssertionError(method);
            });
            MemoryAPI memory = api(MemoryAPI.class, (self, method, args) -> switch (method.getName()) {
                case "set" -> values.put((String) args[0], args[1]);
                case "unset" -> values.remove(args[0]);
                case "getBoolean" -> Boolean.TRUE.equals(values.get(args[0]));
                case "getInt" -> stage;
                default -> throw new AssertionError(method);
            });
            NavigationModulePlugin nav = api(NavigationModulePlugin.class, (self, method, args) -> {
                switch (method.getName()) {
                    case "avoidEntity" -> avoided.add((SectorEntityToken) args[0]);
                    case "unavoidEntity" -> avoided.remove(args[0]);
                    default -> throw new AssertionError(method);
                }
                return null;
            });
            TacticalModulePlugin tactical = api(TacticalModulePlugin.class, (self, method, args) ->
                    switch (method.getName()) {
                        case "setTarget", "setPriorityTarget", "setTravelDestination" -> null;
                        default -> throw new AssertionError(method);
                    });
            ModularFleetAIAPI ai = api(ModularFleetAIAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getNavModule" -> nav;
                case "getTacticalModule" -> tactical;
                default -> throw new AssertionError(method);
            });
            FleetAssignmentDataAPI orders = api(FleetAssignmentDataAPI.class, (self, method, args) ->
                    switch (method.getName()) {
                        case "getAssignment" -> assignment;
                        case "getTarget" -> target;
                        default -> throw new AssertionError(method);
                    });
            BattleAPI battle = api(BattleAPI.class, (self, method, args) -> { throw new AssertionError(method); });
            player = api(CampaignFleetAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getContainingLocation" -> system;
                case "getLocation" -> playerPosition;
                case "getBattle" -> inBattle ? battle : null;
                case "isExpired" -> false;
                default -> throw new AssertionError(method);
            });
            boat = api(CampaignFleetAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getContainingLocation" -> system;
                case "getLocation" -> boatPosition;
                case "getRadius" -> 20f;
                case "getBattle" -> null;
                case "getMemoryWithoutUpdate" -> memory;
                case "getAI" -> ai;
                case "getCurrentAssignment" -> assignment == null ? null : orders;
                case "setLocation" -> { boatPosition.set((float) args[0], (float) args[1]); yield null; }
                case "setVelocity", "setMoveDestinationOverride", "setInteractionTarget" -> null;
                case "clearAssignments" -> { assignment = null; target = null; yield null; }
                case "addAssignment" -> {
                    assignment = (FleetAssignment) args[0];
                    target = (SectorEntityToken) args[1];
                    yield null;
                }
                default -> throw new AssertionError(method);
            });
            CampaignUIAPI ui = api(CampaignUIAPI.class, (self, method, args) -> switch (method.getName()) {
                case "isShowingDialog" -> showingDialog;
                default -> throw new AssertionError(method);
            });
            sector = api(SectorAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getPlayerFleet" -> player;
                case "getViewport" -> view;
                case "getCampaignUI" -> ui;
                case "getEconomy" -> economy;
                case "getMemoryWithoutUpdate" -> memory;
                default -> throw new AssertionError(method);
            });
            avoided.add(settlement);
            avoided.add(hazard);
        }

        private SectorEntityToken token(float x, float y) {
            Vector2f position = new Vector2f(x, y);
            return api(SectorEntityToken.class, (self, method, args) -> switch (method.getName()) {
                case "getContainingLocation" -> system;
                case "getLocation" -> position;
                case "getRadius" -> 200f;
                case "getMarket" -> self == settlement ? market : null;
                case "isExpired" -> false;
                case "getOrbit" -> null;
                default -> throw new AssertionError(method);
            });
        }

        private void start() {
            new FishermanInterception() {
                void start() { cutOff(boat, player); }
            }.start();
        }

        private void work() {
            new FishermanBehavior(boat) {
                void step() { keepWorking(); }
            }.step();
        }
    }

    private static <T> T api(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (self, method, args) -> switch (method.getName()) {
                    case "equals" -> self == args[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> type.getSimpleName();
                    default -> handler.invoke(self, method, args);
                }));
    }

    private static ViewportAPI viewport(float width, float height, float scaleX, float scaleY) {
        return (ViewportAPI) Proxy.newProxyInstance(ViewportAPI.class.getClassLoader(),
                new Class<?>[]{ViewportAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getLLX", "getLLY" -> 0f;
                    case "getVisibleWidth" -> width * scaleX;
                    case "getVisibleHeight" -> height * scaleY;
                    case "convertScreenWidthToWorldWidth" -> (float) args[0] * scaleX;
                    case "convertScreenHeightToWorldHeight" -> (float) args[0] * scaleY;
                    default -> throw new AssertionError(method);
                });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
