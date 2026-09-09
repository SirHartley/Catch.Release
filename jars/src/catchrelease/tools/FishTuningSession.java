package catchrelease.tools;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.minigame.FishingSimulation;
import catchrelease.campaign.fish.tackle.Tackle;

import java.util.Random;
import java.util.function.Consumer;

import static catchrelease.tools.FishTuningSheet.Field.*;

final class FishTuningSession {

    static final float STEP = 1f / 60f;
    static final float RESULT_HOLD = 0.8f;

    final FishTuningSheet.Row fish;
    final Tackle tackle;
    final SimulatedAngler.Skill skill;
    final float barPixels;
    final float playerGain;
    final float playerLoss;
    final float rumorSpeed;
    final Consumer<String> onResult;
    FishingSimulation game;
    SimulatedAngler angler;
    long seed;
    int attempt;
    int caught;
    int lost;
    float catchSeconds;
    float endWait;
    boolean noLoss;
    boolean unscored;
    boolean held;
    String lastResult = "No completed attempts";

    FishTuningSession(FishTuningSheet.Row fish, Tackle tackle, SimulatedAngler.Skill skill,
                      float barPixels, float playerGain, float playerLoss, float rumorSpeed,
                      boolean noLoss, long seed, Consumer<String> onResult) {
        this.fish = fish;
        this.tackle = tackle;
        this.skill = skill;
        this.barPixels = barPixels;
        this.playerGain = playerGain;
        this.playerLoss = playerLoss;
        this.rumorSpeed = rumorSpeed;
        this.noLoss = noLoss;
        this.seed = seed;
        this.onResult = onResult;
        restart(false);
    }

    void restart(boolean freshSeed) {
        if (freshSeed) seed++;
        Random random = new Random(seed);
        game = new FishingSimulation(fish.value(DIFFICULTY), speed(), fish.value(RESTLESSNESS),
                fish.value(GAIN), fish.value(LOSS), fish.motion, tackle, barPixels,
                (min, max) -> min + random.nextFloat() * (max - min));
        game.setPlayerRates(playerGain, playerLoss);
        game.setCannotLose(noLoss);
        angler = new SimulatedAngler(skill, seed ^ 0x4f1bbcdcL);
        attempt++;
        endWait = 0f;
        held = false;
        unscored = noLoss;
    }

    void tune() {
        game.tune(fish.value(DIFFICULTY), speed(), fish.value(RESTLESSNESS),
                fish.value(GAIN), fish.value(LOSS), fish.motion);
        unscored = true;
        caught = lost = 0;
        catchSeconds = 0f;
    }

    void disableLosing(boolean value) {
        noLoss = value;
        game.setCannotLose(value);
        unscored |= value;
    }

    void advance(float amount, boolean manualHeld) {
        if (!game.isRunning()) {
            endWait -= amount;
            if (endWait <= 0f) restart(true);
            return;
        }
        held = skill == SimulatedAngler.Skill.MANUAL ? manualHeld : angler.input(game.getTimeTotal(),
                visibleFish(), game.getBarPosition(), game.getBarHeightFraction(),
                FishConstants.MINIGAME_BAR_LIFT * tackle.barLiftMult,
                FishConstants.MINIGAME_BAR_GRAVITY * tackle.barGravityMult);
        game.advance(amount, held);
        if (game.isRunning()) return;
        boolean success = game.isCaught();
        if (!unscored) {
            if (success) {
                caught++;
                catchSeconds += game.getTimeTotal();
            } else lost++;
        }
        lastResult = (success ? "Caught" : "Lost") + String.format(java.util.Locale.ROOT,
                " — %.2fs — %s — seed %d%s", game.getTimeTotal(), skill, seed,
                unscored ? " (practice/edited, not scored)" : "");
        endWait = RESULT_HOLD;
        onResult.accept(fish.id + " — " + lastResult + String.format(java.util.Locale.ROOT,
                " | %s d=%.2f speed=%.2f restless=%.2f gain=%.2f loss=%.2f shake=%.2f"
                        + " | %s bar=%.0f player=%.2f/%.2f rumor=%.2f",
                fish.motion, fish.value(DIFFICULTY), fish.value(SPEED), fish.value(RESTLESSNESS),
                fish.value(GAIN), fish.value(LOSS), fish.value(JITTER), tackle, barPixels, playerGain, playerLoss, rumorSpeed));
    }

    float visibleFish() {
        return game.getFishPosition() + FishingSimulation.jitter(game.getTimeTotal(), 1.7f,
                game.getFishVelocity(), fish.value(JITTER)) / FishConstants.MINIGAME_TRACK_HEIGHT;
    }

    private float speed() {
        return fish.value(SPEED) * (fish.rarity.equals("LEGENDARY") ? 1f : rumorSpeed);
    }
}
