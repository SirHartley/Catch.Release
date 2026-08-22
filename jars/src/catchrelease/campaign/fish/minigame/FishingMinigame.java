package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.treasure.MinigameTreasure;
import catchrelease.campaign.fish.treasure.TreasureRoller;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;

public class FishingMinigame {

    public enum State {

        RUNNING,
        CAUGHT,
        ESCAPED
    }

    protected FishSpec fish;
    protected float difficulty;
    protected float motionSpeed;
    protected float restlessness;
    protected float progressRateMult;
    protected float escapeRateMult;
    protected FishMotion motion;

    protected float barPosition = 0.4f;
    protected float barHeight;
    protected float barVelocity = 0f;

    protected float fishPosition = 0.5f;
    protected float fishTarget = 0.5f;
    protected float fishThinkTimer = 0f;
    protected float fishVelocity = 0f;

    protected float progress = FishConstants.MINIGAME_PROGRESS_START;
    protected MinigameTreasure treasure;
    protected final java.util.List<MinigameTreasure> takenTreasures = new java.util.ArrayList<>();
    protected int treasuresLeft = 0;
    protected float treasureClock = 0f;
    protected Tackle tackle = Tackle.NONE;
    protected State state = State.RUNNING;
    protected boolean cannotLose = false;

    protected float timeHeld = 0f;
    protected float timeTotal = 0f;

    public FishingMinigame(FishSpec fish, Tackle tackle) {
        this.fish = fish;
        this.tackle = tackle == null ? Tackle.NONE : tackle;

        this.difficulty = fish.difficulty;
        this.motionSpeed = fish.motionSpeed;
        this.restlessness = fish.restlessness;
        this.progressRateMult = fish.progressRateMult;
        this.escapeRateMult = fish.escapeRateMult;
        this.motion = fish.motion;

        // clamped after the tackle has had its say, so a wide window is still a window
        this.barHeight = MathUtils.clamp(getBarHeight() * tackle.barSizeMult,
                FishConstants.MINIGAME_BAR_MIN_FRACTION, FishConstants.MINIGAME_BAR_MAX_FRACTION);
        this.fishTarget = pickFishTarget();
        this.cannotLose = Global.getSettings().isDevMode();

        rollTreasure();
    }

    protected void rollTreasure() {
        takenTreasures.clear();
        treasureClock = 0f;
        treasuresLeft = TreasureRoller.rollCount(
                tackle.treasureChanceMult * FishRumors.getLootMultForPlayer());

        treasure = spawnTreasure();
    }

    protected MinigameTreasure spawnTreasure() {
        if (treasuresLeft <= 0) return null;

        treasuresLeft--;
        return new MinigameTreasure(TreasureRoller.rollRarity());
    }

    public void restart() {
        barPosition = 0.4f;
        barVelocity = 0f;
        fishPosition = 0.5f;
        fishVelocity = 0f;
        fishThinkTimer = 0f;
        fishTarget = pickFishTarget();
        progress = FishConstants.MINIGAME_PROGRESS_START;

        rollTreasure();
        state = State.RUNNING;
        timeHeld = 0f;
        timeTotal = 0f;
    }

    public static float getBarHeight() {
        float pixels = UpgradeManager.getValue(
                StatIds.FISHING_BAR_SIZE, FishConstants.MINIGAME_BAR_SIZE_FALLBACK);

        return MathUtils.clamp(pixels / FishConstants.MINIGAME_TRACK_HEIGHT,
                FishConstants.MINIGAME_BAR_MIN_FRACTION, FishConstants.MINIGAME_BAR_MAX_FRACTION);
    }

    public void advance(float amount, boolean reeling) {
        if (state != State.RUNNING) return;

        timeTotal += amount;

        advanceBar(amount, reeling);
        advanceFish(amount);
        advanceTreasure(amount);
        advanceProgress(amount);
    }

