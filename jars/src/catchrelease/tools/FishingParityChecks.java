package catchrelease.tools;

import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.minigame.FishingMinigame;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import catchrelease.memory.upgrades.StatIds;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.lazywizard.lazylib.MathUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class FishingParityChecks {

    static String context;
    static int frames;
    static int caught;
    static int lost;

    static class Environment implements AutoCloseable {

        final Map<String, Object> persistent = new HashMap<>();
        final UpgradeManager upgrades = new UpgradeManager();
        boolean dev;

        final StarSystemAPI system = proxy(StarSystemAPI.class, (object, method, args) -> switch (method.getName()) {
            case "getId" -> "fish-test-system";
            default -> throw new AssertionError("Unexpected system call: " + method);
        });

        Environment() {
            if (Global.getSector() != null || Global.getSettings() != null || Global.getCombatEngine() != null) {
                throw new IllegalStateException("Run these checks in a separate JVM, not inside Starsector.");
            }
            MemoryAPI memory = proxy(MemoryAPI.class, (object, method, args) -> switch (method.getName()) {
                case "contains" -> UpgradeManager.MEMORY_ID.equals(args[0]);
                case "get" -> UpgradeManager.MEMORY_ID.equals(args[0]) ? upgrades : null;
                default -> throw new AssertionError("Unexpected memory call: " + method);
            });
            CampaignClockAPI clock = proxy(CampaignClockAPI.class, (object, method, args) -> switch (method.getName()) {
                case "getElapsedDaysSince" -> 0f;
                default -> throw new AssertionError("Unexpected clock call: " + method);
            });
            SectorAPI sector = proxy(SectorAPI.class, (object, method, args) -> switch (method.getName()) {
                case "getMemoryWithoutUpdate" -> memory;
                case "getPersistentData" -> persistent;
                case "getClock" -> clock;
                default -> throw new AssertionError("Unexpected sector call: " + method);
            });
            SettingsAPI settings = proxy(SettingsAPI.class, (object, method, args) -> switch (method.getName()) {
                case "isDevMode" -> dev;
                default -> throw new AssertionError("Unexpected settings call: " + method);
            });
            Global.setSector(sector);
            Global.setSettings(settings);
        }

        void setStat(String id, float value) {
            UpgradeStat stat = new UpgradeStat();
            stat.id = id;
            stat.baseType = UpgradeStat.BaseType.DOUBLE;
            stat.upgradeType = UpgradeStat.UpgradeType.FLAT;
            stat.baseValue = value;
            upgrades.levelMap.put(id, stat);
        }

        float setCalm(boolean active) {
            ArrayList<FishRumors.Saved> rumors = new ArrayList<>();
            if (active) {
                FishRumors.Saved rumor = new FishRumors.Saved();
                rumor.systemId = system.getId();
                rumor.kindId = FishRumors.Kind.CALM.id;
                rumors.add(rumor);
            }
            persistent.put(FishRumors.ACTIVE_KEY, rumors);
            return FishRumors.getMotionMult(system);
        }

        @Override
        public void close() {
            Global.setSettings(null);
            Global.setSector(null);
        }
    }

    static class CatchOnlyMinigame extends FishingMinigame {

        CatchOnlyMinigame(FishSpec fish, Tackle tackle, StarSystemAPI system) {
            super(fish, tackle, system);
        }

        @Override
        protected void rollTreasure() {
            // Treasure consumes random draws unrelated to the catch model.
            takenTreasures.clear();
            treasure = null;
            treasuresLeft = 0;
            treasureClock = 0f;
        }
    }

    public static void main(String[] args) throws Exception {
        jitterFormula();
        try (Environment environment = new Environment()) {
            for (FishMotion motion : FishMotion.values()) {
                for (Tackle tackle : Tackle.values()) {
                    for (int fps : new int[]{30, 60, 144}) {
                        for (int seed = 0; seed < 8; seed++) trace(environment, motion, tackle, fps, seed);
                    }
                }
            }
            trace(environment, null, Tackle.NONE, 60, 9);
        }
        require(Global.getSector() == null && Global.getSettings() == null, "test globals cleared");
        require(caught > 0 && lost > 0, "traces must reach both terminal states");
        System.out.printf("Physics parity passed: %,d frames, %,d catches, %,d losses; all movements/tackle, "
                + "30/60/144Hz, restarts, live modifiers, protection and jitter formula.%n", frames, caught, lost);
    }

    static void trace(Environment environment, FishMotion motion, Tackle tackle, int fps, int seed) {
        context = motion + "/" + tackle + "/" + fps + "/" + seed;
        FishSpec fish = new FishSpec();
        fish.motion = motion;
        fish.difficulty = 5 + seed * 27;
        fish.motionSpeed = 0.5f + seed * 0.2f;
        fish.restlessness = 0.2f + seed * 0.3f;
        fish.progressRateMult = 0.5f + seed * 0.17f;
        fish.escapeRateMult = 0.8f + seed * 0.21f;
        fish.rarity = seed % 2 == 0 ? FishRarity.COMMON : FishRarity.LEGENDARY;
        environment.dev = seed % 3 == 0;
        float rumorSpeed = environment.setCalm(seed % 4 < 2);
        float bar = seed == 0 ? 1f : seed == 7 ? 400f : 80 + seed * 25;
        float gain = 1 + seed * 0.08f;
        float loss = 1 - seed * 0.07f;
        environment.setStat(StatIds.FISHING_BAR_SIZE, bar);
        environment.setStat(StatIds.MINIGAME_PROGRESS_RATE, gain);
        environment.setStat(StatIds.MINIGAME_ESCAPE_RESIST, loss);
        Random toolRandom = new Random(seed);
        MathUtils.getRandom().setSeed(seed);
        FishingMinigame game = new CatchOnlyMinigame(fish, tackle, environment.system);
        FishingSimulation tool = new FishingSimulation(fish.difficulty,
                fish.motionSpeed * (fish.rarity == FishRarity.LEGENDARY ? 1f : rumorSpeed),
                fish.restlessness, fish.progressRateMult, fish.escapeRateMult, motion,
                tackle, bar,
                (min, max) -> min + toolRandom.nextFloat() * (max - min));
        tool.setPlayerRates(gain, loss);
        tool.setCannotLose(environment.dev);
        compare(game, tool);
        for (int frame = 0; frame < fps * 20; frame++) {
            boolean held = seed % 2 == 0
                    ? game.getFishPosition() > game.getBarPosition() + game.getBarHeightFraction() / 2f
                    : Math.sin(frame * 0.067) > 0;
            game.advance(1f / fps, held);
            tool.advance(1f / fps, held);
            compare(game, tool);
            frames++;
            if (!game.isRunning()) {
                if (game.isCaught()) caught++;
                else lost++;
                float time = game.getTimeTotal();
                game.advance(1f, !held);
                tool.advance(1f, !held);
                compare(game, tool);
                equal(time, game.getTimeTotal());
            }
            if (!game.isRunning() || frame == fps * 8) {
                game.restart();
                tool.restart();
                compare(game, tool);
            }
            if (frame == fps * 5) {
                gain *= 1.3f;
                loss *= 0.7f;
                environment.setStat(StatIds.MINIGAME_PROGRESS_RATE, gain);
                environment.setStat(StatIds.MINIGAME_ESCAPE_RESIST, loss);
                tool.setPlayerRates(gain, loss);
            }
            if (frame == fps * 9) {
                game.setCannotLose(!environment.dev);
                tool.setCannotLose(!environment.dev);
            }
            if (frame == fps * 12) {
                game.setMotion(FishMotion.WEAVER);
                tool.setMotion(FishMotion.WEAVER);
                game.setDifficulty(80f);
                tool.setDifficulty(80f);
                game.setMotionSpeed(1.25f);
                tool.setMotionSpeed(1.25f);
            }
        }
        game.setCaught();
        tool.setCaught();
        compare(game, tool);
        game.setEscaped();
        tool.setEscaped();
        compare(game, tool);
    }

    static void compare(FishingMinigame game, FishingSimulation tool) {
        equal(game.getFishPosition(), tool.getFishPosition());
        equal(game.getFishVelocity(), tool.getFishVelocity());
        equal(game.getBarPosition(), tool.getBarPosition());
        equal(game.getBarHeightFraction(), tool.getBarHeightFraction());
        equal(game.getProgress(), tool.getProgress());
        equal(game.getTimeHeld(), tool.getTimeHeld());
        equal(game.getTimeTotal(), tool.getTimeTotal());
        require(game.getState().name().equals(tool.getState().name()), "state");
        require(game.isCannotLose() == tool.isCannotLose(), "protection");
    }

    static void jitterFormula() throws Exception {
        String game = methodBody(Path.of("jars/src/catchrelease/campaign/fish/minigame/FishingMinigamePanel.java"),
                "protected float getJitter(float offset)")
                .replace("float time =", "time =").replace("jitterTime", "time")
                .replace("minigame.getFishVelocity()", "velocity").replace("minigame.getFish().jitter", "jitter");
        String tool = methodBody(Path.of("jars/src/catchrelease/tools/FishingSimulation.java"), "public static float jitter(");
        require(game.replaceAll("\\s+", "").equals(tool.replaceAll("\\s+", "")),
                "jitter formula changed; compare the game panel with the tool");
    }

    static String methodBody(Path file, String signature) throws Exception {
        String source = Files.readString(file);
        int method = source.indexOf(signature);
        require(method >= 0, "missing " + signature);
        int start = source.indexOf('{', method);
        return source.substring(start + 1, source.indexOf('}', start));
    }

    static void equal(float a, float b) {
        require(Float.floatToIntBits(a) == Float.floatToIntBits(b), a + " != " + b);
    }

    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(context + ": " + message);
    }
}
