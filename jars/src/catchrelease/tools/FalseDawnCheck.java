package catchrelease.tools;

import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.entities.HauntMineEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.legendary.FalseDawnCorona;
import catchrelease.campaign.fish.legendary.FalseDawnOrbit;
import catchrelease.campaign.fish.legendary.MinefieldModule;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.terrain.FlareManager;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.util.*;

public final class FalseDawnCheck {

    private static int checks;
    private static final Object DEFAULT = new Object();

    private static class Corona extends StarCoronaTerrainPlugin {

        Corona(PlanetAPI star, float width) {
            params = new CoronaParams(star.getRadius() + width,
                    (star.getRadius() + width) * 0.5f, star, 1f, 0f, 1f);
            flareManager = new FlareManager(this);
        }
    }

    private static class Readings extends Aberration {

        static void set(String id, float level) {
            index = new ArrayList<>();
            colonyIndex = new ArrayList<>();
            stampDate = 0;
            stampMarkets = 0;
            stampGates = false;
            readings.put(id, new Reading(level, null));
        }
    }

    private static class Mine extends HauntMineEntityPlugin {

        @Override
        protected void explode(Color color, float radius) {
        }

        @Override
        protected void implode() {
        }

        boolean fired() {
            return triggered;
        }

        float stun() {
            return stunLeft;
        }
    }

    private static class Field extends MinefieldModule {

        Field(StarSystemAPI system) {
            super(system, null);
        }

        void wave() {
            for (int i = 0; i < WAVE_MAX && spawned.size() < MAX_ALIVE; i++) spawnMine();
        }

        boolean clearAt(Vector2f at) {
            return isClear(at);
        }
    }

    private static class SystemData {

        final String id;
        final List<CampaignTerrainAPI> terrain = new ArrayList<>();
        final List<PlanetAPI> stars = new ArrayList<>();
        final List<SectorEntityToken> mines = new ArrayList<>();
        final StarSystemAPI api;

        SystemData(String id) {
            this.id = id;
            api = proxy(StarSystemAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getId", "getBaseName" -> id;
                case "getLocation" -> new Vector2f();
                case "getTerrainCopy" -> terrain;
                case "getPlanets" -> stars;
                case "getEntitiesWithTag" -> mines;
                case "addCustomEntity" -> {
                    SectorEntityToken token = token(this, new Vector2f());
                    mines.add(token);
                    yield token;
                }
                default -> DEFAULT;
            });
        }

