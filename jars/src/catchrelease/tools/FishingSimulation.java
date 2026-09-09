package catchrelease.tools;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.tackle.Tackle;

final class FishingSimulation {

    public enum State {

        RUNNING, CAUGHT, ESCAPED
    }

    protected float difficulty;
    protected float motionSpeed;
    protected float restlessness;
    protected float progressRateMult;
    protected float escapeRateMult;
    protected FishMotion motion;
    protected FishMotion activeMotion;

    protected float barPosition = 0.4f;
    protected float barHeight;
    protected float barVelocity = 0f;

    protected float fishPosition = 0.5f;
    protected float fishTarget = 0.5f;
    protected float fishThinkTimer = 0f;
    protected float fishVelocity = 0f;

    protected float progress = FishConstants.MINIGAME_PROGRESS_START;
    protected State state = State.RUNNING;
    protected boolean cannotLose;
    protected float timeHeld;
    protected float timeTotal;

    private final RandomRange random;
    private final Tackle tackle;
    private float playerProgress = 1f;
    private float playerEscape = 1f;

    @FunctionalInterface
    public interface RandomRange {

        float between(float min, float max);
    }

    public FishingSimulation(float difficulty, float motionSpeed, float restlessness,
                             float progressRateMult, float escapeRateMult, FishMotion motion,
                             Tackle tackle, float barPixels, RandomRange random) {
        this.random = random;
        this.tackle = tackle == null ? Tackle.NONE : tackle;
        this.difficulty = difficulty;
        this.motionSpeed = motionSpeed;
        this.restlessness = restlessness;
        this.progressRateMult = progressRateMult;
        this.escapeRateMult = escapeRateMult;
        this.motion = motion;
        barHeight = barHeight(barPixels, this.tackle.barSizeMult);
        fishTarget = pickFishTarget();
    }

    public static float barHeight(float pixels, float tackleSize) {
        float base = clamp(pixels / FishConstants.MINIGAME_TRACK_HEIGHT,
                FishConstants.MINIGAME_BAR_MIN_FRACTION, FishConstants.MINIGAME_BAR_MAX_FRACTION);
        return clamp(base * tackleSize,
                FishConstants.MINIGAME_BAR_MIN_FRACTION, FishConstants.MINIGAME_BAR_MAX_FRACTION);
    }

    public void restart() {
        barPosition = 0.4f;
        barVelocity = 0f;
        fishPosition = 0.5f;
        fishVelocity = 0f;
        fishThinkTimer = 0f;
        fishTarget = pickFishTarget();
        progress = FishConstants.MINIGAME_PROGRESS_START;

        state = State.RUNNING;
        timeHeld = 0f;
        timeTotal = 0f;
    }

    public void advance(float amount, boolean reeling) {
        if (!isRunning()) return;
        advanceMotion(amount, reeling);
        advanceProgress(amount, isFishInBar() ? playerProgress : playerEscape);
    }

    void advanceMotion(float amount, boolean reeling) {
        timeTotal += amount;
        advanceBar(amount, reeling);
        advanceFish(amount);
    }

    protected void advanceBar(float amount, boolean reeling) {
        barVelocity += (reeling
                ? FishConstants.MINIGAME_BAR_LIFT * tackle.barLiftMult
                : -FishConstants.MINIGAME_BAR_GRAVITY * tackle.barGravityMult) * amount;
        barVelocity = clamp(barVelocity, -FishConstants.MINIGAME_BAR_MAX_SPEED, FishConstants.MINIGAME_BAR_MAX_SPEED);

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

        if (activeMotion == FishMotion.LUNGER) {
            maxSpeed *= Math.abs(fishTarget - fishPosition) > FishConstants.MINIGAME_LUNGER_NEAR
                    ? FishConstants.MINIGAME_LUNGER_DASH_MULT
                    : FishConstants.MINIGAME_LUNGER_CREEP_MULT;
        }

        float desired = clamp((fishTarget - fishPosition) * FishConstants.MINIGAME_FISH_STIFFNESS,
                -maxSpeed, maxSpeed);

        float response = 1f - (float) Math.exp(-amount / FishConstants.MINIGAME_FISH_RESPONSE);
        fishVelocity += (desired - fishVelocity) * response;

        fishPosition += fishVelocity * amount;

        float markerSize = Math.max(FishConstants.MINIGAME_FISH_ICON_SIZE,
                FishConstants.MINIGAME_MOTE_HALO_SIZE);
        float margin = markerSize * 0.5f / FishConstants.MINIGAME_TRACK_HEIGHT;

        if (fishPosition < margin || fishPosition > 1f - margin) fishVelocity = 0f;
        fishPosition = clamp(fishPosition, margin, 1f - margin);
    }

    void advanceProgress(float amount, float playerMult) {
        if (isFishInBar()) {
            timeHeld += amount;
            progress += FishConstants.MINIGAME_CATCH_RATE * compressRate(progressRateMult)
                    * amount
                    * playerMult
                    * tackle.progressMult;
        } else {
            progress -= FishConstants.MINIGAME_ESCAPE_RATE * compressRate(escapeRateMult)
                    * amount
                    * playerMult
                    * tackle.escapeMult;
        }

        if (progress >= 1f) {
            progress = 1f;
            state = State.CAUGHT;
            return;
        }

        // Practice protection applies before the escape check.
        if (cannotLose) {
            progress = Math.max(progress, FishConstants.MINIGAME_DEV_PROGRESS_FLOOR);
            return;
        }

        if (progress <= 0f) {
            progress = 0f;
            state = State.ESCAPED;
        }
    }

