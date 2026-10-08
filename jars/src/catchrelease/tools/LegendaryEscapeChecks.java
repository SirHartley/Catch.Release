package catchrelease.tools;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.legendary.*;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.util.*;

public final class LegendaryEscapeChecks {

    private static int checks;

    static class Fish extends FishEntityPlugin {

        final FishSpec spec = new FishSpec();
        final Vector2f at = new Vector2f(500f, 500f);
        final boolean phantom;
        final SectorEntityToken anchor;
        boolean expired;

        Fish(Environment env, String id, boolean phantom, SectorEntityToken anchor) {
            spec.id = id;
            spec.rarity = "quorum_shard".equals(id) ? FishRarity.RARE : FishRarity.LEGENDARY;
            this.phantom = phantom;
            this.anchor = anchor;
            entity = api(CustomCampaignEntityAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> at;
                case "setLocation" -> { at.set((float) a[0], (float) a[1]); yield null; }
                case "getCustomPlugin" -> this;
                case "getContainingLocation" -> env.system;
                case "isExpired" -> expired;
                case "setExpired" -> { expired = (boolean) a[0]; yield null; }
                case "addTag", "addFloatingText" -> null;
                default -> throw new AssertionError(m);
            });
            env.fish.add(this);
        }

        @Override public FishSpec getFishSpec() { return spec; }
        @Override public boolean isPhantom() { return phantom; }
        @Override public boolean isDecoy() { return anchor != null; }
        @Override public SectorEntityToken getDecoyAnchor() { return anchor; }
        @Override public boolean isLampVisible() { return true; }
        @Override protected void advanceLampFade(float amount) { }
        @Override protected void advanceShieldLens() { }
        @Override protected void advanceTrail() { }
        void diveTime(float amount) { advanceDive(amount); }
        void swim(Vector2f next) { moveTo(next); }
    }

    static class Buried extends BuriedMoteEntityPlugin {

        final FishSpec spec;
        Buried(Fish fish) {
            entity = (CustomCampaignEntityAPI) fish.getMote();
            spec = fish.spec;
            fishId = spec.id;
            heading = 0f;
            headingLeft = Float.MAX_VALUE;
        }
        @Override public FishSpec getFishSpec() { return spec; }
        @Override protected float getWanderMult() { return 0f; }
    }

    static class Corona extends StarCoronaTerrainPlugin {

        Corona(PlanetAPI star, float outer) {
            params = new CoronaParams(outer - star.getRadius(),
                    (outer + star.getRadius()) * 0.5f, star, 0f, 0f, 0f);
        }
    }

    static class Stream extends SlipstreamTerrainPlugin2 {

        Stream(SlipstreamParams2 p) { params = p; }
        @Override public void recompute() { }
    }

    static class Terrain {

        final Vector2f at = new Vector2f();
        final Stream stream;
        final CampaignTerrainAPI token;
        boolean expired;
        boolean removed;

        Terrain(Environment env, SlipstreamTerrainPlugin2.SlipstreamParams2 params) {
            stream = new Stream(params);
            token = api(CampaignTerrainAPI.class, (p, m, a) -> switch (m.getName()) {
                case "setLocation" -> { at.set((float) a[0], (float) a[1]); yield null; }
                case "getLocation" -> at;
                case "getPlugin" -> stream;
                case "addTag" -> null;
                case "isExpired" -> expired;
                case "setExpired" -> { expired = (boolean) a[0]; yield null; }
                case "getContainingLocation" -> env.system;
                default -> throw new AssertionError(m);
            });
        }
    }

    static class Slip extends SlipDashModule {

        Slip(StarSystemAPI system, FishSpec spec) { super(system, spec); }
        float remaining() { return dashLeft; }
        void step(FishEntityPlugin fish, float amount) { steer(fish, amount); }
    }

    static class Manta extends MantaFormationModule {

        boolean black;
        Manta(StarSystemAPI system, FishSpec spec) { super(system, spec); }
        @Override protected void showBlackout(boolean visible) { black = visible; }
        int index() { return realSlot; }
        float remaining() { return blackoutLeft; }
        void tick(float amount) { advanceBlackout(amount); }
        List<Vector2f> positions() {
            List<Vector2f> positions = new ArrayList<>();
            for (SectorEntityToken token : slots) positions.add(new Vector2f(token.getLocation()));
            return positions;
        }
    }

    static class Haunt extends LegendaryHaunt {

        HauntModule module;
        @Override protected List<HauntModule> buildModules(FishSpec spec, StarSystemAPI system) {
            module = "slipstream_moray".equals(spec.id) ? new Slip(system, spec) : new Manta(system, spec);
            return List.of(module);
        }
        void close() { stop(); }
    }

    static class Shell extends QuorumShellGame {

        static void reset() { states.clear(); }
    }

    static class Environment implements AutoCloseable {

        final List<Fish> fish = new ArrayList<>();
        final List<Terrain> terrain = new ArrayList<>();
        final List<PlanetAPI> stars = new ArrayList<>();
        final List<CampaignTerrainAPI> coronas = new ArrayList<>();
        final Map<String, Object> persistent = new HashMap<>();
        final List<EveryFrameScript> scripts = new ArrayList<>();
        final StarSystemAPI system;
        final Haunt haunt = new Haunt();

        Environment() {
            if (Global.getSector() != null) throw new IllegalStateException("Run outside Starsector.");
            Global.setSettings(api(SettingsAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getFloat" -> 1f;
                case "getBaseTravelSpeed" -> 100f;
                case "getSpeedPerBurnLevel" -> 20f;
                case "getInt" -> 1;
                case "getBoolean", "isDevMode", "isInGame" -> false;
                case "getColor" -> java.awt.Color.WHITE;
                case "getAngleInDegreesFast" -> {
                    Vector2f from = a.length == 1 ? new Vector2f() : (Vector2f) a[0];
                    Vector2f to = (Vector2f) a[a.length - 1];
                    yield (float) Math.toDegrees(Math.atan2(to.y - from.y, to.x - from.x));
                }
                default -> throw new AssertionError(m);
            }));
            system = api(StarSystemAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getId" -> "escape-system";
                case "getPlanets" -> stars;
                case "getTerrainCopy" -> coronas;
                case "getEntitiesWithTag" -> fish.stream().filter(f -> !f.expired).map(Fish::getMote).toList();
                case "addCustomEntity" -> {
                    FishEntityPlugin.Params params = (FishEntityPlugin.Params) a[4];
                    yield new Fish(this, params.fishId, params.phantom, params.decoyAnchor).getMote();
                }
                case "addTerrain" -> {
                    Terrain stream = new Terrain(this, (SlipstreamTerrainPlugin2.SlipstreamParams2) a[1]);
                    terrain.add(stream);
                    yield stream.token;
                }
                case "removeEntity" -> {
                    for (Terrain t : terrain) if (t.token == a[0]) t.removed = true;
                    yield null;
                }
                default -> throw new AssertionError(m);
            });
            CampaignFleetAPI player = api(CampaignFleetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> new Vector2f();
                case "getContainingLocation" -> system;
                default -> throw new AssertionError(m);
            });
            CampaignClockAPI clock = api(CampaignClockAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getTimestamp" -> 1000L;
                case "getElapsedDaysSince" -> 0f;
                default -> throw new AssertionError(m);
            });
            scripts.add(haunt);
            Global.setSector(api(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getPersistentData" -> persistent;
                case "getTransientScripts" -> scripts;
                case "getCurrentLocation" -> system;
                case "getPlayerFleet" -> player;
                case "getClock" -> clock;
                default -> throw new AssertionError(m);
            }));
        }

        Fish real(String id) { return new Fish(this, id, false, null); }

        PlanetAPI star(float x, float y, float radius, float coronaRadius) {
            Vector2f at = new Vector2f(x, y);
            PlanetSpecAPI spec = api(PlanetSpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "isPulsar" -> false;
                default -> throw new AssertionError(m);
            });
            PlanetAPI star = api(PlanetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> at;
                case "getRadius" -> radius;
                case "isStar" -> true;
                case "isExpired", "isBlackHole" -> false;
                case "getSpec" -> spec;
                default -> throw new AssertionError(m);
            });
            stars.add(star);
            if (coronaRadius > 0f) {
                Corona corona = new Corona(star, coronaRadius);
                coronas.add(api(CampaignTerrainAPI.class, (p, m, a) -> switch (m.getName()) {
                    case "isExpired" -> false;
                    case "getPlugin" -> corona;
                    default -> throw new AssertionError(m);
                }));
            }
            return star;
        }
        @Override public void close() {
            haunt.close();
            Shell.reset();
            Global.setSector(null);
            Global.setSettings(null);
        }
    }

    public static void main(String[] args) throws Exception {
        defaults();
        quorum();
        moray();
        manta();
        mantaShieldPop();
        starGeometry();
        starMovement();
        starFormations();
        System.out.println("Legendary escape checks passed: " + checks);
    }

    private static void starGeometry() {
        try (Environment env = new Environment()) {
            Vector2f from = new Vector2f(-2000f, 0f);
            Vector2f to = new Vector2f(2000f, 0f);
            check(LegendaryStarAvoidance.step(env.system, from, to, 0f).equals(to),
                    "starless systems preserve movement");
            env.star(0f, 0f, 500f, 1500f);
            float radius = 1500f + LegendaryStarAvoidance.CLEARANCE;
            Vector2f next = LegendaryStarAvoidance.step(env.system, from, to, 0f);
            check(OuterReaches.distanceToSegment(new Vector2f(), from, next) >= radius,
                    "dash cannot cross corona with both endpoints outside");
            check(Math.abs(Vector2f.sub(next, from, null).length() - 4000f) < 0.01f,
                    "avoidance steers without stopping the escape dash");
            check(LegendaryStarAvoidance.place(env.system, new Vector2f(), 0f).length() > radius,
                    "spawn at exact star center recovers to a finite safe point");
            check(LegendaryStarAvoidance.place(env.system, new Vector2f(1600f, 0f), 0f).length() > radius,
                    "corona rather than stellar surface sets the boundary");
            env.star(2000f, 0f, 400f, 1200f);
            env.star(-4000f, 900f, 700f, 0f);
            Random random = new Random(9268L);
            for (int i = 0; i < 500; i++) {
                Vector2f start = LegendaryStarAvoidance.place(env.system,
                        new Vector2f(random.nextFloat() * 10000f - 5000f,
                                random.nextFloat() * 10000f - 5000f), 300f);
                Vector2f target = new Vector2f(random.nextFloat() * 20000f - 10000f,
                        random.nextFloat() * 20000f - 10000f);
                Vector2f end = LegendaryStarAvoidance.step(env.system, start, target, 300f);
                float[] boundaries = {1500f, 1200f, 700f};
                for (int j = 0; j < env.stars.size(); j++) {
                    check(OuterReaches.distanceToSegment(env.stars.get(j).getLocation(), start, end)
                                    >= boundaries[j] + LegendaryStarAvoidance.CLEARANCE + 300f - 0.01f,
                            "segment clears every overlapping/companion star, including one without corona");
                }
            }
        }
    }

    private static void starMovement() {
        try (Environment env = new Environment()) {
            env.star(0f, 0f, 500f, 1500f);
            for (String id : List.of("lantern_jack", "quorum", "slipstream_moray", "longliner", "abyssal_ghost_manta")) {
                Fish fish = env.real(id);
                fish.at.set(-2000f, 0f);
                Vector2f from = new Vector2f(fish.at);
                fish.swim(new Vector2f(2000f, 0f));
                check(OuterReaches.distanceToSegment(new Vector2f(), from, fish.at) >= 1650f,
                        id + " swimming steers around the corona");
                fish.at.set(0f, 0f);
                LegendaryStarAvoidance.confine(fish.getMote(), fish.spec);
                check(fish.at.length() > 1650f, id + " spawn/reload correction");
            }
            Fish dash = env.real("slipstream_moray");
            dash.at.set(-2000f, 0f);
            dash.setSwimTarget(new Vector2f(5000f, 0f));
            dash.keepSurfaced(10f);
            dash.startTravelDash(new Vector2f(1000f, 0f), 10f);
            dash.advance(4f);
            check(OuterReaches.distanceToSegment(new Vector2f(), new Vector2f(-2000f, 0f), dash.at) >= 1650f,
                    "actual dash branch cannot tunnel through the corona on a long frame");
            Fish buriedFish = env.real("lantern_jack");
            buriedFish.at.set(-1660f, 0f);
            new Buried(buriedFish).advance(2f);
            check(buriedFish.at.length() >= 1650f, "buried swimming uses star avoidance");
            Fish ordinary = env.real("ordinary");
            ordinary.spec.rarity = FishRarity.COMMON;
            ordinary.at.set(-2000f, 0f);
            ordinary.swim(new Vector2f());
            check(ordinary.at.length() == 0f, "ordinary fish movement unchanged");
            Fish dawn = env.real("false_dawn");
            check(!LegendaryStarAvoidance.applies(dawn.spec), "False Dawn is exempt from corona exclusion");
            dawn.at.set(0f, 0f);
            check(LegendaryStarAvoidance.confine(dawn.getMote(), dawn.spec), "False Dawn retains valid corona");
            check(dawn.at.length() > 500f && dawn.at.length() < 1500f,
                    "False Dawn still confined outside stellar surface inside corona");
        }
    }

    private static void starFormations() {
        try (Environment env = new Environment()) {
            env.star(0f, 0f, 500f, 1500f);
            Fish real = env.real("quorum");
            real.at.set(-1660f, 0f);
            LegendaryChases.getState("quorum").shieldUnits = 0;
            QuorumShellGame.onFailedCatch(real);
            for (int i = 0; i < 180; i++) {
                QuorumShellGame.advance(real, 1f / 60f);
                for (Fish body : env.fish) check(body.at.length() >= 1650f - 0.01f,
                        "Quorum body at " + body.at + " entered corona on frame " + i);
            }
        }
        try (Environment env = new Environment()) {
            env.star(0f, 0f, 500f, 1500f);
            Fish real = env.real("abyssal_ghost_manta");
            real.at.set(-1660f, 0f);
            LegendaryShields.onFailedCatch(real.getMote());
            Manta formation = (Manta) env.haunt.module;
            for (int i = 0; i < 120; i++) {
                formation.move(new Vector2f(real.at.x + 30f, real.at.y));
                formation.sync();
                List<Vector2f> positions = formation.positions();
                for (Vector2f at : positions) check(at.length() >= 1650f - 0.01f,
                        "all manta slots stay clear of the corona");
                for (int j = 1; j < positions.size(); j++) {
                    check(Math.abs(Vector2f.sub(positions.get(j), positions.get(j - 1), null).length()
                                    - MantaFormationModule.SPACING) < 0.01f,
                            "star avoidance preserves manta spacing");
                }
            }
            List<Vector2f> before = formation.positions();
            LegendaryShields.onFailedCatch(real.getMote());
            List<Vector2f> after = formation.positions();
            for (int i = 0; i < before.size(); i++) {
                check(Vector2f.sub(before.get(i), after.get(i), null).length() < 0.01f,
                        "blackout still swaps occupants without moving safe formation slots");
            }
        }
    }

    private static void mantaShieldPop() {
        try (Environment env = new Environment()) {
            Fish fish = env.real(MantaFormationModule.SPECIES);
            check(LegendaryShields.onHarpoonContact(fish.getMote(), false)
                    == LegendaryShields.HitResult.DEFLECTED, "first hit wakes manta");
            check(fish.isBaseShieldUp() && env.haunt.module == null,
                    "wake-up does not pop the shell or switch");
            Vector2f at = new Vector2f(fish.at);
            check(LegendaryShields.onHarpoonContact(fish.getMote(), false)
                    == LegendaryShields.HitResult.DEFLECTED, "shield pop deflects the shot");
            Manta formation = (Manta) env.haunt.module;
            check(!fish.isBaseShieldUp() && formation.black, "shield pop starts blackout immediately");
            check(!fish.at.equals(at) && env.fish.size() == 3,
                    "first shield pop creates formation and moves real manta");
            List<Vector2f> before = formation.positions();
            int previous = formation.index();
            check(fish.isMantaSwitching() && !FishEntityPlugin.isAvailable(fish.getMote(), true),
                    "switch blocks even deep-strike targeting");
            check(LegendaryShields.onHarpoonContact(fish.getMote(), true)
                    == LegendaryShields.HitResult.DEFLECTED, "switch rejects immediate explosive hit");
            check(LegendaryShields.onExplosiveStrike(fish.getMote()),
                    "direct explosive callback cannot destroy or relocate a switching manta");
            check(previous == formation.index() && env.fish.size() == 3 && !fish.expired,
                    "extra hits neither switch again nor spawn another manta");
            formation.tick(0.29f);
            check(fish.isMantaSwitching(), "switch protection lasts 0.3 seconds");
            formation.tick(0.02f);
            check(!fish.isMantaSwitching() && FishEntityPlugin.isAvailable(fish.getMote()),
                    "manta becomes available after switch");
            check(LegendaryShields.onHarpoonContact(fish.getMote(), false)
                    == LegendaryShields.HitResult.NONE, "unshielded follow-up can catch after switch");
            fish.restoreBaseShield();
            LegendaryShields.onHarpoonContact(fish.getMote(), true);
            check(previous != formation.index() && !fish.isBaseShieldUp(),
                    "regrown shield switches again with an explosive head");
            List<Vector2f> after = formation.positions();
            for (int i = 0; i < before.size(); i++) {
                check(Vector2f.sub(before.get(i), after.get(i), null).length() < 0.001f,
                        "shield switch preserves formation slots");
            }
            check(env.fish.size() == 3, "shield switch reuses copies");
        }
    }

    private static void defaults() throws Exception {
        try (Environment env = new Environment()) {
            for (String id : List.of("lantern_jack", "false_dawn", "longliner")) {
                Fish fish = env.real(id);
                fish.tryBaseShieldDeflect();
                fish.setHeld(true);
                LegendaryChases.Chase state = LegendaryChases.getState(id);
                state.shieldPopped = true;
                state.shieldUnits = 0;
                check(LegendaryShields.onFailedCatch(fish.getMote()), "preserve real legendary");
                check(!fish.isHeld() && !fish.expired && LegendaryShields.isShielded(fish), "released with shield");
                check(state.shieldUnits == 0, "no free escort or stored shells");
                if (id.equals("longliner")) {
                    check(state.shieldPopped && state.recoveryShield, "hull remains broken");
                    ByteArrayOutputStream saved = new ByteArrayOutputStream();
                    try (ObjectOutputStream out = new ObjectOutputStream(saved)) {
                        out.writeObject(state);
                    }
                    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(saved.toByteArray()))) {
                        LegendaryChases.Chase loaded = (LegendaryChases.Chase) in.readObject();
                        check(loaded.shieldPopped && loaded.recoveryShield, "hull and recovery shield persist together");
                    }
                    check(LegendaryShields.onHarpoonContact(fish.getMote(), false)
                            == LegendaryShields.HitResult.DEFLECTED, "ordinary hit breaks recovery shield");
                    check(!LegendaryShields.isShielded(fish), "recovery shield spent");
                    check(LegendaryShields.onHarpoonContact(fish.getMote(), false)
                            == LegendaryShields.HitResult.NONE, "next ordinary hit can catch");
                    check(state.shieldPopped, "no explosive head required again");
                    LegendaryShields.onFailedCatch(fish.getMote());
                    check(state.shieldPopped && state.recoveryShield, "next failure restores only recovery shield");
                }
            }
            Fish ordinary = env.real("ordinary");
            ordinary.spec.rarity = FishRarity.COMMON;
            check(!LegendaryShields.onFailedCatch(ordinary.getMote()), "ordinary failure stays ordinary");
            Fish phantom = new Fish(env, "quorum", true, null);
            check(!LegendaryShields.onFailedCatch(phantom.getMote()), "phantom cannot trigger response");
        }
    }

    private static void quorum() {
        for (int fps : new int[]{30, 60, 144}) try (Environment env = new Environment()) {
            Fish fish = env.real("quorum");
            LegendaryChases.Chase state = LegendaryChases.getState("quorum");
            state.shieldUnits = 0;
            QuorumShellGame.advance(fish, 0.8f);
            Vector2f center = new Vector2f();
            for (Fish body : env.fish) Vector2f.add(center, body.at, center);
            center.scale(1f / env.fish.size());
            LegendaryShields.onFailedCatch(fish.getMote());
            check(env.fish.size() == 15, "one real, two shell bodies, twelve illusions");
            check(env.fish.stream().filter(f -> f.phantom).count() == 12, "illusions cannot become loot");
            double previous = angle(center, fish.at), total = 0;
            float elapsed = 0;
            while (elapsed < QuorumShellGame.ESCAPE_SPIN_SECONDS) {
                float dt = Math.min(1f / fps, QuorumShellGame.ESCAPE_SPIN_SECONDS - elapsed);
                QuorumShellGame.advance(fish, dt);
                double next = angle(center, fish.at);
                double step = Math.atan2(Math.sin(next - previous), Math.cos(next - previous));
                total += step;
                previous = next;
                elapsed += dt;
            }
            check(Math.abs(total / (Math.PI * 2) - 4) < 0.001, "exactly four rotations at " + fps + " Hz");
            LegendaryShields.onFailedCatch(fish.getMote());
            check(env.fish.size() == 15, "repeated failures cap illusions");
            check(!LegendaryShields.onFailedCatch(env.fish.get(1).getMote()), "ordinary shell body loss is not legendary loss");
            Shell.reset();
            QuorumShellGame.advance(fish, 0f);
            check(env.fish.size() == 15, "controller reconstruction reuses persisted bodies");
        }
    }

    private static void moray() {
        try (Environment env = new Environment()) {
            Fish fish = env.real("slipstream_moray");
            fish.tryBaseShieldDeflect();
            fish.setHeld(true);
            LegendaryShields.onFailedCatch(fish.getMote());
            Slip slip = (Slip) env.haunt.module;
            check(slip.remaining() > SlipDashModule.DASH_MAX_SECONDS && fish.isDashing(), "immediate longer dash");
            check(env.terrain.size() == 1 && !fish.isBaseShieldUp(), "slipstream response does not refill shield");
            for (int i = 0; i < 90; i++) {
                fish.diveTime(0.1f);
                fish.at.translate(70f, 30f);
                slip.step(fish, 0.1f);
                check(fish.getVisibility() == 1f && fish.isDashing(), "dive cannot interrupt emergency dash");
            }
            check(env.terrain.get(0).stream.getSegments().size() > 2, "dash lays a trail");
            LegendaryShields.onFailedCatch(fish.getMote());
            check(env.terrain.size() == 2 && slip.remaining() == SlipDashModule.ESCAPE_DASH_SECONDS,
                    "another loss restarts emergency dash");
            slip.step(fish, SlipDashModule.ESCAPE_DASH_SECONDS);
            check(!fish.isDashing(), "dash ends");
            env.haunt.close();
            check(env.terrain.stream().allMatch(t -> t.expired && t.removed), "cleanup removes both trails");
        }
    }

    private static void manta() {
        try (Environment env = new Environment()) {
            Fish fish = env.real("abyssal_ghost_manta");
            fish.tryBaseShieldDeflect();
            fish.setHeld(true);
            LegendaryShields.onFailedCatch(fish.getMote());
            Manta manta = (Manta) env.haunt.module;
            check(manta.black && fish.isBaseShieldUp() && env.fish.size() == 3,
                    "first loss restores shield and builds blackout formation");
            int before = manta.index();
            List<Vector2f> positions = manta.positions();
            LegendaryShields.onFailedCatch(fish.getMote());
            check(manta.index() != before, "real manta changes slot immediately");
            List<Vector2f> after = manta.positions();
            for (int i = 0; i < positions.size(); i++) {
                check(Vector2f.sub(positions.get(i), after.get(i), null).length() < 0.001f,
                        "swap preserves each world-space slot within float precision");
            }
            check(manta.remaining() == 0.3f && env.fish.size() == 3, "repeat loss restarts blackout without more copies");
            manta.advance(0.31f);
            check(!manta.black, "blackout ends");
            env.haunt.close();
            check(env.fish.get(1).expired && env.fish.get(2).expired && !fish.expired,
                    "cleanup preserves only the real manta");
        }
    }

    private static double angle(Vector2f center, Vector2f point) {
        return Math.atan2(point.y - center.y, point.x - center.x);
    }

    private static <T> T api(Class<T> type, InvocationHandler handler) {
        return FishingParityChecks.proxy(type, (p, m, a) -> switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            case "toString" -> type.getSimpleName();
            default -> handler.invoke(p, m, a);
        });
    }

    private static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
}
