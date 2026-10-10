package catchrelease.tools;

import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.HauntMineEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.legendary.FalseDawnCorona;
import catchrelease.campaign.fish.legendary.FalseDawnOrbit;
import catchrelease.campaign.fish.legendary.MinefieldModule;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.campaign.fish.legendary.DawnShieldTransfer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.terrain.FlareManager;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
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

        int bursts;

        @Override
        protected void explode(Color color, float radius) {
            bursts++;
        }

        @Override
        protected void implode() {
            bursts++;
        }

        boolean fired() {
            return triggered;
        }

        DawnShieldTransfer transfer() {
            return transfer;
        }
    }

    private static class Field extends MinefieldModule {

        Field(StarSystemAPI system) {
            super(system, null);
        }

        void wave() {
            for (int i = 0; i < WAVE_MAX && liveMines() < MAX_ALIVE; i++) spawnMine();
        }

        boolean clearAt(Vector2f at) {
            return isClear(at);
        }

        void own(SectorEntityToken token) {
            track(token);
        }

        int count() {
            return liveMines();
        }
    }

    public interface TextToken extends CustomCampaignEntityAPI {

        List<?> getFloatingText();
    }

    private static class Token {

        final Vector2f at;
        final Set<String> tags = new HashSet<>();
        final List<String> notices = new ArrayList<>();
        final CustomCampaignEntityAPI api;
        Object plugin;
        boolean expired;
        boolean removed;

        Token(SystemData system, Vector2f at) {
            this.at = new Vector2f(at);
            api = proxy(TextToken.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> this.at;
                case "getContainingLocation" -> system.api;
                case "setLocation" -> { this.at.set((float) a[0], (float) a[1]); yield null; }
                case "getCustomPlugin" -> plugin;
                case "isExpired" -> expired;
                case "setExpired" -> { expired = (boolean) a[0]; yield null; }
                case "isAlive" -> !expired && !removed;
                case "hasTag" -> tags.contains(a[0]);
                case "addTag" -> { tags.add((String) a[0]); yield null; }
                case "removeTag" -> { tags.remove(a[0]); yield null; }
                case "isVisibleToPlayerFleet" -> true;
                case "addFloatingText" -> { notices.add((String) a[0]); yield null; }
                case "getFloatingText" -> null;
                default -> DEFAULT;
            });
        }
    }

    private static class Fish extends FishEntityPlugin {

        final FishSpec spec = new FishSpec();
        final Token token;
        boolean phantom;

        Fish(SystemData system) {
            spec.id = LegendaryShields.DAWN_SPECIES;
            spec.rarity = FishRarity.LEGENDARY;
            token = new Token(system, new Vector2f(6000f, 0f));
            token.plugin = this;
            token.tags.add(MOTE_TAG);
            entity = token.api;
            system.mines.add(entity);
        }

        @Override public FishSpec getFishSpec() { return spec; }
        @Override public String getFishId() { return spec.id; }
        @Override public boolean isPhantom() { return phantom; }
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
                case "getEntitiesWithTag" -> mines.stream().filter(e -> e.hasTag((String) a[0])).toList();
                case "removeEntity" -> { mines.remove(a[0]); yield null; }
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
        LocationAPI current = local.api;
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
                case "getContainingLocation" -> current;
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
                case "getCurrentLocation" -> current;
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
            shieldMines(environment);
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
                case "hasTag" -> (plugin instanceof FishEntityPlugin ? FishEntityPlugin.MOTE_TAG
                        : BuriedMoteEntityPlugin.BURIED_TAG).equals(a[0]);
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
        for (HauntMineEntityPlugin.Kind kind : HauntMineEntityPlugin.Kind.values()) {
            for (int fps : new int[]{30, 60, 144}) {
                Mine timed = new Mine();
                timed.init(token(environment.local, new Vector2f(1500f, 0f)),
                        new HauntMineEntityPlugin.Params(kind));
                environment.paused = true;
                timed.advance(100f);
                require(!timed.fired(), "Pause does not age " + kind);
                environment.paused = false;
                for (int frame = 0; frame < fps * 10 - 1; frame++) timed.advance(1f / fps);
                require(!timed.fired(), "Mine survives until ten seconds at " + fps + " Hz");
                timed.advance(2f / fps);
                require(timed.fired() && timed.bursts == 1, "Mine expires with one burst: " + kind);
                timed.detonate();
                timed.advance(20f);
                require(timed.bursts == 1, "Expiry cannot detonate twice");
            }
        }
        Mine blast = new Mine();
        blast.init(token(environment.local, new Vector2f(100f, 0f)), new HauntMineEntityPlugin.Params(HauntMineEntityPlugin.Kind.BLAST));
        blast.advance(1.99f);
        require(!blast.fired(), "Full arming delay despite randomized blink");
        blast.advance(0.02f);
        require(blast.fired() && environment.velocity.x < -650f, "Armed push reaches actual movement velocity");
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
        require(field.count() <= MinefieldModule.MAX_ALIVE, "Live mine cap");
        for (int i = 0; i < environment.local.mines.size(); i++) {
            for (int j = i + 1; j < environment.local.mines.size(); j++) {
                float gap = Vector2f.sub(environment.local.mines.get(i).getLocation(),
                        environment.local.mines.get(j).getLocation(), null).length() - 2f * HauntMineEntityPlugin.TRIGGER_RANGE;
                require(gap >= 350f - 0.01f, "Passage remains between waves");
            }
        }
        field.cleanup();
    }

    private static void shieldMines(Environment env) {
        env.position.set(0f, 0f);
        Fish fish = new Fish(env.local);
        LegendaryChases.Chase state = LegendaryChases.getState("false_dawn");
        state.caught = false;
        state.provoked = false;
        state.shieldUnits = -1;
        require(LegendaryShields.getDawnCharges() == 1 && LegendaryShields.isShielded(fish),
                "False Dawn starts with one stored charge");
        require(LegendaryShields.getShieldColor(fish).equals(HauntMineEntityPlugin.Kind.SHIELD.color),
                "Shield and mines share green");
        require(LegendaryShields.onHarpoonContact(fish.getMote(), false) == LegendaryShields.HitResult.DEFLECTED
                        && state.provoked && state.shieldUnits == 0,
                "Wake-up hit spends the only starting charge");
        fish.restoreBaseShield();
        require(!LegendaryShields.isShielded(fish)
                        && LegendaryShields.onHarpoonContact(fish.getMote(), false) == LegendaryShields.HitResult.NONE,
                "Base shield cannot grant another deflection");
        fish.setHeld(true);
        require(LegendaryShields.onFailedCatch(fish.getMote()) && !fish.isHeld() && state.shieldUnits == 0,
                "Failed catch releases fish without recharging");
        Fish respawned = new Fish(env.local);
        require(!LegendaryShields.isShielded(respawned), "A fresh mote keeps spent charges");
        env.local.mines.remove(respawned.getMote());
        fish.token.notices.clear();

        for (int fps : new int[]{30, 60, 144}) {
            state.shieldUnits = 0;
            fish.token.at.set(6000f, 0f);
            Token origin = new Token(env.local, new Vector2f(100f, 0f));
            Mine mine = shieldMine(origin);
            env.velocity.set(300f, 25f);
            env.stopped = false;
            mine.advance(HauntMineEntityPlugin.ARM_SECONDS);
            require(mine.fired() && mine.transfer() != null && state.shieldUnits == 0,
                    "Collision launches a courier, not an instant charge");
            require(env.velocity.x == 300f && env.velocity.y == 25f && !env.stopped,
                    "Green mines do not interdict or slow the fleet");
            require(!origin.tags.contains(HauntMineEntityPlugin.MINE_TAG), "Spent mine is no longer harpoonable");
            env.paused = true;
            mine.advance(100f);
            require(state.shieldUnits == 0 && !origin.expired, "Pause freezes the courier");
            env.paused = false;
            for (int frame = 0; frame < fps * 2; frame++) {
                fish.token.at.y += 300f / fps;
                mine.advance(1f / fps);
                if (state.shieldUnits > 0) break;
            }
            require(state.shieldUnits == 1 && LegendaryShields.getStackedRings(fish) == 1,
                    "Courier catches a moving, unlit fish at " + fps + " Hz");
            require(origin.notices.equals(List.of("The False Dawn brightens")) && fish.token.notices.isEmpty(),
                    "Gain notice stays at the mine, never at the fish");
            require(origin.at.equals(new Vector2f(100f, 0f)) && !origin.expired,
                    "Notice anchor survives the arrival");
            require(mine.getRenderRange() > 5500f, "Courier stays in render range away from its origin");
            mine.detonate();
            mine.advance(3f);
            require(state.shieldUnits == 1 && origin.expired && mine.bursts == 1,
                    "Courier awards once and releases its spent mine");
        }

        state.shieldUnits = 1;
        Mine first = shieldMine(new Token(env.local, new Vector2f(100f, 0f)));
        Token excessOrigin = new Token(env.local, new Vector2f(100f, 0f));
        Mine second = shieldMine(excessOrigin);
        first.advance(2f);
        second.advance(2f);
        first.advance(2f);
        second.advance(2f);
        require(state.shieldUnits == 2 && LegendaryShields.getStackedRings(fish) == 2,
                "Overlapping arrivals cannot exceed two charges");
        require(excessOrigin.notices.isEmpty(), "No gain notice at cap");
        require(LegendaryShields.onHarpoonContact(fish.getMote(), true) == LegendaryShields.HitResult.DEFLECTED
                        && state.shieldUnits == 1, "Explosive hit also spends just one charge");
        LegendaryShields.onHarpoonContact(fish.getMote(), false);
        require(!LegendaryShields.isShielded(fish) && LegendaryShields.getStackedRings(fish) == 0,
                "Empty charge ledger removes collision shield and rings");

        for (boolean expiry : new boolean[]{false, true}) {
            Token origin = new Token(env.local, new Vector2f(expiry ? 1500f : 100f, 0f));
            Mine mine = shieldMine(origin);
            if (expiry) mine.advance(10f); else mine.detonate();
            mine.advance(3f);
            require(state.shieldUnits == 0 && mine.transfer() == null && origin.notices.isEmpty()
                            && mine.bursts == 1, "Harpoons and natural expiry never recharge");
        }
        Token relocationOrigin = new Token(env.local, new Vector2f(100f, 0f));
        Mine relocating = shieldMine(relocationOrigin);
        relocating.advance(2f);
        relocating.advance(0.1f);
        fish.token.tags.add(Tags.FADING_OUT_AND_EXPIRING);
        Fish relocated = new Fish(env.local);
        relocated.token.at.set(3000f, 1000f);
        relocating.advance(1f);
        require(state.shieldUnits == 1 && relocationOrigin.notices.size() == 1,
                "Courier follows explosive relocation instead of the fading old mote");
        require(relocating.getRenderRange() < 4500f, "Arrival is at the replacement's position");
        env.local.mines.remove(relocated.getMote());
        fish.token.tags.remove(Tags.FADING_OUT_AND_EXPIRING);
        for (String invalid : List.of("caught", "expired", "removed", "phantom", "other species", "departure", "cleanup")) {
            state.shieldUnits = 0;
            Token origin = new Token(env.local, new Vector2f(100f, 0f));
            Mine mine = shieldMine(origin);
            mine.advance(2f);
            mine.advance(0.1f);
            Field field = new Field(env.local.api);
            field.own(origin.api);
            require(field.count() == 0, "Couriers and notice anchors do not fill the live mine cap");
            switch (invalid) {
                case "caught" -> state.caught = true;
                case "expired" -> fish.token.expired = true;
                case "removed" -> fish.token.removed = true;
                case "phantom" -> fish.phantom = true;
                case "other species" -> fish.spec.id = "lantern_jack";
                case "departure" -> env.current = new SystemData("elsewhere").api;
                case "cleanup" -> field.cleanup();
            }
            mine.advance(3f);
            require(state.shieldUnits == 0 && origin.notices.isEmpty(), "No late charge after " + invalid);
            state.caught = false;
            fish.token.expired = false;
            fish.token.removed = false;
            fish.phantom = false;
            fish.spec.id = "false_dawn";
            env.current = env.local.api;
            field.cleanup();
        }
        env.local.mines.clear();
    }

    private static Mine shieldMine(Token origin) {
        Mine mine = new Mine();
        origin.plugin = mine;
        mine.init(origin.api, new HauntMineEntityPlugin.Params(HauntMineEntityPlugin.Kind.SHIELD));
        return mine;
    }

    private static SectorEntityToken token(SystemData system, Vector2f location) {
        Token token = new Token(system, location);
        token.tags.add(HauntMineEntityPlugin.MINE_TAG);
        return token.api;
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