    protected float compressRate(float mult) {
        return 1f + (mult - 1f) * FishConstants.MINIGAME_RATE_COMPRESSION;
    }

    public boolean isFishInBar() {
        return covers(fishPosition);
    }

    public boolean covers(float position) {
        return position >= barPosition && position <= barPosition + barHeight;
    }

    protected float pickFishTarget() {
        activeMotion = motion == FishMotion.MIXED ? pickMixedMotion() : motion;
        if (activeMotion == null) activeMotion = FishMotion.SMOOTH;

        switch (activeMotion) {
            case DARTER:
                return random.between(0f, 1f) < 0.5f
                        ? random.between(0f, 0.25f)
                        : random.between(0.75f, 1f);

            case SINKER:
                return random.between(0f, 0.45f);

            case FLOATER:
                return random.between(0.55f, 1f);

            case WEAVER:
                // Only reverse after arrival; timer-only reversals collapse into centre jitter.
                return Math.abs(fishPosition - fishTarget) >= FishConstants.MINIGAME_WEAVER_ARRIVE
                        ? fishTarget
                        : fishPosition > 0.5f
                                ? FishConstants.MINIGAME_WEAVER_LOW
                                : FishConstants.MINIGAME_WEAVER_HIGH;

            case TWITCHER: {
                float hop = random.between(0f, 1f)
                        < FishConstants.MINIGAME_TWITCHER_LEAP_CHANCE
                        ? FishConstants.MINIGAME_TWITCHER_LEAP
                        : FishConstants.MINIGAME_TWITCHER_HOP;

                return clamp(fishPosition
                        + random.between(-hop, hop), 0.05f, 0.95f);
            }

            case LUNGER:
                return random.between(0f, 1f);

            default:
                return random.between(0f, 1f);
        }
    }

    protected FishMotion pickMixedMotion() {
        FishMotion[] options = {FishMotion.SMOOTH, FishMotion.DARTER, FishMotion.SINKER,
                FishMotion.FLOATER, FishMotion.WEAVER, FishMotion.TWITCHER, FishMotion.LUNGER};

        return options[(int) random.between(0, options.length - 0.001f)];
    }

    protected float pickThinkTime() {
        float base = random.between(
                FishConstants.MINIGAME_THINK_TIME_MIN, FishConstants.MINIGAME_THINK_TIME_MAX);

        float divisor = Math.max(0.1f, restlessness * getDifficultyMult());

        // MIXED uses the cadence of its current movement type.
        switch (activeMotion == null ? FishMotion.SMOOTH : activeMotion) {
            case DARTER:
                base *= FishConstants.MINIGAME_DARTER_PATIENCE;
                break;

            case TWITCHER:
                base *= FishConstants.MINIGAME_TWITCHER_CADENCE;
                break;

            case LUNGER:
                base *= FishConstants.MINIGAME_LUNGER_PATIENCE;
                break;

            case WEAVER:
                base = FishConstants.MINIGAME_THINK_TIME_MAX;
                break;

            default:
                break;
        }

        float think = base / divisor;

        // Preserve a tracking window at the end of a Weaver sweep.
        if (activeMotion == FishMotion.WEAVER) {
            think = Math.max(FishConstants.MINIGAME_WEAVER_DWELL_FLOOR, think);
        }

        return think;
    }

    protected float getDifficultyMult() {
        float scaled = FishConstants.MINIGAME_DIFFICULTY_FLOOR
                + FishConstants.MINIGAME_DIFFICULTY_SCALE
                * (float) Math.sqrt(difficulty / FishConstants.MINIGAME_DIFFICULTY_BASELINE);

        return Math.max(0.2f, scaled * FishConstants.MINIGAME_GLOBAL_DIFFICULTY);
    }

    public float getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(float value) {
        difficulty = clamp(value, FishConstants.MINIGAME_DIFFICULTY_MIN, FishConstants.MINIGAME_DIFFICULTY_MAX);
    }

    public float getMotionSpeed() {
        return motionSpeed;
    }

    public void setMotionSpeed(float value) {
        motionSpeed = clamp(value, FishConstants.MINIGAME_SPEED_MIN, FishConstants.MINIGAME_SPEED_MAX);
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

    public void tune(float difficulty, float speed, float restlessness, float gain, float loss, FishMotion motion) {
        this.difficulty = difficulty;
        motionSpeed = speed;
        this.restlessness = restlessness;
        progressRateMult = gain;
        escapeRateMult = loss;
        if (this.motion != motion) setMotion(motion);
    }

    public void setPlayerRates(float gain, float loss) {
        playerProgress = gain;
        playerEscape = loss;
    }

    public float getGainPerSecond() {
        return FishConstants.MINIGAME_CATCH_RATE * compressRate(progressRateMult) * playerProgress * tackle.progressMult;
    }

    public float getLossPerSecond() {
        return FishConstants.MINIGAME_ESCAPE_RATE * compressRate(escapeRateMult) * playerEscape * tackle.escapeMult;
    }

    public float getBarVelocity() {
        return barVelocity;
    }

    public FishMotion getActiveMotion() {
        return activeMotion;
    }

    public static float jitter(float time, float offset, float velocity, float jitter) {
        time = (time + offset) * FishConstants.MINIGAME_FISH_JITTER_SPEED;
        float wobble = (float) (Math.sin(time) * 0.5f
                + Math.sin(time * 1.73f) * 0.3f + Math.sin(time * 2.61f) * 0.2f);
        float effort = 1f + Math.abs(velocity) * FishConstants.MINIGAME_FISH_JITTER_EFFORT;
        return wobble * FishConstants.MINIGAME_FISH_JITTER * jitter * effort;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
