package catchrelease.tools;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryTrail;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.rendering.SearchlightImpressionRenderer;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

import java.lang.invoke.MethodHandles;
import java.util.List;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class LegendaryTrailChecks {

    private static int checks;

    private static final class Mote extends FishEntityPlugin {

        final FishSpec spec = new FishSpec();
        final Vector2f at = new Vector2f();
        float visibility = 1f;
        float sensor = 1f;
        float contact = 1f;
        boolean local = true;
        boolean expired;
        int segments;
        int cuts;
        float angle;
        float alpha;

        Mote(FishRarity rarity) {
            spec.rarity = rarity;
            entity = proxy(SectorEntityToken.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> at;
                case "isInCurrentLocation" -> local;
                case "isExpired" -> expired;
                case "getSensorFaderBrightness" -> sensor;
                case "getSensorContactFaderBrightness" -> contact;
                default -> throw new AssertionError(m);
            });
            field("lampFade", float.class, 1f);
            field("trail", LegendaryTrail.class, new LegendaryTrail() {
                @Override protected void emit(SectorEntityToken mote, float heading, float opacity) {
                    segments++;
                    angle = heading;
                    alpha = opacity;
                }
                @Override protected void cut(SectorEntityToken mote) { cuts++; }
            });
        }

        @Override public FishSpec getFishSpec() { return spec; }
        @Override public float getVisibility() { return visibility; }
        @Override protected boolean isLampBound() { return true; }

        void tick(float dx, float dy) {
            at.translate(dx, dy);
            advanceTrail();
        }

        void field(String name, Class<?> type, Object value) {
            try {
                MethodHandles.privateLookupIn(FishEntityPlugin.class, MethodHandles.lookup())
                        .findVarHandle(FishEntityPlugin.class, name, type).set(this, value);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    public static void main(String[] args) {
        buried();
        for (FishRarity rarity : FishRarity.values()) {
            Mote fish = new Mote(rarity);
            fish.tick(0f, 0f);
            fish.tick(10f, 0f);
            check(fish.segments == (rarity == FishRarity.LEGENDARY ? 1 : 0), "rarity gate: " + rarity);
        }

        Mote decoy = new Mote(FishRarity.RARE);
        decoy.field("decoyAnchor", SectorEntityToken.class, decoy.getMote());
        decoy.tick(0f, 0f);
        decoy.tick(10f, 0f);
        check(decoy.segments == 1, "Quorum decoy must match the real fish");
        decoy.field("phantom", boolean.class, true);
        decoy.tick(10f, 0f);
        check(decoy.segments == 2, "uncatchable copies retain the same trail");

        Mote fish = new Mote(FishRarity.LEGENDARY);
        fish.tick(0f, 0f);
        fish.tick(0f, 10f);
        check(fish.angle == 90f, "heading follows movement, not token facing");
        fish.sensor = 0.5f;
        fish.contact = 0.4f;
        fish.visibility = 0.3f;
        fish.field("lampFade", float.class, 0.6f);
        fish.tick(-10f, 0f);
        check(Math.abs(fish.alpha - 0.036f) < 0.00001f, "lamp, dive and sensor fades combine");
        check(fish.angle == 180f, "turns follow sampled positions");

        fish.tick(180f, 0f);
        check(fish.cuts == 1 && fish.segments == 2, "Manta slot swap cannot bridge positions");
        fish.tick(5f, 0f);
        check(fish.segments == 3, "trail resumes after a teleport");
        fish.tick(0f, 0f);
        check(fish.segments == 3, "stationary fish do not stack trail pieces");
        fish.advance(0f);
        check(fish.segments == 3, "pause does not emit");

        for (String state : new String[]{"held", "hidden", "elsewhere", "expired"}) {
            Mote stopped = new Mote(FishRarity.LEGENDARY);
            stopped.tick(0f, 0f);
            stopped.tick(10f, 0f);
            switch (state) {
                case "held" -> stopped.setHeld(true);
                case "hidden" -> stopped.visibility = 0f;
                case "elsewhere" -> stopped.local = false;
                case "expired" -> stopped.expired = true;
            }
            stopped.tick(10f, 0f);
            stopped.tick(10f, 0f);
            check(stopped.segments == 1 && stopped.cuts == 1, state + " stops and cuts once");
            stopped.setHeld(false);
            stopped.visibility = 1f;
            stopped.local = true;
            stopped.expired = false;
            stopped.tick(10f, 0f);
            check(stopped.segments == 1, state + " resumes with a fresh position sample");
            stopped.tick(10f, 0f);
            check(stopped.segments == 2, state + " resumes emission");
        }

        for (int fps : new int[]{30, 60, 144}) {
            Mote moray = new Mote(FishRarity.LEGENDARY);
            moray.tick(0f, 0f);
            for (int i = 0; i < fps; i++) moray.tick(900f / fps, 0f);
            check(moray.cuts == 0 && moray.segments == fps, "Moray dash at " + fps + " fps");
        }
        System.out.println("Legendary trail checks passed: " + checks);
    }

    private static void buried() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            Mote token = new Mote(FishRarity.LEGENDARY);
            float[] reveal = {0.7f};
            int[] segments = {0};
            var impressions = new SearchlightImpressionRenderer(List.of(), env.lamps, env.system) {
                @Override public float getRevealStrength(SectorEntityToken mote) { return reveal[0]; }
            };
            ReflectionUtils.set(env.lamps, "impressionRenderer", impressions, true);
            class Buried extends BuriedMoteEntityPlugin {

                Buried() { entity = token.getMote(); }
                @Override public FishSpec getFishSpec() { return token.spec; }
                void tick() { advanceTrail(); }
            }
            Buried buried = new Buried();
            ReflectionUtils.set(buried, "trail", new LegendaryTrail() {
                @Override protected void emit(SectorEntityToken mote, float angle, float alpha) {
                    check(alpha == reveal[0], "buried trail uses impression visibility");
                    segments[0]++;
                }
                @Override protected void cut(SectorEntityToken mote) { }
            }, true);
            buried.tick();
            token.at.translate(10f, 0f);
            buried.tick();
            check(segments[0] == 1 && env.haunt.getActiveSpeciesId() == null,
                    "buried legendary trail exists before any haunt");
            reveal[0] = 0f;
            token.at.translate(10f, 0f);
            buried.tick();
            check(segments[0] == 1, "unrevealed buried fish do not leak a trail");
            token.spec.rarity = FishRarity.COMMON;
            reveal[0] = 1f;
            token.at.translate(10f, 0f);
            buried.tick();
            check(segments[0] == 1, "ordinary buried fish do not gain legendary trails");
        }
    }

    private static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
}
