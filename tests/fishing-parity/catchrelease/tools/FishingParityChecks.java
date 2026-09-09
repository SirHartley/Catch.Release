package catchrelease.tools;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.minigame.FishingMinigame;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

public class FishingParityChecks {

    static String context;
    static int frames;
    static int caught;
    static int lost;

    public static void main(String[] args) throws Exception {
        constantsAndTackle();
        jitterFormula();
        for (FishMotion motion : FishMotion.values()) {
            for (var tackle : catchrelease.campaign.fish.tackle.Tackle.values()) {
                for (int fps : new int[]{30, 60, 144}) {
                    for (int seed = 0; seed < 8; seed++) trace(motion, tackle, fps, seed);
                }
            }
        }
        trace(null, catchrelease.campaign.fish.tackle.Tackle.NONE, 60, 9);
        require(caught > 0 && lost > 0, "traces must reach both terminal states");
        System.out.printf("Physics parity passed: %,d frames, %,d catches, %,d losses; all movements/tackle, "
                + "30/60/144Hz, restarts, live modifiers, protection and jitter formula.%n", frames, caught, lost);
    }

    static Tackle toolTackle(catchrelease.campaign.fish.tackle.Tackle game) {
        try { return Tackle.valueOf(game.name()); }
        catch (IllegalArgumentException ignored) { return Tackle.NONE; }
    }

    static void constantsAndTackle() throws Exception {
        for (var field : SimulationConstants.class.getDeclaredFields()) {
            context = field.getName();
            equal(field.getFloat(null), FishConstants.class.getField(field.getName()).getFloat(null));
        }
        require(Arrays.equals(Arrays.stream(FishMotion.values()).map(Enum::name).toArray(),
                Arrays.stream(catchrelease.campaign.fish.data.FishMotion.values()).map(Enum::name).toArray()), "movement roster");
        for (var game : catchrelease.campaign.fish.tackle.Tackle.values()) {
            Tackle tool = toolTackle(game);
            for (String name : new String[]{"barSizeMult", "barLiftMult", "barGravityMult", "progressMult", "escapeMult"}) {
                context = game.name() + "." + name;
                equal(Tackle.class.getDeclaredField(name).getFloat(tool), game.getClass().getField(name).getFloat(game));
            }
        }
        for (Tackle tool : Tackle.values()) catchrelease.campaign.fish.tackle.Tackle.valueOf(tool.name());
    }

    static void trace(FishMotion motion, catchrelease.campaign.fish.tackle.Tackle tackle, int fps, int seed) {
        context = motion + "/" + tackle + "/" + fps + "/" + seed;
        FishSpec fish = new FishSpec();
        fish.motion = motion == null ? null : catchrelease.campaign.fish.data.FishMotion.valueOf(motion.name());
        fish.difficulty = 5 + seed * 27;
        fish.motionSpeed = 0.5f + seed * 0.2f;
        fish.restlessness = 0.2f + seed * 0.3f;
        fish.progressRateMult = 0.5f + seed * 0.17f;
        fish.escapeRateMult = 0.8f + seed * 0.21f;
        fish.rarity = seed % 2 == 0 ? FishRarity.COMMON : FishRarity.LEGENDARY;
        Global.dev = seed % 3 == 0;
        FishRumors.speed = 0.7f + seed * 0.17f;
        UpgradeManager.bar = seed == 0 ? 1f : seed == 7 ? 400f : 80 + seed * 25;
        UpgradeManager.gain = 1 + seed * 0.08f;
        UpgradeManager.loss = 1 - seed * 0.07f;
        Random toolRandom = new Random(seed);
        MathUtils.rng = new Random(seed);
        FishingMinigame game = new FishingMinigame(fish, tackle);
        FishingSimulation tool = new FishingSimulation(fish.difficulty,
                fish.motionSpeed * (fish.rarity == FishRarity.LEGENDARY ? 1f : FishRumors.speed),
                fish.restlessness, fish.progressRateMult, fish.escapeRateMult, motion,
                toolTackle(tackle), UpgradeManager.bar,
                (min, max) -> min + toolRandom.nextFloat() * (max - min));
        tool.setPlayerRates(UpgradeManager.gain, UpgradeManager.loss);
        tool.setCannotLose(Global.dev);
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
                UpgradeManager.gain *= 1.3f;
                UpgradeManager.loss *= 0.7f;
                tool.setPlayerRates(UpgradeManager.gain, UpgradeManager.loss);
            }
            if (frame == fps * 9) {
                game.setCannotLose(!Global.dev);
                tool.setCannotLose(!Global.dev);
            }
            if (frame == fps * 12) {
                game.setMotion(catchrelease.campaign.fish.data.FishMotion.WEAVER);
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
                .replace("minigame.getFishVelocity()", "velocity").replace("minigame.getFish().jitter", "jitter")
                .replace("FishConstants", "SimulationConstants");
        String tool = methodBody(Path.of("dev-tools/src/catchrelease/tools/FishingSimulation.java"), "public static float jitter(");
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

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(context + ": " + message);
    }
}
