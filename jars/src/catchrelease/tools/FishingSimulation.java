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
    protected float specialChance;
    protected float mixChance;
    protected FishMotion motion;
    protected FishMotion activeMotion;
    protected float legSpeedMult = 1f;
    protected float legThinkMult = 1f;

    protected Move pendingMove;
    protected float tellLeft;
    protected float tellDirection;

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

    protected record Move(float target, FishMotion motion, float speedMult, float thinkMult, boolean telegraphed) {

    }

    public FishingSimulation(float difficulty, float motionSpeed, float restlessness,
                             float progressRateMult, float escapeRateMult, float specialChance, float mixChance,
                             FishMotion motion, Tackle tackle, float barPixels, RandomRange random) {
        this.random = random;
        this.tackle = tackle == null ? Tackle.NONE : tackle;
        this.difficulty = difficulty;
        this.motionSpeed = motionSpeed;
        this.restlessness = restlessness;
        this.progressRateMult = progressRateMult;
        this.escapeRateMult = escapeRateMult;
        this.specialChance = specialChance;
        this.mixChance = mixChance;
        this.motion = motion;
        barHeight = barHeight(barPixels, this.tackle.barSizeMult);
        applyMove(chooseMove());
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
        applyMove(chooseMove());
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
        if (pendingMove != null) {
            tellLeft -= amount;
            if (tellLeft <= 0f) startMove(pendingMove);
        } else {
            fishThinkTimer -= amount;

            if (fishThinkTimer <= 0f && !isBounding()) {
                Move move = chooseMove();

                if (move.telegraphed()) {
                    pendingMove = move;
                    tellLeft = FishConstants.MINIGAME_TELL_TIME;
                    tellDirection = Math.signum(move.target() - fishPosition);
                } else {
                    startMove(move);
                }
            }
        }

        float maxSpeed = FishConstants.MINIGAME_FISH_BASE_SPEED * motionSpeed * getDifficultyMult() * legSpeedMult;

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

    protected Move chooseMove() {
        FishMotion own = motion == null ? FishMotion.SMOOTH : motion;
        if (own == FishMotion.MIXED) return withTell(borrowedMove());

        // Mirrors the game: no roll without a share.
        float roll = mixChance + specialChance > 0f ? random.between(0f, 1f) : 1f;
        if (roll < mixChance) return withTell(borrowedMove());
        if (roll < mixChance + specialChance) return withTell(pickSpecialMove(own));

        return withTell(new Move(pickTarget(own), own, 1f, 1f, false));
    }

    protected void startMove(Move move) {
        applyMove(move);
        fishThinkTimer = pickThinkTime();
    }

    protected void applyMove(Move move) {
        fishTarget = move.target();
        activeMotion = move.motion();
        legSpeedMult = move.speedMult();
        legThinkMult = move.thinkMult();
        pendingMove = null;
        tellLeft = 0f;
        tellDirection = 0f;
    }

    protected boolean isBounding() {
        return activeMotion == FishMotion.TWITCHER && legSpeedMult > 1f
                && Math.abs(fishTarget - fishPosition) >= FishConstants.MINIGAME_WEAVER_ARRIVE;
    }

    protected Move borrowedMove() {
        FishMotion type = pickMixedMotion();

        return new Move(pickTarget(type), type, 1f, 1f, false);
    }

    protected Move withTell(Move move) {
        float speed = FishConstants.MINIGAME_FISH_BASE_SPEED * motionSpeed * getDifficultyMult() * move.speedMult()
                * (move.motion() == FishMotion.LUNGER ? FishConstants.MINIGAME_LUNGER_DASH_MULT : 1f);
        boolean huge = Math.abs(move.target() - fishPosition) >= FishConstants.MINIGAME_TELL_DISTANCE
                && Math.abs(move.target() - fishTarget) >= FishConstants.MINIGAME_TELL_DISTANCE;

        return new Move(move.target(), move.motion(), move.speedMult(), move.thinkMult(),
                huge && speed >= FishConstants.MINIGAME_TELL_SPEED);
    }

    protected Move pickSpecialMove(FishMotion own) {
        switch (own) {
            case DARTER:
                return new Move(fishPosition > 0.5f
                        ? random.between(0f, 0.25f)
                        : random.between(0.75f, 1f), own, 1f, FishConstants.MINIGAME_QUICK_THINK, false);

            case SINKER:
                return new Move(random.between(FishConstants.MINIGAME_SURGE_MIN,
                        FishConstants.MINIGAME_SURGE_MAX), own, 1f, FishConstants.MINIGAME_SURGE_THINK, false);

            case FLOATER:
                return new Move(random.between(1f - FishConstants.MINIGAME_SURGE_MAX,
                        1f - FishConstants.MINIGAME_SURGE_MIN), own, 1f, FishConstants.MINIGAME_SURGE_THINK, false);

            case WEAVER: {
                float target = Math.abs(fishPosition - fishTarget) >= FishConstants.MINIGAME_WEAVER_ARRIVE
                        ? fishTarget > 0.5f ? FishConstants.MINIGAME_WEAVER_LOW : FishConstants.MINIGAME_WEAVER_HIGH
                        : random.between(FishConstants.MINIGAME_WEAVER_SHORT_MIN,
                                FishConstants.MINIGAME_WEAVER_SHORT_MAX);

                return new Move(target, own, 1f, 1f, false);
            }

            case TWITCHER:
                return new Move(fishPosition > 0.5f
                        ? random.between(0.05f, 0.05f + FishConstants.MINIGAME_TWITCHER_BOUND_REACH)
                        : random.between(0.95f - FishConstants.MINIGAME_TWITCHER_BOUND_REACH, 0.95f),
                        own, FishConstants.MINIGAME_TWITCHER_BOUND_SPEED, 1f, false);

            case LUNGER:
                return new Move(random.between(0f, 1f), own, 1f, FishConstants.MINIGAME_QUICK_THINK, false);

            default:
                return new Move(fishPosition > 0.5f
                        ? random.between(0f, FishConstants.MINIGAME_SMOOTH_BURST_REACH)
                        : random.between(1f - FishConstants.MINIGAME_SMOOTH_BURST_REACH, 1f),
                        own, FishConstants.MINIGAME_SMOOTH_BURST_SPEED, 1f, false);
        }
    }

    protected float pickTarget(FishMotion type) {
        switch (type) {
            case DARTER:
                return random.between(0f, 1f) < 0.5f
                        ? random.between(0f, 0.25f)
                        : random.between(0.75f, 1f);

            case SINKER:
                return random.between(0f, FishConstants.MINIGAME_SINKER_CEILING);

            case FLOATER:
                return random.between(FishConstants.MINIGAME_FLOATER_FLOOR, 1f);

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

        return think * legThinkMult;
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
        pendingMove = null;
        tellLeft = 0f;
        tellDirection = 0f;
    }

    public float getTellProgress() {
        return pendingMove == null || state != State.RUNNING ? 0f : 1f - tellLeft / FishConstants.MINIGAME_TELL_TIME;
    }

    public float getTellDirection() {
        return pendingMove == null || state != State.RUNNING ? 0f : tellDirection;
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

    public void tune(float difficulty, float speed, float restlessness, float gain, float loss,
                     float special, float mix, FishMotion motion) {
        this.difficulty = difficulty;
        motionSpeed = speed;
        this.restlessness = restlessness;
        progressRateMult = gain;
        escapeRateMult = loss;
        specialChance = special;
        mixChance = mix;
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

    public static float jitter(float time, float offset, float velocity, float jitter, float tellProgress) {
        time = (time + offset) * FishConstants.MINIGAME_FISH_JITTER_SPEED;
        float wobble = (float) (Math.sin(time) * 0.5f
                + Math.sin(time * 1.73f) * 0.3f + Math.sin(time * 2.61f) * 0.2f);
        float effort = 1f + Math.abs(velocity) * FishConstants.MINIGAME_FISH_JITTER_EFFORT;
        float swell = (float) Math.sin(tellProgress * Math.PI);
        float calm = 1f - FishConstants.MINIGAME_TELL_CALM * swell * swell;
        return wobble * FishConstants.MINIGAME_FISH_JITTER * jitter * effort * calm;
    }

    // Same body as FishingMinigamePanel.getTell; FishingParityChecks compares the text.
    public static float tell(float progress, float direction, float jitter) {
        float swell = (float) Math.sin(progress * Math.PI);
        float envelope = swell * swell;
        float pulse = 0.5f - 0.5f * (float) Math.cos(progress * Math.PI * 2f * FishConstants.MINIGAME_TELL_PULSES);
        float reach = FishConstants.MINIGAME_TELL_LEAN + FishConstants.MINIGAME_TELL_PULSE * pulse
                + FishConstants.MINIGAME_TELL_JITTER_SHARE * FishConstants.MINIGAME_FISH_JITTER * jitter;
        return direction * envelope * reach;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