        Corona star(float radius, float width, boolean blackHole, boolean pulsar) {
            Vector2f at = new Vector2f(5000f, -2000f);
            PlanetSpecAPI spec = proxy(PlanetSpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "isPulsar" -> pulsar;
                case "getCoronaColor" -> new Color(255, 110, 40);
                default -> DEFAULT;
            });
            PlanetAPI star = proxy(PlanetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> at;
                case "getRadius" -> radius;
                case "isStar" -> true;
                case "isBlackHole" -> blackHole;
                case "getSpec" -> spec;
                default -> DEFAULT;
            });
            Corona corona = new Corona(star, width);
            terrain.add(proxy(CampaignTerrainAPI.class, (p, m, a) ->
                    m.getName().equals("getPlugin") ? corona : DEFAULT));
            stars.add(star);
            return corona;
        }
    }

    private static class Environment {

        final List<StarSystemAPI> systems = new ArrayList<>();
        final Map<String, Object> persistent = new HashMap<>();
        final Vector2f position = new Vector2f();
        final Vector2f velocity = new Vector2f();
        final SystemData local = new SystemData("local");
        final List<FalseDawnCorona> effects = new ArrayList<>();
        boolean paused;
        boolean stopped;
        final CampaignFleetAPI player;

        Environment() {
            if (Global.getSector() != null) throw new IllegalStateException("Run in a standalone JVM");
            CargoAPI cargo = proxy(CargoAPI.class, (p, m, a) -> DEFAULT);
            player = proxy(CampaignFleetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> position;
                case "getRadius" -> 100f;
                case "getAcceleration" -> 300f;
                case "getVelocity" -> new Vector2f(9999f, 9999f);
                case "getVelocityFromMovementModule" -> velocity;
                case "setVelocity" -> { velocity.set((float) a[0], (float) a[1]); yield null; }
                case "goSlowOneFrame" -> { stopped = a != null && a.length == 1 && (boolean) a[0]; yield null; }
                case "getContainingLocation" -> local.api;
                case "getCargo" -> cargo;
                default -> DEFAULT;
            });
            CampaignClockAPI clock = proxy(CampaignClockAPI.class, (p, m, a) ->
                    m.getName().equals("convertToDays") ? (float) a[0] / 10f : DEFAULT);
            MemoryAPI memory = proxy(MemoryAPI.class, (p, m, a) -> DEFAULT);
            EconomyAPI economy = proxy(EconomyAPI.class, (p, m, a) -> DEFAULT);
            ListenerManagerAPI listeners = proxy(ListenerManagerAPI.class, (p, m, a) ->
                    m.getName().equals("getListeners") ? effects : DEFAULT);
            Global.setSector(proxy(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getStarSystems" -> systems;
                case "getPersistentData" -> persistent;
                case "getClock" -> clock;
                case "getEconomy" -> economy;
                case "getMemoryWithoutUpdate" -> memory;
                case "getPlayerFleet" -> player;
                case "getCurrentLocation" -> local.api;
                case "getListenerManager" -> listeners;
                case "isPaused" -> paused;
                default -> DEFAULT;
            }));
            Global.setSettings(proxy(SettingsAPI.class, (p, m, a) -> DEFAULT));
        }
    }

    public static void main(String[] args) {
        Environment environment = new Environment();
        try {
            orbitAndHosts(environment);
            residency(environment);
            flares(environment);
            mines(environment);
            System.out.println("False Dawn: " + checks + " orbit, host, flare, mine and lifecycle checks passed");
        } finally {
            Global.setSector(null);
            Global.setSettings(null);
        }
    }

    private static void orbitAndHosts(Environment environment) {
        SystemData system = new SystemData("binary");
        system.star(1000f, 1000f, false, false);
        Corona corona = system.star(3000f, 2000f, false, false);
        system.star(9000f, 2000f, true, false);
        system.star(8000f, 2000f, false, true);
        system.star(7000f, 100f, false, false);
        require(FalseDawnOrbit.findCorona(system.api) == corona, "Largest usable star includes secondary stars");
        Vector2f center = corona.getParams().relatedEntity.getLocation();
        float inner = FalseDawnOrbit.innerRadius(corona), outer = FalseDawnOrbit.outerRadius(corona);
        for (Vector2f invalid : List.of(new Vector2f(center), new Vector2f(90000f, -60000f))) {
            float distance = Vector2f.sub(FalseDawnOrbit.confine(corona, invalid), center, null).length();
            require(distance >= inner - 0.01f && distance <= outer + 0.01f, "Spawn clamp");
        }
        for (int fps : new int[]{30, 60, 144}) {
            for (float direction : new float[]{-1f, 1f}) {
                Vector2f point = new Vector2f(center.x + inner + 50f, center.y);
                for (int i = 0; i < fps * 120; i++) {
                    Vector2f next = FalseDawnOrbit.step(corona, point, 550f / fps, i / (float) fps, direction);
                    float radius = Vector2f.sub(next, center, null).length();
                    require(radius >= inner - 0.01f && radius <= outer + 0.01f, "Orbit left corona");
                    require(Vector2f.sub(next, point, null).length() <= 550f / fps + 0.02f, "Orbit teleported");
                    point = next;
                }
            }
        }
        SystemData largerStable = new SystemData("stable");
        largerStable.star(7000f, 2000f, false, false);
        SystemData smaller = new SystemData("small");
        smaller.star(1200f, 1000f, false, false);
        environment.systems.addAll(List.of(system.api, largerStable.api, smaller.api));
        Readings.set(system.id, 0.55f);
        Readings.set(largerStable.id, 0.54f);
        Readings.set(smaller.id, 0.9f);
        require(FalseDawnOrbit.pickHost(null).equals(largerStable.id), "Largest star wins regardless of coherence");
        require(FalseDawnOrbit.pickHost(largerStable.id).equals(largerStable.id), "Retain sole largest eligible host");
        Readings.set(largerStable.id, 0.55f);
        require(FalseDawnOrbit.pickHost(null).equals(largerStable.id), "Coherence changes do not change host selection");
        for (StarSystemAPI entry : environment.systems) Readings.set(entry.getId(), 0f);
        require(FalseDawnOrbit.pickHost(null).equals(largerStable.id), "Stable systems remain eligible");
        environment.systems.clear();
        environment.systems.add(new SystemData("starless").api);
        require(FalseDawnOrbit.pickHost(null) == null, "Still requires a usable corona");
    }

    private static void residency(Environment environment) {
        SystemData system = new SystemData("residency");
        system.star(3000f, 2000f, false, false);
        FishEntityPlugin surfaced = new FishEntityPlugin() {
            @Override public String getFishId() { return "false_dawn"; }
        };
        BuriedMoteEntityPlugin buried = new BuriedMoteEntityPlugin() {
            @Override public String getFishId() { return "false_dawn"; }
        };
        for (var plugin : List.of(surfaced, buried)) {
            boolean[] expired = {false};
            Vector2f location = new Vector2f(8500f, -2000f);
            SectorEntityToken mote = proxy(CustomCampaignEntityAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> location;
                case "getContainingLocation" -> system.api;
                case "getCustomPlugin" -> plugin;
                case "setLocation" -> { location.set((float) a[0], (float) a[1]); yield null; }
                case "isExpired" -> expired[0];
                case "setExpired" -> { expired[0] = (boolean) a[0]; yield null; }
                default -> DEFAULT;
            });
            system.mines.add(mote);
            LegendaryChases.Chase state = LegendaryChases.getState("false_dawn");
            state.systemId = system.id;
            state.caught = false;
            require(FalseDawnOrbit.hasMote(system.api), "Live specimen prevents duplicate spawn");
            FalseDawnOrbit.advance(mote, "false_dawn", 10f, 1f);
            require(!expired[0], "Current residency remains alive");
            state.systemId = "elsewhere";
            FalseDawnOrbit.advance(mote, "false_dawn", 10f, 1f);
            require(expired[0] && !FalseDawnOrbit.hasMote(system.api), "Relocated specimen expires");
            expired[0] = false;
            state.systemId = system.id;
            state.caught = true;
            FalseDawnOrbit.advance(mote, "false_dawn", 10f, 1f);
            require(expired[0], "Captured specimen expires");
            system.mines.clear();
        }
    }

    private static void flares(Environment environment) {
        Corona corona = new SystemData("flare").star(2000f, 1800f, false, false);
        FalseDawnCorona effect = new FalseDawnCorona();
        environment.effects.add(effect);
        FlareManager.Flare natural = new FlareManager.Flare();
        natural.colors.add(Color.ORANGE);
        corona.getFlareManager().getFlares().add(natural);
        effect.replenish(corona);
        effect.replenish(corona);
        require(corona.getFlareManager().getFlares().size() == 4, "Bounded owned flare queue");
        require(natural.colors.get(0).equals(Color.ORANGE), "Natural flare unchanged");
        require(!corona.getFlareManager().getFlares().get(1).colors.get(0).equals(Color.ORANGE), "Contrast hue");
        FalseDawnCorona.beforeSave();
        require(corona.getFlareManager().getFlares().equals(List.of(natural)), "Only natural flares enter save");
        corona.getFlareManager().getFlares().clear();
        effect.replenish(corona);
        for (int frame = 0; frame < 1200; frame++) {
            corona.getFlareManager().advance(1f / 60f);
            effect.replenish(corona);
            require(corona.getFlareManager().getFlares().size() == 3, "Queue remains bounded during flare turnover");
        }
        effect.reportCurrentLocationChanged(null, null);
        require(corona.getFlareManager().getFlares().isEmpty(), "Departure removes owned flares");
    }

    private static void mines(Environment environment) {
        Mine blast = new Mine();
        blast.init(token(environment.local, new Vector2f(100f, 0f)), new HauntMineEntityPlugin.Params(HauntMineEntityPlugin.Kind.BLAST));
        blast.advance(1.99f);
        require(!blast.fired(), "Full arming delay despite randomized blink");
        blast.advance(0.02f);
        require(blast.fired() && environment.velocity.x < -650f, "Armed push reaches actual movement velocity");
        Mine stun = new Mine();
        stun.init(token(environment.local, new Vector2f(100f, 0f)), new HauntMineEntityPlugin.Params(HauntMineEntityPlugin.Kind.INTERCEPT));
        stun.detonate();
        require(environment.velocity.length() == 0f && environment.stopped, "Immediate stun");
        environment.paused = true;
        stun.advance(100f);
        require(stun.stun() == HauntMineEntityPlugin.INTERCEPT_STUN_SECONDS, "Paused stun timer");
        environment.paused = false;
        for (int i = 0; i < 21; i++) {
            environment.velocity.set(300f, 20f);
            environment.stopped = false;
            stun.advance(0.1f);
            if (i < 19) require(environment.stopped && environment.velocity.length() == 0f, "Stun holds against steering");
        }
        environment.stopped = false;
        stun.advance(0.1f);
        require(stun.stun() == 0f && !environment.stopped, "Stun releases without a persistent modifier");
        for (Vector2f at : List.of(new Vector2f(100f, 0f), new Vector2f(0f, -200f),
                new Vector2f(240f, 320f))) {
            Mine push = new Mine();
            push.init(token(environment.local, at), new HauntMineEntityPlugin.Params(HauntMineEntityPlugin.Kind.BLAST));
            environment.position.set(0f, 0f);
            Vector2f start = new Vector2f(-400f, 90f);
            environment.velocity.set(start);
            push.detonate();
            Vector2f pushImpulse = Vector2f.sub(environment.velocity, start, null);
            Mine pull = new Mine();
            pull.init(token(environment.local, at), new HauntMineEntityPlugin.Params(HauntMineEntityPlugin.Kind.IMPLOSION));
            environment.velocity.set(start);
            pull.detonate();
            Vector2f pullImpulse = Vector2f.sub(environment.velocity, start, null);
            require(Math.abs(pullImpulse.length() - HauntMineEntityPlugin.BLAST_PUSH_SPEED) < 0.01f,
                    "Pull delivers full strength in one impulse");
            require(Vector2f.add(pushImpulse, pullImpulse, null).length() < 0.01f,
                    "Push and pull impulses are exact opposites");
            require(Vector2f.dot(pullImpulse, at) > 0f, "Pull points towards the mine");
            Vector2f after = new Vector2f(environment.velocity);
            pull.detonate();
            require(Vector2f.sub(environment.velocity, after, null).length() == 0f, "Cannot pull twice");
            for (int fps : new int[]{30, 60, 144}) {
                environment.velocity.set(start);
                for (int i = 0; i < fps * 4; i++) pull.advance(1f / fps);
                require(Vector2f.sub(environment.velocity, start, null).length() == 0f,
                        "No ongoing attraction at " + fps + " Hz");
            }
        }
        for (Vector2f at : List.of(new Vector2f(), new Vector2f(701f, 0f))) {
            Mine pull = new Mine();
            pull.init(token(environment.local, at), new HauntMineEntityPlugin.Params(HauntMineEntityPlugin.Kind.IMPLOSION));
            environment.velocity.set(100f, 20f);
            pull.detonate();
            require(environment.velocity.x == 100f && environment.velocity.y == 20f,
                    "No undefined centre impulse or out-of-range pull");
        }
        environment.position.set(0f, 0f);
        Field field = new Field(environment.local.api);
        require(!field.clearAt(new Vector2f(450f, 0f)), "Player spawn clearance");
        for (int wave = 0; wave < 8; wave++) field.wave();
        require(!environment.local.mines.isEmpty(), "Field still produces mines");
        for (int i = 0; i < environment.local.mines.size(); i++) {
            for (int j = i + 1; j < environment.local.mines.size(); j++) {
                float gap = Vector2f.sub(environment.local.mines.get(i).getLocation(),
                        environment.local.mines.get(j).getLocation(), null).length() - 2f * HauntMineEntityPlugin.TRIGGER_RANGE;
                require(gap >= 350f - 0.01f, "Passage remains between waves");
            }
        }
    }

    private static SectorEntityToken token(SystemData system, Vector2f location) {
        return proxy(CustomCampaignEntityAPI.class, (p, m, a) -> switch (m.getName()) {
            case "getLocation" -> location;
            case "getContainingLocation" -> system.api;
            case "setLocation" -> { location.set((float) a[0], (float) a[1]); yield null; }
            default -> DEFAULT;
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return FishingParityChecks.proxy(type, (p, m, a) -> {
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            Object result = handler.invoke(p, m, a);
            if (result != DEFAULT) return result;
            Class<?> returns = m.getReturnType();
            if (returns == boolean.class) return false;
            if (returns == int.class) return 0;
            if (returns == long.class) return 0L;
            if (returns == float.class) return 0f;
            if (returns == double.class) return 0d;
            if (List.class.isAssignableFrom(returns)) return new ArrayList<>();
            if (Map.class.isAssignableFrom(returns)) return new HashMap<>();
            return null;
        });
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
