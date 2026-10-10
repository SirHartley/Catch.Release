package catchrelease.tools;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryTrail;
import catchrelease.campaign.fish.legendary.MantaFormationModule;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.helper.math.CircularArc;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class LegendaryTrailChecks {

    private static int checks;

    private record Vertex(float x, float y, float alpha) {

    }

    private static class Trail extends LegendaryTrail {

        final List<Vertex> vertices = new ArrayList<>();
        int registrations;

        @Override protected void register() { registrations++; }
        @Override protected void drawVertex(float x, float y, float u, float v, float alpha) {
            vertices.add(new Vertex(x, y, alpha));
        }
        float paint(Fixture f) {
            vertices.clear();
            renderTrail(f.viewport);
            return vertices.stream().map(Vertex::alpha).max(Float::compare).orElse(0f);
        }
    }

    private static class Mote extends FishEntityPlugin {

        final FishSpec spec = new FishSpec();
        final Vector2f at = new Vector2f();
        final Trail recorded = new Trail();
        float visibility = 1f;
        float sensor = 1f;
        boolean expired;

        Mote(Fixture f, FishRarity rarity) {
            spec.rarity = rarity;
            entity = proxy(SectorEntityToken.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> at;
                case "getContainingLocation" -> f.env.system;
                case "isInCurrentLocation" -> f.location == f.env.system;
                case "isExpired" -> expired;
                case "getSensorFaderBrightness", "getSensorContactFaderBrightness" -> sensor;
                default -> throw new AssertionError(m);
            });
            field("lampFade", 1f);
            field("trail", recorded);
        }
        @Override public FishSpec getFishSpec() { return spec; }
        @Override public float getVisibility() { return visibility; }
        @Override protected boolean isLampBound() { return true; }
        float opacity() { return getMoteAlpha(); }
        void tick(float x, float y) { at.set(x, y); advanceTrail(); }
        void field(String name, Object value) { ReflectionUtils.set(this, name, value, true); }
    }

    private static class Lamp extends Searchlight {

        boolean live = true;
        @Override public boolean isRuntimeCurrent() { return live; }
    }

    private static class Fixture implements AutoCloseable {

        final LegendaryEscapeChecks.Environment env = new LegendaryEscapeChecks.Environment();
        LocationAPI location = env.system;
        boolean paused;
        boolean on = true;
        boolean installed = true;
        boolean inViewport = true;
        float viewportAlpha = 1f;
        final List<Searchlight> lights = new ArrayList<>();
        final SearchlightAbilityPlugin lamps = new SearchlightAbilityPlugin() {

            @Override public boolean isRuntimeCurrent() { return on && location == env.system; }
        };
        final ViewportAPI viewport = proxy(ViewportAPI.class, (p, m, a) -> switch (m.getName()) {
            case "getAlphaMult" -> viewportAlpha;
            case "isNearViewport" -> inViewport;
            default -> throw new AssertionError(m);
        });

        Fixture() {
            SectorAPI original = Global.getSector();
            UpgradeManager upgrades = new UpgradeManager();
            MemoryAPI memory = proxy(MemoryAPI.class, (p, m, a) -> switch (m.getName()) {
                case "contains" -> UpgradeManager.MEMORY_ID.equals(a[0]);
                case "get" -> upgrades;
                default -> throw new AssertionError(m);
            });
            CampaignFleetAPI player = proxy(CampaignFleetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getAbility" -> installed ? lamps : null;
                default -> throw new AssertionError(m);
            });
            Global.setSector(proxy(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getCurrentLocation" -> location;
                case "getPlayerFleet" -> player;
                case "isPaused" -> paused;
                case "getMemoryWithoutUpdate" -> memory;
                default -> m.invoke(original, a);
            }));
            ReflectionUtils.set(lamps, "activeSearchlights", lights, true);
            lamp(0f, 0f);
        }
        Lamp lamp(float x, float y) {
            Lamp light = new Lamp();
            light.updateRenderLoc(new Vector2f(x, y));
            ReflectionUtils.set(light, "arc", new CircularArc(new Vector2f(), 300f, 0f, 90f), true);
            lights.add(light);
            return light;
        }
        @Override public void close() { env.close(); }
    }

    public static void main(String[] args) {
        eligibility();
        hiddenTrail();
        mask();
        lifecycle();
        System.out.println("Legendary trail checks passed: " + checks);
    }

    private static void eligibility() {
        try (Fixture f = new Fixture()) {
            for (FishRarity rarity : FishRarity.values()) {
                Mote fish = new Mote(f, rarity);
                fish.tick(0f, 0f);
                fish.tick(10f, 0f);
                check((fish.recorded.paint(f) > 0f) == (rarity == FishRarity.LEGENDARY), "rarity gate " + rarity);
            }
            Mote decoy = new Mote(f, FishRarity.RARE);
            decoy.field("decoyAnchor", decoy.getMote());
            decoy.tick(0f, 0f);
            decoy.tick(10f, 0f);
            check(decoy.recorded.paint(f) > 0f, "Quorum decoy matches the real trail");
            decoy.field("phantom", true);
            decoy.tick(20f, 0f);
            check(decoy.recorded.paint(f) > 0f, "phantoms keep their trails");

            Mote token = new Mote(f, FishRarity.LEGENDARY);
            class Buried extends BuriedMoteEntityPlugin {

                Buried() { entity = token.getMote(); }
                @Override public FishSpec getFishSpec() { return token.spec; }
                void tick() { advanceTrail(); }
            }
            Buried buried = new Buried();
            ReflectionUtils.set(buried, "trail", token.recorded, true);
            f.on = false;
            buried.tick();
            token.at.set(10f, 0f);
            buried.tick();
            check(token.recorded.paint(f) == 0f, "buried history is hidden without lamps");
            f.on = true;
            check(token.recorded.paint(f) > 0f && f.env.haunt.getActiveSpeciesId() == null,
                    "unrevealed buried history appears under lamps without a haunt");
            token.spec.rarity = FishRarity.COMMON;
            token.recorded.advance(5.1f);
            token.at.set(20f, 0f);
            buried.tick();
            check(token.recorded.paint(f) == 0f, "ordinary buried fish have no trails");
        }
    }

    private static void hiddenTrail() {
        try (Fixture f = new Fixture()) {
            Mote fish = new Mote(f, FishRarity.LEGENDARY);
            fish.visibility = 0f;
            fish.sensor = 0f;
            fish.field("lampFade", 0f);
            check(fish.opacity() == 0f, "fixture mote really is invisible");
            f.on = false;
            fish.tick(0f, 0f);
            fish.tick(10f, 0f);
            check(fish.recorded.paint(f) == 0f, "darkness hides sampled movement");
            f.on = true;
            float lit = fish.recorded.paint(f);
            check(lit > 0f, "beam exposes history emitted while fish was invisible");
            f.lights.get(0).updateRenderLoc(new Vector2f(1000f, 0f));
            check(fish.recorded.paint(f) == 0f, "moving lamp away hides existing trail immediately");
            f.lights.get(0).updateRenderLoc(new Vector2f());
            check(fish.recorded.paint(f) == lit, "returning beam exposes the same history without new samples");
            f.viewportAlpha = 0.5f;
            check(Math.abs(fish.recorded.paint(f) - lit * 0.5f) < 0.00001f, "viewport fade multiplies trail alpha");
            f.inViewport = false;
            check(fish.recorded.paint(f) == 0f && fish.recorded.vertices.isEmpty(), "offscreen trail is culled");
            f.inViewport = true;
            f.paused = true;
            fish.recorded.advance(2f);
            fish.tick(20f, 0f);
            check(Math.abs(fish.recorded.paint(f) - lit * 0.5f) < 0.00001f, "pause freezes history and lifetime");
            f.paused = false;
            fish.recorded.advance(2.5f);
            check(fish.recorded.paint(f) > 0f && fish.recorded.paint(f) < lit * 0.5f, "history fades with age");
            fish.recorded.advance(2.6f);
            check(fish.recorded.paint(f) == 0f, "short trail expires even without mote callbacks");
            check(fish.recorded.registrations == 1, "one transient renderer per live trail");
        }
    }

    private static void mask() {
        try (Fixture f = new Fixture()) {
            Mote fish = new Mote(f, FishRarity.LEGENDARY);
            fish.tick(180f, 0f);
            fish.tick(220f, 0f);
            fish.tick(260f, 0f);
            check(SearchlightAbilityPlugin.getBeamVisibilityAt(fish.at) == 0f, "head has left the circle");
            check(fish.recorded.paint(f) > 0f, "illuminated tail stays visible after head leaves beam");
            check(fish.recorded.vertices.stream().anyMatch(v -> v.x() > 240f && v.alpha() == 0f), "unlit tail vertices are hidden");
            check(fish.recorded.vertices.stream().filter(v -> v.x() >= 240f).allMatch(v -> v.alpha() == 0f), "no full-strip visibility leak");

            TackleManager.fit(Tackle.Fit.SEARCHLIGHT, Tackle.FANNED_ARRAY);
            f.lights.get(0).updateRenderLoc(new Vector2f(300f, 0f));
            Mote fan = new Mote(f, FishRarity.LEGENDARY);
            fan.tick(100f, -40f);
            fan.tick(100f, 40f);
            check(SearchlightAbilityPlugin.getBeamVisibilityAt(new Vector2f(100f, -40f)) == 0f
                    && SearchlightAbilityPlugin.getBeamVisibilityAt(fan.at) == 0f, "both segment ends outside narrow fan");
            check(fan.recorded.paint(f) > 0f, "subdivision finds fan crossing between unlit samples");
            for (Vertex v : fan.recorded.vertices) {
                if (Math.abs(v.y()) >= 28f) check(v.alpha() == 0f, "fan edges stay masked");
            }
            ((Lamp) f.lights.get(0)).live = false;
            check(fan.recorded.paint(f) == 0f, "stale lamp does not expose trail");
            f.lamp(300f, 0f);
            check(fan.recorded.paint(f) > 0f, "another live lamp can reveal trail");
            ReflectionUtils.set(f.env.haunt, "activeSystem", f.env.system, true);
            MantaFormationModule flicker = new MantaFormationModule(f.env.system, new FishSpec()) {

                @Override public float getLampAlpha(Vector2f at) { return 0.25f; }
            };
            float full = fan.recorded.paint(f);
            ReflectionUtils.set(f.env.haunt, "modules", new ArrayList<>(List.of(flicker)), true);
            check(Math.abs(fan.recorded.paint(f) - full * 0.25f) < 0.00001f, "Manta flicker masks trail too");
            f.installed = false;
            check(fan.recorded.paint(f) == 0f, "removed lamp ability cannot expose history");
        }
    }

    private static void lifecycle() {
        try (Fixture f = new Fixture()) {
            Mote fish = new Mote(f, FishRarity.LEGENDARY);
            fish.tick(0f, 0f);
            fish.tick(10f, 0f);
            fish.tick(200f, 0f);
            fish.recorded.paint(f);
            check(fish.recorded.vertices.stream().allMatch(v -> v.x() <= 10f), "teleport does not bridge old and new positions");
            fish.tick(210f, 0f);
            check(fish.recorded.paint(f) > 0f && fish.recorded.vertices.stream().anyMatch(v -> v.x() >= 200f), "trail resumes after teleport");
            int vertices = fish.recorded.vertices.size();
            fish.tick(210f, 0f);
            fish.recorded.paint(f);
            check(fish.recorded.vertices.size() == vertices, "stationary fish add no points");
            fish.setHeld(true);
            fish.tick(220f, 0f);
            fish.recorded.paint(f);
            check(fish.recorded.vertices.size() == vertices, "held fish stop emitting, old history remains");
            fish.recorded.advance(5.1f);
            fish.setHeld(false);
            fish.tick(0f, 0f);
            check(fish.recorded.paint(f) == 0f, "release starts a fresh strip");
            fish.tick(10f, 0f);
            check(fish.recorded.paint(f) > 0f, "release resumes trail");
            fish.expired = true;
            fish.tick(20f, 0f);
            fish.recorded.advance(5.1f);
            check(fish.recorded.isExpired(), "removed mote retires after remaining history fades");

            Mote departed = new Mote(f, FishRarity.LEGENDARY);
            departed.tick(0f, 0f);
            departed.tick(10f, 0f);
            f.location = null;
            check(departed.recorded.isExpired() && departed.recorded.paint(f) == 0f, "departure immediately retires trail");
            f.location = f.env.system;
            check(departed.recorded.isExpired(), "old renderer cannot revive on returning");
        }
        for (int fps : new int[]{30, 60, 144}) {
            try (Fixture f = new Fixture()) {
                Mote fish = new Mote(f, FishRarity.LEGENDARY);
                for (int i = 0; i <= fps * 6; i++) {
                    fish.recorded.advance(1f / fps);
                    fish.tick(900f * i / fps, 0f);
                }
                f.lights.get(0).updateRenderLoc(new Vector2f(5250f, 0f));
                check(fish.recorded.paint(f) > 0f, "continuous Moray dash at " + fps + " fps");
                check(fish.recorded.vertices.stream().allMatch(v -> v.x() >= 880f), "expired history pruned at " + fps + " fps");
            }
        }
    }

    private static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
}
