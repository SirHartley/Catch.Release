package catchrelease.tools;

import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.minigame.FishingMinigame;
import catchrelease.campaign.fish.minigame.MantaMinigame;
import catchrelease.campaign.fish.tackle.Tackle;
import com.fs.starfarer.api.campaign.LocationAPI;
import org.lazywizard.lazylib.MathUtils;

import java.util.HashSet;
import java.util.Set;

public final class MantaMinigameChecks {

    private static int checks;

    static class Game extends MantaMinigame {

        Game(FishSpec fish, LocationAPI location) { super(fish, Tackle.NONE, location); }
        void cover(float at) { barHeight = 0.06f; barPosition = at - 0.03f; }
        void score() { advanceProgress(0.1f); }
    }

    static class Reference extends FishingMinigame {

        Reference(FishSpec fish, LocationAPI location) { super(fish, Tackle.NONE, location); }
        @Override protected void rollTreasure() { }
        void keepRunning() { state = State.RUNNING; progress = 0.5f; }
    }

    public static void main(String[] args) {
        try (FishingParityChecks.Environment env = new FishingParityChecks.Environment()) {
            FishSpec fish = new FishSpec();
            fish.id = "abyssal_ghost_manta";
            fish.rarity = FishRarity.LEGENDARY;
            fish.specialChance = 0.7f;
            fish.mixChance = 0.4f;
            fish.motionSpeed = 1.7f;
            fish.restlessness = 1.5f;
            for (FishMotion motion : FishMotion.values()) {
                fish.motion = motion;
                for (int fps : new int[]{30, 60, 144}) {
                    Game game = new Game(fish, env.system);
                    check(game.getDecoys().size() == 2, "two decoys");
                    Set<Float> starts = new HashSet<>();
                    starts.add(game.getFishPosition());
                    for (FishingMinigame decoy : game.getDecoys()) starts.add(decoy.getFishPosition());
                    check(starts.size() == 3, "distinct opening slots");
                    game.cover(game.getDecoys().get(0).getFishPosition());
                    check(!game.isFishInBar(), "a decoy cannot turn the bar green");
                    float progress = game.getProgress();
                    game.score();
                    check(game.getProgress() < progress, "covering only a decoy loses progress");
                    game.cover(game.getFishPosition());
                    check(game.isFishInBar(), "real fish turns bar green");
                    float beforeGain = game.getProgress();
                    game.score();
                    check(game.getProgress() > beforeGain, "real fish gains progress");
                    FishingMinigame decoy = game.getDecoys().get(0);
                    Reference reference = new Reference(fish, env.system);
                    // Weaver opening targets depend on the previous target; prime both identically.
                    decoy.setMotion(FishMotion.SMOOTH);
                    reference.setMotion(FishMotion.SMOOTH);
                    MathUtils.getRandom().setSeed(901);
                    decoy.restart();
                    MathUtils.getRandom().setSeed(901);
                    reference.restart();
                    decoy.setMotion(motion);
                    reference.setMotion(motion);
                    for (int frame = 0; frame < fps * 20; frame++) {
                        MathUtils.getRandom().setSeed(frame + 71);
                        decoy.advance(1f / fps, true);
                        MathUtils.getRandom().setSeed(frame + 71);
                        reference.keepRunning();
                        reference.advance(1f / fps, true);
                        check(decoy.getFishPosition() == reference.getFishPosition(), "motion position parity");
                        check(decoy.getFishVelocity() == reference.getFishVelocity(), "motion velocity parity");
                        check(decoy.getTellProgress() == reference.getTellProgress(), "tell timing parity");
                        check(decoy.getTellDirection() == reference.getTellDirection(), "tell direction parity");
                    }
                    check(decoy.getTreasures().isEmpty() && decoy.getTimeHeld() == 0f,
                            "decoys cannot grant treasure or collect coverage");
                    float time = decoy.getTimeTotal();
                    game.setCaught();
                    game.advance(3f, true);
                    check(decoy.getTimeTotal() == time, "ending freezes decoys");
                    game.restart();
                    check(game.isRunning() && game.getTimeTotal() == 0f, "real restarts");
                    for (FishingMinigame other : game.getDecoys()) {
                        check(other.getTimeTotal() == 0f, "decoy restarts");
                    }
                }
            }
        }
        System.out.println("Manta minigame checks passed: " + checks);
    }

    private static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
}
