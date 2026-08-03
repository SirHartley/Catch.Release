package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.treasure.MinigameTreasure;
import catchrelease.campaign.fish.treasure.TreasureRoller;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.Global;
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

    /**
     * The fish's numbers, copied rather than read through.
     * <p>
     * Two reasons: the spec comes from a loader cache shared by every mote in the session, so writing
     * to it would quietly retune the whole species - and the dev controls need somewhere to write.
     */
    protected float difficulty;
    protected float motionSpeed;
    protected float restlessness;
    protected float progressRateMult;
    protected float escapeRateMult;
    protected FishMotion motion;

    /** Bar window, as a fraction of the track: where its bottom is, and how tall it is. */
    protected float barPosition = 0.4f;
    protected float barHeight;
    protected float barVelocity = 0f;

    /** Where the fish is, where it is headed, and how long until it thinks again. */
    protected float fishPosition = 0.5f;
    protected float fishTarget = 0.5f;
    protected float fishThinkTimer = 0f;
    protected float fishVelocity = 0f;

    protected float progress = FishConstants.MINIGAME_PROGRESS_START;

    /**
     * What else is down there, if anything. Rolled once when the catch starts: treasure that could
     * appear at any moment would be a thing to wait for rather than a thing to react to.
     */
    protected MinigameTreasure treasure;
    protected State state = State.RUNNING;

    /** Set from dev mode at the start - kept as a field so this stays testable without a game. */
    protected boolean cannotLose = false;

    /** Seconds the fish has been held, for the summary afterwards. */
    protected float timeHeld = 0f;
    protected float timeTotal = 0f;

    public FishingMinigame(FishSpec fish) {
        this.fish = fish;

        this.difficulty = fish.difficulty;
        this.motionSpeed = fish.motionSpeed;
        this.restlessness = fish.restlessness;
        this.progressRateMult = fish.progressRateMult;
        this.escapeRateMult = fish.escapeRateMult;
        this.motion = fish.motion;

        this.barHeight = getBarHeight();
        this.fishTarget = pickFishTarget();
        this.cannotLose = Global.getSettings().isDevMode();
    }

    /** Puts the fish back at the start with its current numbers - for the dev controls. */
    public void restart() {
        barPosition = 0.4f;
        barVelocity = 0f;
        fishPosition = 0.5f;
        fishVelocity = 0f;
        fishThinkTimer = 0f;
        fishTarget = pickFishTarget();
        progress = FishConstants.MINIGAME_PROGRESS_START;

        treasure = TreasureRoller.rollForTreasure()
                ? new MinigameTreasure(TreasureRoller.rollRarity())
                : null;
        state = State.RUNNING;
        timeHeld = 0f;
        timeTotal = 0f;
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
        advanceTreasure(amount);
        advanceProgress(amount);
    }

    /** Hold to lift, let go to fall. The bar keeps its momentum, so it is aimed rather than driven. */
    protected void advanceBar(float amount, boolean reeling) {
        barVelocity += (reeling ? FishConstants.MINIGAME_BAR_LIFT : -FishConstants.MINIGAME_BAR_GRAVITY) * amount;
        barVelocity = MathUtils.clamp(barVelocity, -FishConstants.MINIGAME_BAR_MAX_SPEED, FishConstants.MINIGAME_BAR_MAX_SPEED);

        barPosition += barVelocity * amount;

        bounce(0f, 1f - barHeight);
    }

    /**
     * Elastic ends. The bar keeps a share of its speed back the other way on contact, so dropping it
     * on the floor gives a run of smaller and smaller bounces before it settles rather than one dead
     * stop - and below a crawl it is put to rest, which is what stops it juddering there forever.
     */
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

        float maxSpeed = FishConstants.MINIGAME_FISH_BASE_SPEED * motionSpeed * getDifficultyMult();

        //the speed it would like: proportional to how far it still has to go, so it eases off as it
        //arrives instead of stopping dead on the spot
        float desired = MathUtils.clamp((fishTarget - fishPosition) * FishConstants.MINIGAME_FISH_STIFFNESS,
                -maxSpeed, maxSpeed);

        //and it takes a moment to get to that speed, which is what rounds off the turns
        float response = 1f - (float) Math.exp(-amount / FishConstants.MINIGAME_FISH_RESPONSE);
        fishVelocity += (desired - fishVelocity) * response;

        fishPosition += fishVelocity * amount;

        if (fishPosition < 0f || fishPosition > 1f) fishVelocity = 0f;
        fishPosition = MathUtils.clamp(fishPosition, 0f, 1f);
    }

    /** Fills while the fish is inside the bar, drains while it is not. */
    protected void advanceProgress(float amount) {
        if (isFishInBar()) {
            timeHeld += amount;
            progress += FishConstants.MINIGAME_CATCH_RATE * progressRateMult * amount;
        } else {
            progress -= FishConstants.MINIGAME_ESCAPE_RATE * escapeRateMult * amount;
        }

        if (progress >= 1f) {
            progress = 1f;
            state = State.CAUGHT;
            return;
        }

        //in dev mode the meter bottoms out instead of ending it, so a fish can be sat with and
        //retuned for as long as it takes rather than escaping the moment it gets away from you
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

    /** Whether the window is over a point in the track. The fish and the treasure both ask this. */
    public boolean covers(float position) {
        return position >= barPosition && position <= barPosition + barHeight;
    }

    /**
     * The treasure's own clock. It neither moves nor affects the fish - taking it costs the ground
     * the bar gives up going to get it, and that is the whole of the trade.
     */
    protected void advanceTreasure(float amount) {
        if (treasure == null || !treasure.isActive()) return;

        treasure.advance(amount, covers(treasure.position));
    }

    /** Null when there is nothing down there, which is most catches. */
    public MinigameTreasure getTreasure() {
        return treasure;
    }

    /** Where this fish would like to be next, according to its archetype. */
    protected float pickFishTarget() {
        FishMotion motion = this.motion == FishMotion.MIXED ? pickMixedMotion() : this.motion;

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

        float divisor = Math.max(0.1f, restlessness * getDifficultyMult());

        //a darter is defined by the wait before the bolt, so it gets to keep more of it
        if (motion == FishMotion.DARTER) base *= FishConstants.MINIGAME_DARTER_PATIENCE;

        return base / divisor;
    }

    /** Difficulty as a multiplier around 1 - see the curve's note in {@link FishConstants}. */
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

    /** Dev controls. Clamped, so nothing can be tuned into a state that cannot be played. */
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
