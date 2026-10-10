package catchrelease.tools;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.legendary.*;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.memory.TransientMemory;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
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

    public interface TextMote extends CustomCampaignEntityAPI {

        List<Notice> getFloatingText();
    }

    static class Notice {

        private SectorEntityToken entity;
        final Vector2f offset = new Vector2f(0f, 25f);
        final TextSize label = new TextSize();
        final String text;

        Notice(SectorEntityToken entity, String text) {
            this.entity = entity;
            this.text = text;
        }
    }

    public static class TextSize {

        public float getHeight() { return 16f; }
    }

    static class Fish extends FishEntityPlugin {

        final FishSpec spec = new FishSpec();
        final Vector2f at = new Vector2f(500f, 500f);
        final boolean phantom;
        final SectorEntityToken anchor;
        SectorEntityToken orbit;
        Object plugin = this;
        final Set<String> tags = new HashSet<>(Set.of(MOTE_TAG));
        final List<EveryFrameScript> entityScripts = new ArrayList<>();
        final List<Notice> notices = new ArrayList<>();
        boolean visible = true;
        boolean expired;
        boolean removed;
        boolean quest;
        boolean pond;

        Fish(Environment env, String id, boolean phantom, SectorEntityToken anchor) {
            spec.id = id;
            spec.rarity = env.specs.containsKey(id) ? env.specs.get(id).rarity
                    : "quorum_shard".equals(id) ? FishRarity.RARE : FishRarity.LEGENDARY;
            this.phantom = phantom;
            this.anchor = anchor;
            MemoryAPI moteMemory = api(MemoryAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getBoolean" -> quest && catchrelease.campaign.fish.jobs.QuestPond.QUEST_MOTE_FLAG.equals(a[0]);
                default -> throw new AssertionError(m);
            });
            entity = api(TextMote.class, (p, m, a) -> switch (m.getName()) {
                case "getMemoryWithoutUpdate" -> moteMemory;
                case "getLocation" -> at;
                case "setLocation" -> { at.set((float) a[0], (float) a[1]); yield null; }
                case "getCustomPlugin" -> plugin;
                case "getContainingLocation" -> env.system;
                case "isExpired" -> expired;
                case "setExpired" -> { expired = (boolean) a[0]; yield null; }
                case "addTag" -> { tags.add((String) a[0]); yield null; }
                case "hasTag" -> tags.contains(a[0]);
                case "addScript" -> { entityScripts.add((EveryFrameScript) a[0]); yield null; }
                case "isVisibleToPlayerFleet" -> visible;
                case "getFloatingText" -> notices.isEmpty() ? null : notices;
                case "addFloatingText" -> {
                    if (visible) notices.add(new Notice((SectorEntityToken) p, (String) a[0]));
                    yield null;
                }
                default -> throw new AssertionError(m);
            });
            env.fish.add(this);
        }

        @Override public FishSpec getFishSpec() { return spec; }
        @Override public String getFishId() { return spec.id; }
        @Override public boolean isPhantom() { return phantom; }
        @Override public boolean isDecoy() { return anchor != null; }
        @Override public SectorEntityToken getDecoyAnchor() { return anchor; }
        @Override public SectorEntityToken getOrbitAnchor() { return orbit; }
        @Override public boolean holdsStation() { return false; }
        @Override public boolean isFromPond() { return pond; }
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
        @Override protected void advanceTrail() { }
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
        void stepTrails(float amount) { advanceTrails(amount); }
        void straight(float direction) { bearing = dashStartBearing = direction; curveRate = 0f; }
        void moveForward(Fish fish, float distance) {
            fish.at.translate((float) Math.cos(Math.toRadians(bearing)) * distance,
                    (float) Math.sin(Math.toRadians(bearing)) * distance);
        }
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

        boolean productionModules;
        HauntModule module;
        @Override protected List<HauntModule> buildModules(FishSpec spec, StarSystemAPI system) {
            if (productionModules) return super.buildModules(spec, system);
            module = switch (spec.id) {
                case "slipstream_moray" -> new Slip(system, spec);
                case "quorum" -> new DistractionMotesModule(system, spec);
                default -> new Manta(system, spec);
            };
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
        final Map<String, FishSpec> specs = new LinkedHashMap<>();
        final TransientMemory memory = new TransientMemory();
        int tutorialStage;
        float zoom = 1f;
        final List<EveryFrameScript> scripts = new ArrayList<>();
        final StarSystemAPI system;
        final Haunt haunt = new Haunt();
        boolean lampsOn = true;
        final SearchlightAbilityPlugin lamps = new SearchlightAbilityPlugin() {

            @Override public boolean isActive() { return lampsOn; }
        };

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
                case "isHyperspace" -> false;
                case "getPlanets" -> stars;
                case "getStar" -> stars.stream().filter(PlanetAPI::isStar).findFirst().orElse(null);
                case "getTerrainCopy" -> coronas;
                case "createToken" -> {
                    Vector2f at = new Vector2f((float) a[0], (float) a[1]);
                    yield api(SectorEntityToken.class, (token, method, args) -> switch (method.getName()) {
                        case "getLocation" -> at;
                        default -> throw new AssertionError(method);
                    });
                }
                case "getEntitiesWithTag" -> fish.stream()
                        .filter(f -> !f.expired && f.tags.contains(a[0])).map(Fish::getMote).toList();
                case "addCustomEntity" -> {
                    if (a[4] instanceof BuriedMoteEntityPlugin.Params params) {
                        Fish created = new Fish(this, params.fishId, false, null);
                        created.plugin = new Buried(created);
                        created.tags.clear();
                        created.tags.add(BuriedMoteEntityPlugin.BURIED_TAG);
                        yield created.getMote();
                    }
                    FishEntityPlugin.Params params = (FishEntityPlugin.Params) a[4];
                    Fish created = new Fish(this, params.fishId, params.phantom, params.decoyAnchor);
                    created.setSwimTarget(params.target);
                    created.orbit = params.orbitAnchor;
                    yield created.getMote();
                }
                case "addTerrain" -> {
                    Terrain stream = new Terrain(this, (SlipstreamTerrainPlugin2.SlipstreamParams2) a[1]);
                    terrain.add(stream);
                    yield stream.token;
                }
                case "removeEntity" -> {
                    for (Fish f : fish) if (f.getMote() == a[0]) f.removed = true;
                    for (Terrain t : terrain) if (t.token == a[0]) t.removed = true;
                    yield null;
                }
                default -> throw new AssertionError(m);
            });
            CampaignFleetAPI player = api(CampaignFleetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> new Vector2f();
                case "getContainingLocation" -> system;
                case "getAbility" -> SearchlightAbilityPlugin.ABILITY_ID.equals(a[0]) ? lamps : null;
                default -> throw new AssertionError(m);
            });
            CampaignClockAPI clock = api(CampaignClockAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getTimestamp" -> 1000L;
                case "getElapsedDaysSince" -> 0f;
                default -> throw new AssertionError(m);
            });
            scripts.add(haunt);
            memory.set("$catchrelease_data/campaign/fish.csv", specs);
            MemoryAPI sectorMemory = api(MemoryAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getInt" -> TutorialConstants.STAGE_KEY.equals(a[0]) ? tutorialStage : 0;
                default -> throw new AssertionError(m);
            });
            GenericPluginManagerAPI plugins = api(GenericPluginManagerAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasPlugin" -> true;
                case "getPluginsOfClass" -> List.of(memory);
                default -> throw new AssertionError(m);
            });
            Global.setSector(api(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getViewport" -> api(ViewportAPI.class, (vp, vm, va) -> switch (vm.getName()) {
                    case "convertScreenHeightToWorldHeight" -> (float) va[0] * zoom;
                    default -> throw new AssertionError(vm);
                });
                case "getMemoryWithoutUpdate" -> sectorMemory;
                case "getGenericPlugins" -> plugins;
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
        morayHauntStart();
        morayDiveLock();
        moraySwimSpeed();
        morayChaseOnly();
        morayShield();
        moray();
        morayTrailLifetime();
        manta();
        mantaShieldPop();
        deflectionLabels();
        labelSpacing();
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
            LegendaryChases.getState("quorum").provoked = true;
            LegendaryChases.getState("quorum").roaming = true;
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
                if (i % 10 == 0) {
                    LegendaryShields.onFailedCatch(real.getMote());
                    formation.tick(0.31f);
                }
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
            Notice wake = fish.notices.get(0);
            check(wake.text.equals("Deflected - Now awake") && wake.entity != fish.getMote()
                    && wake.entity.getLocation().equals(at), "wake-up label anchors at the hit");
            check(LegendaryShields.onHarpoonContact(fish.getMote(), false)
                    == LegendaryShields.HitResult.DEFLECTED, "shield pop deflects the shot");
            Manta formation = (Manta) env.haunt.module;
            check(!fish.isBaseShieldUp() && formation.black, "shield pop starts blackout immediately");
            check(!fish.at.equals(at) && env.fish.size() == 3,
                    "first shield pop creates formation and moves real manta");
            Notice pop = fish.notices.get(1);
            check(pop.text.equals("Deflected") && pop.entity != fish.getMote()
                    && pop.entity.getLocation().equals(at), "shield label stays at the pre-swap hit");
            check(wake.entity.getLocation().equals(at), "existing wake-up label does not follow the swap");
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
            check(fish.notices.size() == 2, "switch protection does not add another label");
            formation.tick(0.29f);
            check(fish.isMantaSwitching(), "switch protection lasts 0.3 seconds");
            formation.tick(0.02f);
            check(!fish.isMantaSwitching() && FishEntityPlugin.isAvailable(fish.getMote()),
                    "manta becomes available after switch");
            check(Vector2f.sub(before.get(0), formation.positions().get(0), null).length() > 1f,
                    "shield-break jitter reorients the line when it ends");
            check(pop.entity.getLocation().equals(at) && wake.entity.getLocation().equals(at),
                    "labels stay at the hit through jitter-end reorientation");
            before = formation.positions();
            check(LegendaryShields.onHarpoonContact(fish.getMote(), false)
                    == LegendaryShields.HitResult.NONE, "unshielded follow-up can catch after switch");
            fish.restoreBaseShield();
            Vector2f nextHit = new Vector2f(fish.at);
            LegendaryShields.onHarpoonContact(fish.getMote(), true);
            check(fish.notices.get(2).entity.getLocation().equals(nextHit)
                    && pop.entity.getLocation().equals(at), "repeated hits keep separate fixed anchors");
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

    private static void deflectionLabels() {
        try (Environment env = new Environment()) {
            Fish manta = env.real(MantaFormationModule.SPECIES);
            manta.visible = false;
            LegendaryShields.onHarpoonContact(manta.getMote(), false);
            LegendaryShields.onHarpoonContact(manta.getMote(), false);
            check(manta.notices.isEmpty() && manta.isMantaSwitching(),
                    "hidden contacts still switch without creating labels");
        }
        try (Environment env = new Environment()) {
            Fish fish = env.real(LegendaryShields.MORAY_SPECIES);
            LegendaryShields.onHarpoonContact(fish.getMote(), false);
            LegendaryShields.onHarpoonContact(fish.getMote(), false);
            fish.at.set(700f, 900f);
            check(fish.notices.size() == 2 && fish.notices.stream().allMatch(n -> n.entity == fish.getMote()),
                    "other legendary labels retain vanilla entity following");
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
                check(!fish.isHeld() && !fish.expired, "released alive");
                check(LegendaryShields.isShielded(fish) == id.equals("longliner"),
                        "failure restores the Imposter's recovery shield, not stored charges");
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

    private static void labelSpacing() {
        for (float zoom : new float[]{0.5f, 1f, 3f}) try (Environment env = new Environment()) {
            env.zoom = zoom;
            Fish jack = env.real("lantern_jack");
            for (String message : List.of("Deflected", "The lantern flares. Nearby motes turn toward it.",
                    "Mote consumed. Another shell layers on.", "Shell burned")) {
                LegendaryShields.say(jack.getMote(), message);
            }
            for (int i = 1; i < jack.notices.size(); i++) {
                check((jack.notices.get(i - 1).offset.y - jack.notices.get(i).offset.y) / zoom >= 20f,
                        "same-frame call, shield and feeding labels have a screen-space gap");
            }
            jack.notices.get(3).offset.y += 15f;
            LegendaryShields.say(jack.getMote(), "Deflected");
            for (int i = 1; i < jack.notices.size(); i++) {
                check((jack.notices.get(i - 1).offset.y - jack.notices.get(i).offset.y) / zoom >= 20f,
                        "older labels drift away from newer labels");
            }
            jack.visible = false;
            LegendaryShields.say(jack.getMote(), "Deflected");
            check(jack.notices.size() == 5, "hidden entities do not gain a label");
        }
    }

    private static void quorum() {
        for (int fps : new int[]{30, 60, 144}) try (Environment env = new Environment()) {
            Fish fish = env.real("quorum");
            LegendaryChases.Chase state = LegendaryChases.getState("quorum");
            state.shieldUnits = 0;
            state.provoked = true;
            state.roaming = true;
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

    private static void morayHauntStart() {
        for (int fps : new int[]{30, 60, 144}) for (float range : new float[]{700f, 2100f}) {
            try (Environment env = new Environment()) {
                env.tutorialStage = FishingIntro.DONE;
                Fish fish = env.real("slipstream_moray");
                fish.at.set(range, 0f);
                env.specs.put(fish.spec.id, fish.spec);
                LegendaryChases.Chase chase = LegendaryChases.getState(fish.spec.id);
                chase.systemId = env.system.getId();
                float dt = 1f / fps;

                env.haunt.advance(dt);
                check(env.terrain.isEmpty(), "unprovoked Moray does not start a stream");
                chase.provoked = true;
                env.lampsOn = false;
                env.haunt.advance(dt);
                check(env.terrain.isEmpty(), "lamps must be on to start the haunt");
                env.lampsOn = true;
                env.haunt.advance(0f);
                check(env.terrain.isEmpty(), "zero time does not start the haunt");

                env.haunt.advance(dt);
                Slip slip = (Slip) env.haunt.module;
                check(env.haunt.getIntensity() < 1f && env.terrain.size() == 1,
                        "first haunt update creates the stream before full intensity");
                check(fish.isDashing() && slip.remaining() >= SlipDashModule.DASH_MIN_SECONDS
                                && slip.remaining() <= SlipDashModule.DASH_MAX_SECONDS,
                        "opening escape uses the normal dash duration");
                Vector2f before = new Vector2f(fish.at);
                fish.advance(dt);
                check(fish.at.length() > before.length(), "first movement frame flees from the player");
                env.haunt.advance(dt);
                for (int i = 0; i < fps / 2; i++) {
                    fish.advance(dt);
                    env.haunt.advance(dt);
                    check(!fish.isDiving(), "opening dash stays surfaced");
                }
                check(env.terrain.size() == 1 && env.terrain.get(0).stream.getSegments().size() > 1,
                        "opening escape grows one stream at " + fps + " Hz");

                slip.step(fish, SlipDashModule.DASH_MAX_SECONDS);
                fish.at.set(700f, 0f);
                slip.setIntensity(1f);
                slip.advance(SlipDashModule.COOLDOWN_MIN_SECONDS - dt);
                check(env.terrain.size() == 1, "repeat dash keeps its cooldown");
                slip.setIntensity(0.5f);
                slip.advance(SlipDashModule.COOLDOWN_MAX_SECONDS);
                check(env.terrain.size() == 1, "repeat dash still requires full intensity");
                slip.setIntensity(1f);
                fish.at.set(2100f, 0f);
                slip.advance(dt);
                check(env.terrain.size() == 1, "repeat dash still requires normal trigger range");
                fish.at.set(700f, 0f);
                slip.advance(dt);
                check(slip.remaining() > 0f, "repeat dash starts once all normal gates pass");
                env.haunt.close();
                check(env.terrain.stream().allMatch(t -> t.expired && t.removed),
                        "haunt cleanup removes opening and repeat streams");
            }
        }
        try (Environment env = new Environment()) {
            Fish fish = env.real("slipstream_moray");
            Slip slip = new Slip(env.system, fish.spec);
            fish.setHeld(true);
            slip.advance(0.1f);
            check(env.terrain.isEmpty() && !fish.isDashing(), "opening dash cannot pull a held fish away");
            fish.setHeld(false);
            env.lampsOn = false;
            slip.advance(0.1f);
            check(env.terrain.isEmpty(), "delayed opening cannot start with lamps off");
            env.lampsOn = true;
            slip.advance(0.1f);
            check(env.terrain.size() == 1 && fish.isDashing(), "released fish can start its opening escape");
            slip.cleanup();
        }
    }

    private static void morayDiveLock() {
        for (int fps : new int[]{30, 60, 144}) for (boolean moduleFirst : new boolean[]{false, true}) {
            try (Environment env = new Environment()) {
                Fish fish = env.real("slipstream_moray");
                fish.setSwimTarget(new Vector2f(20000f, 0f));
                Slip slip = new Slip(env.system, fish.spec);
                float dt = 1f / fps;
                for (int dash = 0; dash < 3; dash++) {
                    fish.at.set(700f, 0f);
                    if (dash == 2) {
                        fish.keepSurfaced(0f);
                        for (int frame = 0; frame < 20 * fps && !fish.isDiving(); frame++) {
                            fish.diveTime(dt);
                        }
                        check(fish.isDiving(), "emergency dash begins with a submerged fish");
                        slip.onFailedCatch(fish);
                    } else {
                        // Start during a dive fade, before the fish becomes untargetable.
                        fish.keepSurfaced(0f);
                        for (int frame = 0; frame < 20 * fps && fish.getVisibility() == 1f; frame++) {
                            fish.diveTime(dt);
                        }
                        check(fish.getVisibility() < 1f && !fish.isDiving(), "dive fade is in progress");
                        slip.setIntensity(1f);
                        slip.advance(SlipDashModule.COOLDOWN_MAX_SECONDS + 1f);
                    }
                    check(fish.isDashing() && fish.getVisibility() == 1f,
                            "opening, repeat and emergency dashes surface immediately");
                    float duration = slip.remaining();
                    int frames = 0;
                    while (slip.remaining() > dt) {
                        if (moduleFirst) slip.step(fish, dt);
                        fish.advance(dt);
                        if (!moduleFirst) slip.step(fish, dt);
                        check(fish.getVisibility() == 1f, "dash cannot fade or dive in either callback order");
                        frames++;
                    }
                    check(frames * dt >= duration - 2f * dt, "diving cannot shorten a dash");
                    slip.step(fish, dt);
                    check(!fish.isDashing(), "dash expires normally");
                    boolean dived = false;
                    for (int frame = 0; frame < 20 * fps; frame++) {
                        fish.diveTime(dt);
                        dived |= fish.isDiving();
                    }
                    check(dived, "diving resumes after the dash");
                }
                slip.onFailedCatch(fish);
                fish.setHeld(true);
                slip.step(fish, dt);
                check(!fish.isDashing(), "retrieval cancels the dash");
                fish.setHeld(false);
                slip.onFailedCatch(fish);
                slip.cleanup();
                check(!fish.isDashing(), "cleanup releases the dive lock");
                boolean dived = false;
                for (int frame = 0; frame < 20 * fps; frame++) {
                    fish.diveTime(dt);
                    dived |= fish.isDiving();
                }
                check(dived, "cleanup does not leave the fish surfaced permanently");
            }
        }
    }

    private static void moraySwimSpeed() {
        for (int fps : new int[]{30, 60, 144}) for (FishMotion motion : FishMotion.values()) {
            try (Environment env = new Environment()) {
                Fish fish = env.real(LegendaryShields.MORAY_SPECIES);
                fish.spec.motion = motion;
                fish.keepSurfaced(30f);
                LegendaryChases.getState(fish.spec.id).provoked = true;
                float dt = 1f / fps;
                float min = Float.MAX_VALUE;
                float max = 0f;
                for (int frame = 0; frame < 24 * fps; frame++) {
                    fish.at.set(700f, 0f);
                    Vector2f before = new Vector2f(fish.at);
                    fish.advance(dt);
                    float speed = Vector2f.sub(fish.at, before, null).length() / dt;
                    check(speed < 400f, "Moray's mixed bursts stay below its slipstream speed");
                    min = Math.min(min, speed);
                    max = Math.max(max, speed);
                }
                check(min < 110f, "Moray retains its breathing room");
                if (motion == FishMotion.DARTER || motion == FishMotion.LUNGER) {
                    check(max > 350f, "Moray retains a smaller mixed-movement burst");
                }
                fish.at.set(700f, 0f);
                fish.startTravelDash(new Vector2f(SlipDashModule.DASH_SPEED, 0f), 1f);
                fish.advance(dt);
                check(Math.abs((fish.at.x - 700f) / dt - 900f) < 0.1f,
                        "swim speed cap does not affect travel dashes");
            }
        }
        try (Environment env = new Environment()) {
            Fish fish = env.real("ordinary");
            fish.spec.rarity = FishRarity.RARE;
            fish.spec.motion = FishMotion.LUNGER;
            fish.setSwimTarget(new Vector2f(20000f, 0f));
            fish.keepSurfaced(30f);
            float max = 0f;
            for (int frame = 0; frame < 600; frame++) {
                Vector2f before = new Vector2f(fish.at);
                fish.advance(1f / 60f);
                max = Math.max(max, Vector2f.sub(fish.at, before, null).length() * 60f);
            }
            check(max > 440f, "ordinary fish keep their existing lunge speed");
        }
    }

    private static void morayChaseOnly() {
        try (Environment env = new Environment()) {
            env.tutorialStage = FishingIntro.DONE;
            env.haunt.productionModules = true;
            Fish fish = env.real("slipstream_moray");
            fish.setSwimTarget(new Vector2f(20000f, 0f));
            Fish ordinary = env.real("ordinary");
            ordinary.spec.rarity = FishRarity.COMMON;
            ordinary.at.set(50f, 0f);
            env.specs.put(fish.spec.id, fish.spec);
            env.specs.put(ordinary.spec.id, ordinary.spec);
            LegendaryChases.Chase chase = LegendaryChases.getState(fish.spec.id);
            chase.systemId = env.system.getId();
            chase.provoked = true;

            env.haunt.advance(1f / 60f);
            check(fish.isDashing() && env.terrain.size() == 1, "production haunt retains its opening escape");
            // The fleet proxy rejects ability/cooldown access and movement writes.
            for (int frame = 0; frame < 40 * 60; frame++) {
                fish.advance(1f / 60f);
                env.haunt.advance(1f / 60f);
                check(!ordinary.isDashing() && ordinary.entityScripts.isEmpty(),
                        "Moray leaves nearby ordinary fish alone");
            }
            check(env.fish.size() == 2, "Moray does not conjure projectile fish");
            env.haunt.close();
            check(env.terrain.stream().allMatch(t -> t.expired && t.removed),
                    "production haunt cleans up streams without changing player abilities");
        }
    }

    private static void morayShield() throws Exception {
        for (int fps : new int[]{30, 60, 144}) for (boolean explosive : new boolean[]{false, true}) {
            try (Environment env = new Environment()) {
                Fish fish = env.real(LegendaryShields.MORAY_SPECIES);
                fish.setSwimTarget(new Vector2f(10000f, 10000f));
                java.awt.Color color = LegendaryShields.getShieldColor(fish);
                check(color.getGreen() > color.getRed() && color.getGreen() > color.getBlue(),
                        "Moray shield and deflection flash are green");
                check(LegendaryShields.isShielded(fish), "Moray starts shielded");
                check(LegendaryShields.onHarpoonContact(fish.getMote(), explosive)
                        == LegendaryShields.HitResult.DEFLECTED && LegendaryShields.isShielded(fish),
                        "wake-up contact retains the existing shield");
                check(LegendaryShields.onHarpoonContact(fish.getMote(), explosive)
                        == LegendaryShields.HitResult.DEFLECTED && !LegendaryShields.isShielded(fish),
                        "next hit breaks Moray shield");
                for (int loss = 0; loss < 3; loss++) {
                    fish.setHeld(true);
                    check(LegendaryShields.onFailedCatch(fish.getMote()) && !fish.isHeld()
                            && fish.isDashing(), "loss releases Moray into its escape dash");
                    for (int frame = 0; frame < fps * 12; frame++) {
                        fish.advance(1f / fps);
                        check(!LegendaryShields.isShielded(fish), "Moray shield cannot regrow over time");
                    }
                    check(LegendaryShields.onHarpoonContact(fish.getMote(), false)
                            == LegendaryShields.HitResult.NONE, "next catch attempt is not deflected");
                }
                ByteArrayOutputStream saved = new ByteArrayOutputStream();
                try (ObjectOutputStream out = new ObjectOutputStream(saved)) {
                    out.writeObject(LegendaryChases.getState(fish.spec.id));
                }
                try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(saved.toByteArray()))) {
                    LegendaryChases.Chase loaded = (LegendaryChases.Chase) in.readObject();
                    env.persistent.put(LegendaryChases.KEY, new LinkedHashMap<>(Map.of(fish.spec.id, loaded)));
                }
                fish.expired = true;
                Fish recreated = env.real(LegendaryShields.MORAY_SPECIES);
                check(!LegendaryShields.isShielded(recreated)
                        && LegendaryShields.onHarpoonContact(recreated.getMote(), false)
                        == LegendaryShields.HitResult.NONE, "saved shield break survives mote recreation");
            }
        }
    }

    private static void moray() {
        try (Environment env = new Environment()) {
            Fish fish = env.real("slipstream_moray");
            LegendaryShields.onHarpoonContact(fish.getMote(), false);
            LegendaryShields.onHarpoonContact(fish.getMote(), false);
            fish.setHeld(true);
            LegendaryShields.onFailedCatch(fish.getMote());
            Slip slip = (Slip) env.haunt.module;
            slip.straight((float) Math.toDegrees(Math.atan2(30, 70)));
            check(slip.remaining() > SlipDashModule.DASH_MAX_SECONDS && fish.isDashing(), "immediate longer dash");
            check(env.terrain.size() == 1 && !LegendaryShields.isShielded(fish),
                    "slipstream response does not refill shield");
            for (int i = 0; i < 90; i++) {
                fish.diveTime(0.1f);
                fish.at.translate(70f, 30f);
                slip.step(fish, 0.1f);
                check(fish.getVisibility() == 1f && fish.isDashing(), "dive cannot interrupt emergency dash");
            }
            check(env.terrain.get(0).stream.getSegments().size() > 2, "dash lays a trail");
            LegendaryShields.onFailedCatch(fish.getMote());
            check(env.terrain.size() == 1 && slip.remaining() == SlipDashModule.ESCAPE_DASH_SECONDS,
                    "another loss restarts escape without overlapping the previous trail");
            slip.moveForward(fish, 2000f);
            slip.step(fish, 0.01f);
            check(env.terrain.size() == 2, "emergency escape resumes its trail in clear space");
            slip.step(fish, SlipDashModule.ESCAPE_DASH_SECONDS);
            check(!fish.isDashing(), "dash ends");
            slip.setIntensity(0.5f);
            slip.advance(SlipDashModule.COOLDOWN_MAX_SECONDS + 1f);
            check(env.terrain.size() == 2, "failed catch consumes the opening dash without a second stream");
            env.haunt.close();
            check(env.terrain.stream().allMatch(t -> t.expired && t.removed), "cleanup removes both trails");
        }
    }

    private static void morayTrailLifetime() {
        for (int fps : new int[]{30, 60, 144}) for (int count : new int[]{3, 14, 28}) {
            try (Environment env = new Environment()) {
                Fish fish = env.real("slipstream_moray");
                Slip slip = new Slip(env.system, fish.spec);
                slip.onFailedCatch(fish);
                slip.straight(0f);
                for (int i = 1; i < count; i++) {
                    fish.at.translate(SlipDashModule.SEGMENT_SPACING + 1f, 0f);
                    slip.step(fish, (SlipDashModule.SEGMENT_SPACING + 1f) / SlipDashModule.DASH_SPEED);
                }
                slip.step(fish, SlipDashModule.ESCAPE_DASH_SECONDS);
                check(!fish.isDashing(), "longer stream lifetime does not extend the dash");
                Terrain trail = env.terrain.get(0);
                var segments = trail.stream.getSegments();
                check(segments.size() == count, "trail retains its segment count");
                for (var segment : segments) segment.fader.forceIn();

                float previousLifetime = 14f / 2.5f + 3f;
                int frames = 0;
                float expectedLifetime = previousLifetime * SlipDashModule.STREAM_LIFETIME_MULT;
                while (!trail.expired && frames < fps * (expectedLifetime + 2f)) {
                    slip.stepTrails(1f / fps);
                    // Vanilla terrain advances these faders separately from the haunt.
                    for (var segment : segments) segment.fader.advance(1f / fps);
                    frames++;
                    if (frames == (int) (previousLifetime * fps)) {
                        check(!trail.expired && segments.stream().anyMatch(s -> !s.fader.isFadedOut()),
                                "stream is still present at the old expiry time");
                    }
                }
                check(trail.expired && trail.removed, "finished stream is removed");
                check(Math.abs(frames / (float) fps - expectedLifetime) <= 3f / fps,
                        "stream lifetime follows its multiplier at " + fps + " Hz with " + count + " segments");
                check(segments.size() == count && segments.stream().allMatch(s -> s.fader.isFadedOut()),
                        "segments fade in place before terrain removal");
                slip.cleanup();
            }
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