    protected void advanceBar(float amount, boolean reeling) {
        barVelocity += (reeling
                ? FishConstants.MINIGAME_BAR_LIFT * tackle.barLiftMult
                : -FishConstants.MINIGAME_BAR_GRAVITY * tackle.barGravityMult) * amount;
        barVelocity = MathUtils.clamp(barVelocity, -FishConstants.MINIGAME_BAR_MAX_SPEED, FishConstants.MINIGAME_BAR_MAX_SPEED);

        barPosition += barVelocity * amount;

        bounce(0f, 1f - barHeight);
    }

    protected void bounce(float lowest, float highest) {
        if (barPosition < lowest) {
            barPosition = lowest;
            barVelocity = -barVelocity * FishConstants.MINIGAME_BAR_RESTITUTION;
        } else if (barPosition > highest) {
            barPosition = highest;
            barVelocity = -barVelocity * FishConstants.MINIGAME_BAR_RESTITUTION;
        } else {
            return;
        }

        if (Math.abs(barVelocity) < FishConstants.MINIGAME_BAR_REST_SPEED) barVelocity = 0f;
    }

    protected void advanceFish(float amount) {
        fishThinkTimer -= amount;

        if (fishThinkTimer <= 0f) {
            fishTarget = pickFishTarget();
            fishThinkTimer = pickThinkTime();
        }

        float maxSpeed = FishConstants.MINIGAME_FISH_BASE_SPEED * motionSpeed * getDifficultyMult();

        float desired = MathUtils.clamp((fishTarget - fishPosition) * FishConstants.MINIGAME_FISH_STIFFNESS,
                -maxSpeed, maxSpeed);

        float response = 1f - (float) Math.exp(-amount / FishConstants.MINIGAME_FISH_RESPONSE);
        fishVelocity += (desired - fishVelocity) * response;

        fishPosition += fishVelocity * amount;

        float markerSize = Math.max(FishConstants.MINIGAME_FISH_ICON_SIZE,
                FishConstants.MINIGAME_MOTE_HALO_SIZE);
        float margin = markerSize * 0.5f / FishConstants.MINIGAME_TRACK_HEIGHT;

        if (fishPosition < margin || fishPosition > 1f - margin) fishVelocity = 0f;
        fishPosition = MathUtils.clamp(fishPosition, margin, 1f - margin);
    }

    protected void advanceProgress(float amount) {
        if (isFishInBar()) {
            timeHeld += amount;
            progress += FishConstants.MINIGAME_CATCH_RATE * progressRateMult * amount
                    * UpgradeManager.getValue(StatIds.MINIGAME_PROGRESS_RATE, 1f)
                    * tackle.progressMult;
        } else {
            progress -= FishConstants.MINIGAME_ESCAPE_RATE * escapeRateMult * amount
                    * UpgradeManager.getValue(StatIds.MINIGAME_ESCAPE_RESIST, 1f)
                    * tackle.escapeMult;
        }

        if (progress >= 1f) {
            progress = 1f;
            state = State.CAUGHT;
            return;
        }

        // dev mode: floor instead of escaping, so the fish can be retuned indefinitely
        if (cannotLose) {
            progress = Math.max(progress, FishConstants.MINIGAME_DEV_PROGRESS_FLOOR);
            return;
        }

        if (progress <= 0f) {
            progress = 0f;
            state = State.ESCAPED;
        }
    }

    public boolean isFishInBar() {
        return covers(fishPosition);
    }

    public boolean covers(float position) {
        return position >= barPosition && position <= barPosition + barHeight;
    }

    protected void advanceTreasure(float amount) {
        if (treasure != null && treasure.isActive()) {
            treasure.advance(amount, covers(treasure.position));

            if (treasure.isTaken()) takenTreasures.add(treasure);
            if (!treasure.isActive()) treasureClock = FishConstants.TREASURE_SPAWN_INTERVAL;

            return;
        }

        if (treasuresLeft <= 0) return;

        treasureClock -= amount;
        if (treasureClock <= 0f) treasure = spawnTreasure();
    }

    public Tackle getTackle() {
        return tackle;
    }

