package catchrelease.tools;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.coherence.CoherenceOverlayScript;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.*;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.memory.TransientMemory;
import catchrelease.rendering.plugins.ChromaticAberrationOverlay;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.GenericPluginManagerAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class HauntWindDownChecks {

    private static int checks;

    static class Module extends BaseHauntModule {

        int advances;
        int spawns;
        int failures;
        int cleanups;

        Module() { super(null, null); }
        @Override public void advance(float amount) {
            advances++;
            if (atFullIntensity()) spawns++;
        }
        @Override public void onFailedCatch(FishEntityPlugin fish) { failures++; }
        @Override public void cleanup() { cleanups++; }
    }

    static class Haunt extends LegendaryHaunt {

        final Module module = new Module();
        @Override protected List<HauntModule> buildModules(FishSpec spec, StarSystemAPI system) {
            return List.of(module);
        }
        void close() { stop(); }
        void restore() { restoreHunt(); }
    }

    static class Fixture implements AutoCloseable {

        final LegendaryEscapeChecks.Environment env = new LegendaryEscapeChecks.Environment();
        final LegendaryEscapeChecks.Fish fish = env.real("slipstream_moray");
        final LegendaryChases.Chase chase = LegendaryChases.getState(fish.spec.id);
        final Haunt haunt = new Haunt();
        final List<Object> listeners = new ArrayList<>();

        Fixture() {
            chase.systemId = env.system.getId();
            chase.provoked = true;
            env.scripts.clear();
            env.scripts.add(haunt);
            TransientMemory cache = new TransientMemory();
            cache.set("$" + ModPlugin.MOD_ID + "_" + FishSpecLoader.PATH, Map.of(fish.spec.id, fish.spec));
            GenericPluginManagerAPI plugins = proxy(GenericPluginManagerAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasPlugin" -> a[0] == TransientMemory.class;
                case "getPluginsOfClass" -> List.of(cache);
                default -> throw new AssertionError(m);
            });
            MemoryAPI memory = proxy(MemoryAPI.class, (p, m, a) -> {
                if (m.getName().equals("getInt") && TutorialConstants.STAGE_KEY.equals(a[0])) return FishingIntro.DONE;
                throw new AssertionError(m);
            });
            ListenerManagerAPI manager = proxy(ListenerManagerAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasListener" -> listeners.contains(a[0]);
                case "addListener" -> { listeners.add(a[0]); yield null; }
                case "removeListener" -> { listeners.remove(a[0]); yield null; }
                default -> throw new AssertionError(m);
            });
            SectorAPI sector = Global.getSector();
            Global.setSector(proxy(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getGenericPlugins" -> plugins;
                case "getMemoryWithoutUpdate" -> memory;
                case "getListenerManager" -> manager;
                default -> m.invoke(sector, a);
            }));
        }

        @Override public void close() {
            haunt.close();
            ChromaticAberrationOverlay.setLevel(0f);
            CoherenceOverlayScript.setHauntFloor(0f);
            env.close();
        }
    }

    public static void main(String[] args) throws Exception {
        fadeAndRestart();
        lampToggle();
        lostFish();
        returnToPatrol();
        restoreHunt();
        overlays();
        System.out.println("Haunt wind-down checks passed: " + checks);
    }

    private static void fadeAndRestart() {
        try (Fixture f = new Fixture()) {
            f.env.lampsOn = false;
            f.haunt.advance(1f);
            LegendaryHaunt.onFailedCatch(f.fish);
            f.fish.spec.id = MantaFormationModule.SPECIES;
            LegendaryHaunt.onMantaShieldPopped(f.fish);
            f.fish.spec.id = "slipstream_moray";
            check(f.haunt.getModuleCount() == 0, "nearby fish and failed catches cannot start haunts in darkness");
            f.env.lampsOn = true;
            f.haunt.advance(LegendaryHaunt.RAMP_SECONDS);
            near(1f, f.haunt.getIntensity(), "lamps and a sighting start the haunt");
            int spawns = f.haunt.module.spawns;
            int advances = f.haunt.module.advances;
            f.env.lampsOn = false;
            check(!f.haunt.isSightedNow(f.fish.spec, f.env.system), "nearby fish no longer count as sighted");
            f.haunt.advance(0f);
            near(1f, f.haunt.getIntensity(), "zero time does not change intensity");
            f.haunt.advance(3f);
            near(0.75f, f.haunt.getIntensity(), "lamp-off skips the sixty-second grace period");
            check(f.haunt.module.advances > advances && f.haunt.module.spawns == spawns,
                    "modules keep aging without spawning new effects");
            LegendaryHaunt.onFailedCatch(f.fish);
            check(f.haunt.module.failures == 0, "failed catch cannot retaliate while lamps are off");
            f.haunt.advance(9f);
            check(f.haunt.getModuleCount() == 0 && f.haunt.module.cleanups == 1, "fade cleans up after twelve seconds");
            check(!f.fish.expired && !f.chase.caught && !f.chase.provoked && !f.chase.roaming
                    && f.env.system.getId().equals(f.chase.systemId), "wind-down preserves the resident but ends the hunt");
            f.haunt.advance(100f);
            check(f.haunt.module.cleanups == 1 && f.haunt.getActiveSpeciesId() == null, "darkness cannot restart the haunt");
            f.env.lampsOn = true;
            f.fish.at.set(500f, 500f);
            f.haunt.advance(1f);
            near(0f, f.haunt.getIntensity(), "sighting alone cannot restart the abandoned hunt");
            f.chase.provoked = true;
            f.haunt.advance(1f);
            near(0.25f, f.haunt.getIntensity(), "a new provocation can restart the haunt");
        }
    }

    private static void returnToPatrol() {
        for (boolean held : new boolean[]{false, true}) try (Fixture f = new Fixture()) {
            f.chase.shieldPopped = true;
            f.chase.shieldUnits = 0;
            f.chase.residency = 7;
            f.chase.seenAt = 1234L;
            f.chase.encountered = true;
            f.haunt.advance(4f);
            f.env.star(0f, 0f, 900f, 2400f);
            f.fish.at.set(14000f, 0f);
            f.fish.startTravelDash(new Vector2f(900f, 0f), 100f);
            f.fish.setHeld(held);
            f.haunt.advance(60f);
            near(14000f, f.fish.at.x, "lost-contact grace does not teleport the fish");
            f.haunt.advance(12f);
            if (held) {
                near(14000f, f.fish.at.x, "held fish cannot be teleported");
                check(f.chase.roaming, "deferred return retains the hunt state");
                f.fish.setHeld(false);
                f.haunt.advance(0.1f);
            }
            check(f.fish.at.length() <= LegendarySpawns.RADIUS && f.fish.at.length() >= 2550f,
                    "failed haunt returns inside the spawn area but outside the corona");
            check(!f.fish.isDashing() && !f.chase.provoked && !f.chase.roaming,
                    "return clears escape movement and provocation");
            check(f.fish.getMovementVelocity().length() == 0f, "teleport is not harpoon lead velocity");
            check(f.chase.shieldPopped && f.chase.shieldUnits == 0 && f.chase.residency == 7
                            && f.chase.seenAt == 1234L && f.chase.encountered,
                    "return preserves defense progress, residency and sightings");
            Vector2f at = new Vector2f(f.fish.at);
            f.fish.advance(0.1f);
            check(Vector2f.sub(at, f.fish.at, null).length() > 0f, "returned fish resumes swimming");
        }
        try (Fixture f = new Fixture()) {
            f.haunt.advance(4f);
            f.fish.at.set(14000f, 0f);
            f.env.lampsOn = false;
            LegendaryEscapeChecks.Fish copy = new LegendaryEscapeChecks.Fish(f.env,
                    f.fish.spec.id, true, f.fish.getMote());
            copy.setHeld(true);
            f.haunt.advance(12f);
            near(14000f, f.fish.at.x, "held attached fish also defers the return");
            copy.setHeld(false);
            f.haunt.advance(0.1f);
            check(copy.expired && copy.removed && !f.fish.expired, "return removes attached decoys, not the real fish");
        }
    }

    private static void restoreHunt() {
        try (Fixture f = new Fixture()) {
            f.chase.roaming = true;
            f.fish.at.set(14000f, 0f);
            f.env.lampsOn = false;
            f.haunt.restore();
            check(f.haunt.getModuleCount() == 1 && f.chase.roaming,
                    "saved hunt restores even outside sight range with lamps off");
            f.haunt.advance(12f);
            check(!f.chase.roaming && f.fish.at.length() <= LegendarySpawns.RADIUS,
                    "restored dark haunt fades and returns the fish");
        }
    }

    private static void lampToggle() {
        try (Fixture f = new Fixture()) {
            f.haunt.advance(4f);
            f.env.lampsOn = false;
            f.haunt.advance(3f);
            f.fish.at.set(5000f, 0f);
            f.env.lampsOn = true;
            f.haunt.advance(3f);
            near(0.5f, f.haunt.getIntensity(), "turning lamps on without seeing the fish cannot cancel the fade");
            f.fish.at.set(500f, 500f);
            f.haunt.advance(1f);
            near(0.75f, f.haunt.getIntensity(), "a fresh sighting reverses the fade without a jump");
            f.env.lampsOn = false;
            f.haunt.advance(9f);
            check(f.haunt.getModuleCount() == 0, "partial-strength haunt fades from its current strength");
            f.env.lampsOn = true;
            LegendaryHaunt.onFailedCatch(f.fish);
            check(f.haunt.module.failures == 1 && f.haunt.getModuleCount() == 1,
                    "failed catches still activate the haunt while lamps are on");
        }
    }

    private static void lostFish() {
        try (Fixture f = new Fixture()) {
            f.haunt.advance(4f);
            f.fish.at.set(5000f, 0f);
            f.haunt.advance(59f);
            near(1f, f.haunt.getIntensity(), "lost-fish grace period remains while lamps are on");
            f.haunt.advance(2f);
            check(f.haunt.getIntensity() < 1f, "unseen haunt eventually fades with lamps still on");
            f.chase.caught = true;
            f.haunt.advance(0.1f);
            check(f.haunt.getModuleCount() == 0, "catching the legendary still cleans up immediately");
        }
    }

    private static void overlays() throws Exception {
        try (Fixture f = new Fixture()) {
            ChromaticAberrationModule chromatic = new ChromaticAberrationModule(null, null);
            CoherenceSurgeModule surge = new CoherenceSurgeModule(null, null);
            chromatic.advance(10f);
            surge.advance(10f);
            Object overlay = field(ChromaticAberrationOverlay.class, "instance").get(null);
            Field level = field(ChromaticAberrationOverlay.class, "level");
            Field floor = field(CoherenceOverlayScript.class, "hauntFloor");
            near(1f, level.getFloat(overlay), "chromatic effect reaches full strength");
            near(1f, floor.getFloat(null), "coherence surge reaches full strength");
            for (float intensity : new float[]{0.5f, 0.1f, 0.01f}) {
                chromatic.setIntensity(intensity);
                surge.setIntensity(intensity);
                chromatic.advance(0.1f);
                surge.advance(0.1f);
                near(intensity, level.getFloat(overlay), "chromatic effect fades below its starting floor");
                near(intensity, floor.getFloat(null), "coherence surge fades below its starting floor");
            }
            chromatic.cleanup();
            surge.cleanup();
            check(f.listeners.isEmpty(), "chromatic cleanup unregisters its listener");
            near(0f, floor.getFloat(null), "coherence cleanup clears only its haunt contribution");
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void near(float expected, float actual, String description) {
        check(Math.abs(expected - actual) < 0.0001f, description + ": " + actual);
    }

    private static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
}
