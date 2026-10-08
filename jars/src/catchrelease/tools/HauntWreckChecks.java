package catchrelease.tools;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.legendary.FakeWrecksModule;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class HauntWreckChecks {

    private static int checks;

    static final class Entity {

        final Vector2f position = new Vector2f();
        final Vector2f velocity = new Vector2f();
        final Set<String> tags = new HashSet<>(List.of(Tags.HAS_INTERACTION_DIALOG, Tags.SALVAGEABLE));
        final Map<String, Object> memory = new HashMap<>();
        final List<EveryFrameScript> scripts = new ArrayList<>();
        final StatBonus detected = new StatBonus();
        final CustomCampaignEntityAPI api;
        Object params;
        LocationAPI location;
        float radius;
        float brightness = 1f;
        boolean expired;
        boolean removed;

        Entity(LocationAPI system) {
            location = system;
            MemoryAPI mem = proxy(MemoryAPI.class, (p, m, a) -> switch (m.getName()) {
                case "set" -> { memory.put((String) a[0], a[1]); yield null; }
                default -> throw new AssertionError(m);
            });
            api = proxy(CustomCampaignEntityAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> position;
                case "getVelocity" -> velocity;
                case "getContainingLocation" -> location;
                case "setLocation" -> { position.set((float) a[0], (float) a[1]); yield null; }
                case "setRadius" -> { radius = (float) a[0]; yield null; }
                case "getRadius" -> radius;
                case "getMemoryWithoutUpdate" -> mem;
                case "getDetectedRangeMod" -> detected;
                case "addTag" -> { tags.add((String) a[0]); yield null; }
                case "removeTag" -> { tags.remove(a[0]); yield null; }
                case "hasTag" -> tags.contains(a[0]);
                case "addScript" -> { scripts.add((EveryFrameScript) a[0]); yield null; }
                case "isExpired" -> expired;
                case "isAlive" -> !expired;
                case "setExpired" -> { expired = (boolean) a[0]; yield null; }
                case "getSensorFaderBrightness" -> brightness;
                case "forceSensorFaderBrightness" -> { brightness = (float) a[0]; yield null; }
                case "setSensorProfile", "setDiscoverable", "setExtendedDetectedAtRange",
                     "setAlwaysUseSensorFaderBrightness", "setDiscoveryXP",
                     "setDetectionRangeDetailsOverrideMult", "forceSensorFaderOut" -> null;
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p == a[0];
                case "toString" -> "Haunt check entity";
                default -> throw new AssertionError(m);
            });
        }

        void fade(float amount) {
            for (EveryFrameScript script : new ArrayList<>(scripts)) script.advance(amount);
        }
    }

    static final class Environment implements AutoCloseable {

        final List<Entity> entities = new ArrayList<>();
        final StarSystemAPI system;
        final CampaignFleetAPI player;
        final Vector2f playerPosition = new Vector2f();
        final Vector2f playerVelocity = new Vector2f();
        long timestamp;
        SalvageEntityGenDataSpec salvage;

        Environment() throws Exception {
            if (Global.getSector() != null || Global.getSettings() != null) {
                throw new IllegalStateException("Run outside Starsector.");
            }
            system = proxy(StarSystemAPI.class, (p, m, a) -> switch (m.getName()) {
                case "addCustomEntity" -> {
                    Entity entity = new Entity((StarSystemAPI) p);
                    entity.params = a.length > 4 ? a[4] : null;
                    entities.add(entity);
                    yield entity.api;
                }
                case "removeEntity" -> {
                    entities.stream().filter(e -> e.api == a[0]).forEach(e -> { e.removed = true; e.location = null; });
                    yield null;
                }
                case "getFleets" -> Collections.emptyList();
                default -> throw new AssertionError(m);
            });
            player = proxy(CampaignFleetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> playerPosition;
                case "getVelocity" -> playerVelocity;
                case "getContainingLocation" -> system;
                case "getRadius" -> 20f;
                case "isAlive" -> true;
                default -> throw new AssertionError(m);
            });
            CampaignClockAPI clock = proxy(CampaignClockAPI.class, (p, m, a) -> switch (m.getName()) {
                case "convertToDays" -> (float) a[0] / 10f;
                case "getTimestamp" -> timestamp;
                case "getElapsedDaysSince" -> (timestamp - (long) a[0]) / 10000f;
                default -> throw new AssertionError(m);
            });
            Global.setSettings(proxy(SettingsAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getSpec" -> salvage;
                case "getAllVariantIds" -> List.of("combat", "freighter", "fighter", "station", "module");
                case "getVariant" -> variant((String) a[0]);
                case "getBaseTravelSpeed" -> 100f;
                case "getSpeedPerBurnLevel" -> 20f;
                case "getFloat" -> 1f;
                case "getInt" -> 1;
                case "getBoolean", "isDevMode" -> false;
                case "getColor" -> java.awt.Color.WHITE;
                case "getAngleInDegreesFast" -> {
                    Vector2f from = a.length == 1 ? new Vector2f() : (Vector2f) a[0];
                    Vector2f to = (Vector2f) a[a.length - 1];
                    yield (float) Math.toDegrees(Math.atan2(to.y - from.y, to.x - from.x));
                }
                default -> throw new AssertionError(m);
            }));
            salvage = new SalvageEntityGenDataSpec(
                    new JSONObject().put("id", "wreck").put("stationRole", ""));
            Global.setSector(proxy(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getPlayerFleet" -> player;
                case "getClock" -> clock;
                case "getFaction" -> proxy(FactionAPI.class, (fp, fm, fa) -> switch (fm.getName()) {
                    case "pickRandomShipName" -> "Test wreck";
                    default -> throw new AssertionError(fm);
                });
                default -> throw new AssertionError(m);
            }));
        }

        private ShipVariantAPI variant(String id) {
            ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getHints" -> id.equals("module") ? EnumSet.of(ShipHullSpecAPI.ShipTypeHints.MODULE)
                        : EnumSet.noneOf(ShipHullSpecAPI.ShipTypeHints.class);
                default -> throw new AssertionError(m);
            });
            return proxy(ShipVariantAPI.class, (p, m, a) -> switch (m.getName()) {
                case "isFighter" -> id.equals("fighter");
                case "isStation" -> id.equals("station");
                case "getHullSpec" -> hull;
                default -> throw new AssertionError(m);
            });
        }

        @Override
        public void close() {
            Global.setSettings(null);
            Global.setSector(null);
        }
    }

    private static final class Wrecks extends FakeWrecksModule {

        Wrecks(StarSystemAPI system) { super(system, new FishSpec()); random.setSeed(10); }
        void spawn() { spawnWreck(); }
        float timer() { return spawnTimer; }
        void expireNext(Entity wreck) { life.put(wreck.api, 0f); }
        int tracked() { return spawned.size(); }
        List<String> pool() { return variants; }
    }

    public static void main(String[] args) throws Exception {
        try (Environment env = new Environment()) {
            Wrecks module = new Wrecks(env.system);
            module.advance(4.99f);
            require(env.entities.isEmpty(), "No early wreck");
            module.advance(0.02f);
            require(env.entities.size() == 3 && module.timer() >= 10f && module.timer() <= 18f, "Initial group and repeat timing");
            require(module.pool().equals(List.of("combat", "freighter")), "Any ship type, no fighters or station modules");
            Entity firstSpawned = env.entities.get(0);
            require(firstSpawned.position.length() >= 599f && firstSpawned.position.length() <= 1101f, "Spawn range");
            require(firstSpawned.detected.computeEffective(0f) >= 2500f, "Visible wrecks");
            require(firstSpawned.memory.containsKey(MemFlags.SALVAGE_SEED)
                    && ((DerelictShipEntityPlugin.DerelictShipData) firstSpawned.params).canHaveExtraCargo, "Vanilla salvage generation");
            require(!firstSpawned.tags.contains(Tags.NON_CLICKABLE)
                    && !firstSpawned.tags.contains(Tags.HAS_INTERACTION_DIALOG)
                    && !firstSpawned.tags.contains(Tags.SALVAGEABLE), "Selectable but no early salvage");
            module.spawn();
            Entity approached = env.entities.get(env.entities.size() - 1);
            approached.position.set(100f, 0f);
            module.advance(0f);
            require(approached.tags.contains(Tags.SALVAGEABLE), "First approached is real even if spawned later");
            module.advance(1f);
            require(approached.scripts.isEmpty(), "Genuine wreck does not fade on approach");
            int real = 0;
            for (int i = 0; i < 1000; i++) {
                module.spawn();
                Entity wreck = env.entities.get(env.entities.size() - 1);
                wreck.position.set(50f, 0f);
                module.advance(0f);
                if (wreck.tags.contains(Tags.SALVAGEABLE)) real++;
                else {
                    wreck.fade(0.25f);
                    require(!wreck.expired && Math.abs(wreck.brightness - 0.5f) < 0.001f, "Half-second fade midpoint");
                    wreck.fade(0.26f);
                    require(wreck.expired, "Phantom expires after fade");
                }
                wreck.expired = true;
            }
            require(real >= 70 && real <= 130, "Later salvage frequency near ten percent: " + real);
            module.expireNext(approached);
            module.advance(0f);
            approached.fade(0.51f);
            require(approached.expired, "Real wreck lifetime expires too");
            module.cleanup();
            require(module.tracked() == 0 && firstSpawned.expired && firstSpawned.removed, "Cleanup removes unresolved wrecks");
            module = new Wrecks(env.system);
            for (int i = 0; i < FakeWrecksModule.MAX_ALIVE; i++) module.spawn();
            int before = env.entities.size();
            module.advance(30f);
            require(env.entities.size() == before && module.tracked() == 25, "Active cap");
            module.cleanup();
            module = new Wrecks(env.system);
            for (int i = 0; i < FakeWrecksModule.MAX_ALIVE - 1; i++) module.spawn();
            module.advance(5f);
            require(module.tracked() == FakeWrecksModule.MAX_ALIVE, "Last group respects remaining capacity");
            module.cleanup();
            module = new Wrecks(env.system);
            module.setIntensity(0.5f);
            before = env.entities.size();
            module.advance(20f);
            require(env.entities.size() == before, "Fading haunt cannot add a group");
        }
        System.out.println("Haunt wrecks: " + checks + " checks passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