    public MinigameTreasure getTreasure() {
        return treasure;
    }

    public java.util.List<MinigameTreasure> getTakenTreasures() {
        return takenTreasures;
    }

    protected float pickFishTarget() {
        FishMotion motion = this.motion == FishMotion.MIXED ? pickMixedMotion() : this.motion;

        switch (motion) {
            case DARTER:
                // bolts somewhere else entirely rather than drifting a little
                return MathUtils.getRandomNumberInRange(0f, 1f) < 0.5f
                        ? MathUtils.getRandomNumberInRange(0f, 0.25f)
                        : MathUtils.getRandomNumberInRange(0.75f, 1f);

            case SINKER:
                return MathUtils.getRandomNumberInRange(0f, 0.45f);

            case FLOATER:
                return MathUtils.getRandomNumberInRange(0.55f, 1f);

            default:
                return MathUtils.getRandomNumberInRange(0f, 1f);
        }
    }

    protected FishMotion pickMixedMotion() {
        FishMotion[] options = {FishMotion.SMOOTH, FishMotion.DARTER, FishMotion.SINKER, FishMotion.FLOATER};

        return options[(int) MathUtils.getRandomNumberInRange(0, options.length - 0.001f)];
    }

    protected float pickThinkTime() {
        float base = MathUtils.getRandomNumberInRange(
                FishConstants.MINIGAME_THINK_TIME_MIN, FishConstants.MINIGAME_THINK_TIME_MAX);

        float divisor = Math.max(0.1f, restlessness * getDifficultyMult());

        // a darter is defined by the wait before the bolt, so it gets to keep more of it
        if (motion == FishMotion.DARTER) base *= FishConstants.MINIGAME_DARTER_PATIENCE;

        return base / divisor;
    }

    protected float getDifficultyMult() {
        float scaled = FishConstants.MINIGAME_DIFFICULTY_FLOOR
                + FishConstants.MINIGAME_DIFFICULTY_SCALE * (difficulty / FishConstants.MINIGAME_DIFFICULTY_BASELINE);

        return Math.max(0.2f, scaled * FishConstants.MINIGAME_GLOBAL_DIFFICULTY);
    }

    public FishSpec getFish() {
        return fish;
    }

    public float getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(float value) {
        difficulty = MathUtils.clamp(value, FishConstants.MINIGAME_DIFFICULTY_MIN, FishConstants.MINIGAME_DIFFICULTY_MAX);
    }

    public float getMotionSpeed() {
        return motionSpeed;
    }

    public void setMotionSpeed(float value) {
        motionSpeed = MathUtils.clamp(value, FishConstants.MINIGAME_SPEED_MIN, FishConstants.MINIGAME_SPEED_MAX);
    }

    public boolean isCannotLose() {
        return cannotLose;
    }

    public void setCannotLose(boolean cannotLose) {
        this.cannotLose = cannotLose;
    }

    public FishMotion getMotion() {
        return motion;
    }

    public void setMotion(FishMotion value) {
        motion = value;
        fishThinkTimer = 0f;
    }

    public State getState() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isCaught() {
        return state == State.CAUGHT;
    }

    public void setEscaped(){
        progress = 0f;
        state = State.ESCAPED;
    }

    public void setCaught() {
        progress = 1f;
        state = State.CAUGHT;
    }

    public void devTakeTreasure() {
        takenTreasures.add(new MinigameTreasure(TreasureRoller.rollRarity()));
    }

    public void devSpawnTreasure() {
        treasuresLeft++;
        treasure = spawnTreasure();
    }

    public float getProgress() {
        return progress;
    }

    public float getBarPosition() {
        return barPosition;
    }

    public float getBarHeightFraction() {
        return barHeight;
    }

    public float getFishVelocity() {
        return fishVelocity;
    }

    public float getFishPosition() {
        return fishPosition;
    }

    public float getTimeHeld() {
        return timeHeld;
    }

    public float getTimeTotal() {
        return timeTotal;
    }
}
