package catchrelease.tools;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.legendary.*;
import catchrelease.campaign.fish.spawner.BuriedMoteSpawner;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import com.fs.starfarer.api.campaign.PlanetAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class LegendarySpawnChecks {

    private static int checks;

    private static class Population extends BuriedMoteSpawner {

        void cull(LegendaryEscapeChecks.Environment env) { cullDistant(env.system, new Vector2f()); }
        int nearby(LegendaryEscapeChecks.Environment env) { return getNearby(env.system, new Vector2f()).size(); }
    }

    public static void main(String[] args) {
        geometry();
        lifecycle();
        System.out.println("Legendary spawn checks passed: " + checks);
    }

    private static void geometry() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            FishSpec spec = new FishSpec();
            spec.id = "lantern_jack";
            spec.rarity = FishRarity.LEGENDARY;
            check(LegendarySpawns.center(env.system).length() == 0f, "empty system uses origin");
            PlanetAPI small = planet(8000f, 5000f, 80f);
            PlanetAPI large = planet(-10000f, 3000f, 300f);
            env.stars.addAll(List.of(small, large));
            check(LegendarySpawns.center(env.system).equals(large.getLocation()), "largest planet in starless system");
            positions(env, spec, large.getLocation());
            env.star(1000f, -2000f, 500f, 1800f);
            Vector2f sun = new Vector2f(1000f, -2000f);
            check(LegendarySpawns.center(env.system).equals(sun), "sun takes priority over planets");
            positions(env, spec, sun);
            spec.id = "false_dawn";
            positions(env, spec, sun);
            var corona = FalseDawnOrbit.findCorona(env.system);
            for (int i = 0; i < 200; i++) {
                Vector2f at = LegendarySpawns.position(env.system, spec);
                float radius = Vector2f.sub(at, sun, null).length();
                check(radius >= FalseDawnOrbit.innerRadius(corona) - 0.01f
                        && radius <= FalseDawnOrbit.outerRadius(corona) + 0.01f, "Dawn retains corona orbit");
            }
            env.star(25000f, 0f, 1200f, 4000f);
            positions(env, spec, new Vector2f(25000f, 0f));
        }
    }

    private static void positions(LegendaryEscapeChecks.Environment env, FishSpec spec, Vector2f center) {
        for (int i = 0; i < 500; i++) {
            Vector2f at = LegendarySpawns.position(env.system, spec);
            check(at != null && Vector2f.sub(at, center, null).length() <= 6000.01f, "spawn stays within 6000");
            if (LegendaryStarAvoidance.applies(spec)) {
                check(Vector2f.sub(LegendaryStarAvoidance.place(env.system, at, 0f), at, null).length() < 0.01f,
                        "spawn avoids stellar hazards");
            }
        }
    }

    private static PlanetAPI planet(float x, float y, float radius) {
        return proxy(PlanetAPI.class, (p, m, a) -> switch (m.getName()) {
            case "isExpired", "isStar" -> false;
            case "getLocation" -> new Vector2f(x, y);
            case "getRadius" -> radius;
            default -> throw new AssertionError(m);
        });
    }

    private static void lifecycle() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            for (String id : List.of("lantern_jack", "quorum", "slipstream_moray", "abyssal_ghost_manta", "longliner")) {
                FishSpec spec = new FishSpec();
                spec.id = id;
                spec.rarity = FishRarity.LEGENDARY;
                env.specs.put(id, spec);
                LegendaryChases.getState(id).systemId = env.system.getId();
            }
            env.haunt.reportCurrentLocationChanged(null, env.system);
            check(env.fish.isEmpty(), "tutorial gates entry spawns");
            env.tutorialStage = FishingIntro.DONE;
            env.haunt.reportCurrentLocationChanged(null, env.system);
            check(env.fish.size() == 4, "every resident spawns on entry; Longliner keeps its boat");
            LegendarySpawns.populate(env.system);
            check(env.fish.size() == 4, "load/entry reconciliation cannot duplicate residents");
            for (var fish : env.fish) {
                check(fish.at.length() <= 6000.01f, "entry spawn radius");
                check(LegendaryChases.getState(fish.spec.id).seenAt == 0L, "hidden spawn is not a sighting");
                fish.at.set(50000f, 50000f);
            }
            Population population = new Population();
            population.cull(env);
            check(env.fish.stream().noneMatch(f -> f.expired || f.removed), "distant residents are not culled");
            env.fish.forEach(f -> f.at.set(0f, 0f));
            check(population.nearby(env) == 0, "residents do not fill the ordinary population quota");
            env.haunt.reportCurrentLocationChanged(env.system, null);
            check(env.fish.stream().allMatch(f -> f.expired), "departure removes residents");
            LegendaryChases.noteCaught("quorum");
            env.haunt.reportCurrentLocationChanged(null, env.system);
            check(env.fish.stream().filter(f -> !f.expired).count() == 3, "return restores uncaught residents only");
        }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
