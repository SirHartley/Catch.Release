package catchrelease.tools;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.*;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2;
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
                case "addTag" -> null;
                default -> throw new AssertionError(m);
            });
            env.fish.add(this);
        }

        @Override public FishSpec getFishSpec() { return spec; }
        @Override public boolean isPhantom() { return phantom; }
        @Override public boolean isDecoy() { return anchor != null; }
        @Override public SectorEntityToken getDecoyAnchor() { return anchor; }
        @Override protected void advanceLampFade(float amount) { }
        @Override protected void advanceShieldLens() { }
        void diveTime(float amount) { advanceDive(amount); }
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
        System.out.println("Legendary escape checks passed: " + checks);
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
