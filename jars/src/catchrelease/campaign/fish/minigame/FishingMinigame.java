package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import org.lazywizard.lazylib.MathUtils;

/**
 * The rules of the catch, with no screen anywhere in them.
 * <p>
 * Everything lives in a 0..1 track: the bar is a window somewhere in it, the fish is a point in it,
 * and progress fills while the window is over the point and drains while it is not. Hold to lift the
 * bar, let go and it falls. The fish's {@link FishSpec} decides how it moves and how fast the meter
 * swings, and the bar's size is the player's {@link StatIds#FISHING_BAR_SIZE} upgrade.
 * <p>
 * Kept free of rendering and input on purpose - it advances on a number of seconds and a boolean, so
 * it can be reasoned about, and tuned, without a game running.
 */
public class FishingMinigame {

    public enum State {
        RUNNING,
        CAUGHT,
        ESCAPED
    }

    protected FishSpec fish;

    /** Bar window, as a fraction of the track: where its bottom is, and how tall it is. */
    protected float barPosition = 0.4f;
    protected float barHeight;
    protected float barVelocity = 0f;

    /** Where the fish is, where it is headed, and how long until it thinks again. */
    protected float fishPosition = 0.5f;
    protected float fishTarget = 0.5f;
    protected float fishThinkTimer = 0f;

    protected float progress = FishConstants.MINIGAME_PROGRESS_START;
    protected State state = State.RUNNING;

    /** Seconds the fish has been held, for the summary afterwards. */
    protected float timeHeld = 0f;
    protected float timeTotal = 0f;

    public FishingMinigame(FishSpec fish) {
        this.fish = fish;
        this.barHeight = getBarHeight();
        this.fishTarget = pickFishTarget();
    }

    /** The bar's share of the track, from the upgrade, clamped so it is always playable. */
    public static float getBarHeight() {
        UpgradeManager upgrades = UpgradeManager.getInstance();

        float pixels = upgrades.hasStat(StatIds.FISHING_BAR_SIZE)
                ? upgrades.getCurrentValue(StatIds.FISHING_BAR_SIZE)
                : FishConstants.MINIGAME_BAR_SIZE_FALLBACK;

        return MathUtils.clamp(pixels / FishConstants.MINIGAME_TRACK_HEIGHT,
                FishConstants.MINIGAME_BAR_MIN_FRACTION, FishConstants.MINIGAME_BAR_MAX_FRACTION);
    }

    /**
     * @param reeling whether the player is holding the button down this frame
     */
    public void advance(float amount, boolean reeling) {
        if (state != State.RUNNING) return;

        timeTotal += amount;

        advanceBar(amount, reeling);
        advanceFish(amount);
        advanceProgress(amount);
    }

    /** Hold to lift, let go to fall. The bar keeps its momentum, so it is aimed rather than driven. */
    protected void advanceBar(float amount, boolean reeling) {
        barVelocity += (reeling ? FishConstants.MINIGAME_BAR_LIFT : -FishConstants.MINIGAME_BAR_GRAVITY) * amount;
        barVelocity = MathUtils.clamp(barVelocity, -FishConstants.MINIGAME_BAR_MAX_SPEED, FishConstants.MINIGAME_BAR_MAX_SPEED);

        barPosition += barVelocity * amount;

        //the ends of the track are walls, not bounces - hitting one kills the momentum into it
        if (barPosition < 0f) {
            barPosition = 0f;
            barVelocity = 0f;
        }

        float highest = 1f - barHeight;
        if (barPosition > highest) {
            barPosition = highest;
            barVelocity = 0f;
        }
    }

    /**
     * The fish swims towards whatever it last decided on, and decides again when its timer runs out.
     * Which spot it decides on is the archetype's business; how often, and how fast it gets there, is
     * the spec's.
     */
    protected void advanceFish(float amount) {
        fishThinkTimer -= amount;

        if (fishThinkTimer <= 0f) {
            fishTarget = pickFishTarget();
            fishThinkTimer = pickThinkTime();
        }

        float speed = FishConstants.MINIGAME_FISH_BASE_SPEED * fish.motionSpeed * getDifficultyMult();
        float step = speed * amount;
        float remaining = fishTarget - fishPosition;

        fishPosition += Math.abs(remaining) <= step ? remaining : Math.signum(remaining) * step;
        fishPosition = MathUtils.clamp(fishPosition, 0f, 1f);
    }

    /** Fills while the fish is inside the bar, drains while it is not. */
    protected void advanceProgress(float amount) {
        if (isFishInBar()) {
            timeHeld += amount;
            progress += FishConstants.MINIGAME_CATCH_RATE * fish.progressRateMult * amount;
        } else {
            progress -= FishConstants.MINIGAME_ESCAPE_RATE * fish.escapeRateMult * amount;
        }

        if (progress >= 1f) {
            progress = 1f;
            state = State.CAUGHT;
            return;
        }

        if (progress <= 0f) {
            progress = 0f;
            state = State.ESCAPED;
        }
    }

    public boolean isFishInBar() {
        return fishPosition >= barPosition && fishPosition <= barPosition + barHeight;
    }

    /** Where this fish would like to be next, according to its archetype. */
    protected float pickFishTarget() {
        FishMotion motion = fish.motion == FishMotion.MIXED ? pickMixedMotion() : fish.motion;

        switch (motion) {
            case DARTER:
                //bolts somewhere else entirely rather than drifting a little
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

    /** How long before it changes its mind - restless fish think more often, and so do hard ones. */
    protected float pickThinkTime() {
        float base = MathUtils.getRandomNumberInRange(
                FishConstants.MINIGAME_THINK_TIME_MIN, FishConstants.MINIGAME_THINK_TIME_MAX);

        float divisor = Math.max(0.1f, fish.restlessness * getDifficultyMult());

        //a darter is defined by the wait before the bolt, so it gets to keep more of it
        if (fish.motion == FishMotion.DARTER) base *= FishConstants.MINIGAME_DARTER_PATIENCE;

        return base / divisor;
    }

    /** Difficulty as a multiplier around 1 - see the curve's note in {@link FishConstants}. */
    protected float getDifficultyMult() {
        return Math.max(0.2f, FishConstants.MINIGAME_DIFFICULTY_FLOOR
                + FishConstants.MINIGAME_DIFFICULTY_SCALE * (fish.difficulty / FishConstants.MINIGAME_DIFFICULTY_BASELINE));
    }

    public FishSpec getFish() {
        return fish;
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

    public float getProgress() {
        return progress;
    }

    public float getBarPosition() {
        return barPosition;
    }

    public float getBarHeightFraction() {
        return barHeight;
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
