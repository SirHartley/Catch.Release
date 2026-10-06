package catchrelease.tools;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.legendary.LanternSensorGhostsModule;
import catchrelease.campaign.fish.legendary.LegendaryHaunt;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ghosts.BaseSensorGhost;
import com.fs.starfarer.api.impl.campaign.ghosts.GBGoInDirection;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashSet;
import java.util.Set;

public final class HauntSensorChecks {

    private static int checks;

    private static final class Ghosts extends LanternSensorGhostsModule {

        Ghosts(StarSystemAPI system, long seed) { super(system, new FishSpec()); random.setSeed(seed); }
        void spawn() { spawnGhost(); }
        BaseSensorGhost latest() { return ghosts.get(ghosts.size() - 1); }
        int active() { return ghosts.size(); }
        int tracked() { return spawned.size(); }
    }

    public static void main(String[] args) throws Exception {
        try (HauntWreckChecks.Environment env = new HauntWreckChecks.Environment()) {
            Set<Class<?>> behaviors = new HashSet<>();
            for (int seed = 0; seed < 30; seed++) {
                Ghosts module = new Ghosts(env.system, seed);
                module.spawn();
                module.setIntensity(0f);
                BaseSensorGhost ghost = module.latest();
                behaviors.add(ghost.getScript().get(0).getClass());
                HauntWreckChecks.Entity entity = env.entities.get(env.entities.size() - 1);
                require(entity.location == env.system && entity.tags.contains(LegendaryHaunt.HAUNT_TAG)
                        && entity.tags.contains(Tags.NON_CLICKABLE), "Owned local sensor contact");
                require(!ghost.isDespawnOutsideSector() && !ghost.isDespawnInAbyss(), "No hyperspace coordinate assumptions");
                Vector2f start = new Vector2f(entity.position);
                env.playerVelocity.set(40f, 20f);
                for (int i = 0; i < 300; i++) {
                    env.timestamp += 100;
                    module.advance(0.1f);
                    entity.fade(0.1f);
                }
                require(Vector2f.sub(entity.position, start, null).length() > 1f, "Vanilla behavior moves contact");
                require(ghost.isDone() && entity.expired, "Behavior ends in seconds, then fades");
                module.cleanup();
                require(module.active() == 0 && module.tracked() == 0, "Cleanup releases all ownership");
            }
            require(behaviors.size() == 3, "All three vanilla movement patterns");

            Ghosts module = new Ghosts(env.system, 77);
            module.spawn();
            module.setIntensity(0f);
            BaseSensorGhost ghost = module.latest();
            HauntWreckChecks.Entity fading = env.entities.get(env.entities.size() - 1);
            ghost.getScript().clear();
            module.advance(0.01f);
            require(ghost.getEntity() == null && !fading.expired && module.tracked() == 1,
                    "Retain token after vanilla starts fading");
            module.cleanup();
            require(fading.expired && fading.removed, "Teardown removes a fading contact immediately");

            module = new Ghosts(env.system, 78);
            module.spawn();
            module.setIntensity(0f);
            HauntWreckChecks.Entity removed = env.entities.get(env.entities.size() - 1);
            removed.location = null;
            module.advance(0.1f);
            require(module.active() == 0 && module.tracked() == 0, "External removal skips vanilla advancement");
            module.cleanup();

            module = new Ghosts(env.system, 79);
            for (int i = 0; i < 4; i++) {
                module.spawn();
                module.latest().getScript().clear();
                module.latest().addBehavior(new GBGoInDirection(100f, 0f, 12));
            }
            int before = env.entities.size();
            module.advance(10f);
            require(env.entities.size() == before, "Active cap includes all owned contacts");
            module.cleanup();
            require(env.entities.subList(before - 4, before).stream().allMatch(e -> e.expired && e.removed),
                    "Haunt teardown removes active contacts");
        }
        System.out.println("Haunt sensors: " + checks + " checks passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
