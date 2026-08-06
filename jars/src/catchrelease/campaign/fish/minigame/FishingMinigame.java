package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.treasure.MinigameTreasure;
import catchrelease.campaign.fish.treasure.TreasureRoller;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;

/**
 * The rules of the catch, with no rendering or input in them - advances on a delta-time float and
 * a boolean so it can be reasoned about and tuned without a game running.
 * <p>
 * Everything lives in a 0..1 track: the bar is a window in it, the fish a point in it, and progress
 * fills while the window covers the point and drains otherwise. Hold to lift the bar, release to
 * fall. {@link FishSpec} sets fish movement and meter swing; bar size comes from the player's
 * {@link StatIds#FISHING_BAR_SIZE} upgrade.
 */
public class FishingMinigame {

    public enum State {
        RUNNING,
        CAUGHT,
        ESCAPED
    }

    protected FishSpec fish;

    /**
     * Copied from {@link FishSpec} rather than read live - the spec is a shared loader-cache
     * instance, so writing to it would retune every mote; dev controls need a place to write too.
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
     * Treasure count rolled once at catch start (up to {@link FishConstants#TREASURE_MAX_PER_CATCH},
     * usually zero); pieces surface one at a time, first immediately, each next one on a clock after
     * the previous resolves - see {@link #advanceTreasure}.
     */
    protected MinigameTreasure treasure;
    protected final java.util.List<MinigameTreasure> takenTreasures = new java.util.ArrayList<>();
    protected int treasuresLeft = 0;
    protected float treasureClock = 0f;

    /** What is fitted. Read once when the catch starts; changing gear mid-catch is not a thing. */
    protected Tackle tackle = Tackle.NONE;
    protected State state = State.RUNNING;

    /** Set from dev mode at the start - kept as a field so this stays testable without a game. */
    protected boolean cannotLose = false;

    /** Seconds the fish has been held, for the summary afterwards. */
    protected float timeHeld = 0f;
    protected float timeTotal = 0f;

    /**
     * Tackle must be passed here, not set afterward - bar size and the treasure roll are both
     * computed in this constructor and would already be locked in against an empty rig otherwise.
     */
    public FishingMinigame(FishSpec fish, Tackle tackle) {
        this.fish = fish;
        this.tackle = tackle == null ? Tackle.NONE : tackle;

        this.difficulty = fish.difficulty;
        this.motionSpeed = fish.motionSpeed;
        this.restlessness = fish.restlessness;
        this.progressRateMult = fish.progressRateMult;
        this.escapeRateMult = fish.escapeRateMult;
        this.motion = fish.motion;

        //clamped after the tackle has had its say, so a wide window is still a window
        this.barHeight = MathUtils.clamp(getBarHeight() * tackle.barSizeMult,
                FishConstants.MINIGAME_BAR_MIN_FRACTION, FishConstants.MINIGAME_BAR_MAX_FRACTION);
        this.fishTarget = pickFishTarget();
        this.cannotLose = Global.getSettings().isDevMode();

        rollTreasure();
    }

    /** Rolls this catch's treasure count; the first piece, if any, spawns immediately. */
    protected void rollTreasure() {
        takenTreasures.clear();
        treasureClock = 0f;
        treasuresLeft = TreasureRoller.rollCount(tackle.treasureChanceMult);

        treasure = spawnTreasure();
    }

    /** The next piece, if any are owed. Decrements the debt; null once it is paid. */
    protected MinigameTreasure spawnTreasure() {
        if (treasuresLeft <= 0) return null;

        treasuresLeft--;
        return new MinigameTreasure(TreasureRoller.rollRarity());
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

        rollTreasure();
        state = State.RUNNING;
        timeHeld = 0f;
        timeTotal = 0f;
    }

    /** The bar's share of the track, from the upgrade, clamped so it is always playable. */
    public static float getBarHeight() {
        float pixels = UpgradeManager.getValue(
                StatIds.FISHING_BAR_SIZE, FishConstants.MINIGAME_BAR_SIZE_FALLBACK);

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
        barVelocity += (reeling
                ? FishConstants.MINIGAME_BAR_LIFT * tackle.barLiftMult
                : -FishConstants.MINIGAME_BAR_GRAVITY * tackle.barGravityMult) * amount;
        barVelocity = MathUtils.clamp(barVelocity, -FishConstants.MINIGAME_BAR_MAX_SPEED, FishConstants.MINIGAME_BAR_MAX_SPEED);

        barPosition += barVelocity * amount;

        bounce(0f, 1f - barHeight);
    }

    /**
     * Elastic bounce off track ends (restitution &lt; 1, so bounces shrink each time); velocity below
     * {@link FishConstants#MINIGAME_BAR_REST_SPEED} is zeroed so it doesn't judder forever.
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
     * Eases toward the current target, repicking a new one when {@link #fishThinkTimer} runs out.
     * Target choice is per-archetype ({@link #pickFishTarget}); speed and frequency come from the spec.
     */
    protected void advanceFish(float amount) {
        fishThinkTimer -= amount;

        if (fishThinkTimer <= 0f) {
            fishTarget = pickFishTarget();
            fishThinkTimer = pickThinkTime();
        }

        float maxSpeed = FishConstants.MINIGAME_FISH_BASE_SPEED * motionSpeed * getDifficultyMult();

        //desired speed proportional to remaining distance, so it eases off on arrival
        float desired = MathUtils.clamp((fishTarget - fishPosition) * FishConstants.MINIGAME_FISH_STIFFNESS,
                -maxSpeed, maxSpeed);

        //and it takes a moment to get to that speed, which is what rounds off the turns
        float response = 1f - (float) Math.exp(-amount / FishConstants.MINIGAME_FISH_RESPONSE);
        fishVelocity += (desired - fishVelocity) * response;

        fishPosition += fishVelocity * amount;

        //margin = half icon size (in track fractions) so the centred icon never hangs off the track
        //edge; narrowest bar is still deeper than the margin, so the fish stays catchable there
        float margin = FishConstants.MINIGAME_FISH_ICON_SIZE * 0.5f / FishConstants.MINIGAME_TRACK_HEIGHT;

        if (fishPosition < margin || fishPosition > 1f - margin) fishVelocity = 0f;
        fishPosition = MathUtils.clamp(fishPosition, margin, 1f - margin);
    }

    /** Fills while the fish is inside the bar, drains while it is not. */
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

        //dev mode: floor instead of escaping, so the fish can be retuned indefinitely
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
     * Treasure doesn't move or affect the fish - taking it just costs bar time spent reaching it. A
     * resolved piece (taken or timed out, either counts against the debt) makes room for the next
     * owed piece {@link FishConstants#TREASURE_SPAWN_INTERVAL} seconds later.
     */
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

    /** The piece on the track right now - null when there is nothing down there, which is most catches. */
    public MinigameTreasure getTreasure() {
        return treasure;
    }

    /** Every piece that was actually held onto this catch, in the order it was taken. */
    public java.util.List<MinigameTreasure> getTakenTreasures() {
        return takenTreasures;
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

    /** {@link #setEscaped()}'s mirror, for the dev controls: the meter filled, the fish is landed. */
    public void setCaught() {
        progress = 1f;
        state = State.CAUGHT;
    }

    /** Dev: adds a piece directly to the taken pile - only rarity is ever read off a taken piece. */
    public void devTakeTreasure() {
        takenTreasures.add(new MinigameTreasure(TreasureRoller.rollRarity()));
    }

    /** Dev: spawns a fresh piece now, replacing any current one - for practising the take, not auditing the debt. */
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
