package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.treasure.MinigameTreasure;
import catchrelease.campaign.fish.treasure.TreasureRarity;
import catchrelease.campaign.fish.treasure.TreasureRoller;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
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
    protected float specialChance;
    protected float mixChance;
    protected FishMotion motion;
    protected FishMotion activeMotion;
    protected float legSpeedMult = 1f;
    protected float legThinkMult = 1f;

    // a telegraphed move waits out its tell while the fish keeps its old course
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
    protected MinigameTreasure treasure;
    protected final java.util.List<MinigameTreasure> takenTreasures = new java.util.ArrayList<>();
    protected int treasuresLeft = 0;
    protected float treasureClock = 0f;
    protected Tackle tackle = Tackle.NONE;
    protected State state = State.RUNNING;
    protected boolean cannotLose = false;
    protected java.awt.Color presentedColor;

    protected final float rumorLootMult;
    protected final float rumorLootRarityBias;

    protected float timeHeld = 0f;
    protected float timeTotal = 0f;

    protected record Move(float target, FishMotion motion, float speedMult, float thinkMult, boolean telegraphed) {

    }

    public FishingMinigame(FishSpec fish, Tackle tackle) {
        this(fish, tackle, Global.getSector().getPlayerFleet().getContainingLocation());
    }

    public FishingMinigame(FishSpec fish, Tackle tackle, LocationAPI location) {
        this.fish = fish;
        this.tackle = tackle == null ? Tackle.NONE : tackle;

        boolean legendary = fish.rarity == FishRarity.LEGENDARY;
        this.rumorLootMult = FishRumors.getLootMult(location);
        this.rumorLootRarityBias = legendary ? 1f : FishRumors.getLootRarityBias(location);

        this.difficulty = fish.difficulty;
        this.motionSpeed = fish.motionSpeed * (legendary ? 1f : FishRumors.getMotionMult(location));
        this.restlessness = fish.restlessness;
        this.progressRateMult = fish.progressRateMult;
        this.escapeRateMult = fish.escapeRateMult;
        this.specialChance = fish.specialChance;
        this.mixChance = fish.mixChance;
        this.motion = fish.motion;

        // clamped after the tackle has had its say, so a wide window is still a window
        this.barHeight = MathUtils.clamp(getBarHeight() * this.tackle.barSizeMult,
                FishConstants.MINIGAME_BAR_MIN_FRACTION, FishConstants.MINIGAME_BAR_MAX_FRACTION);
        applyMove(chooseMove());
        this.cannotLose = Global.getSettings().isDevMode();

        rollTreasure();
    }

    protected void rollTreasure() {
        takenTreasures.clear();
        treasureClock = 0f;
        treasuresLeft = TreasureRoller.rollCount(
                tackle.treasureChanceMult * rumorLootMult);

        // a legendary always carries a full hold of the best there is
        if (fish.rarity == FishRarity.LEGENDARY) {
            treasuresLeft = Math.max(3, treasuresLeft);
        }

        treasure = spawnTreasure();
    }

    protected MinigameTreasure spawnTreasure() {
        if (treasuresLeft <= 0) return null;

        treasuresLeft--;
        if (fish.rarity == FishRarity.LEGENDARY) {
            return new MinigameTreasure(TreasureRarity.EPIC);
        }

        return new MinigameTreasure(TreasureRoller.rollRarity(rumorLootRarityBias));
    }

    public void restart() {
        barPosition = 0.4f;
        barVelocity = 0f;
        fishPosition = 0.5f;
        fishVelocity = 0f;
        fishThinkTimer = 0f;
        applyMove(chooseMove());
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
        if (pendingMove != null) {
            tellLeft -= amount;
            if (tellLeft <= 0f) startMove(pendingMove);
        } else {
            fishThinkTimer -= amount;

            if (fishThinkTimer <= 0f) {
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

        // a lunger is two speeds pretending to be one fish: near its spot it is almost parked,
        // and the trip to the next spot is violent
        if (activeMotion == FishMotion.LUNGER) {
            maxSpeed *= Math.abs(fishTarget - fishPosition) > FishConstants.MINIGAME_LUNGER_NEAR
                    ? FishConstants.MINIGAME_LUNGER_DASH_MULT
                    : FishConstants.MINIGAME_LUNGER_CREEP_MULT;
        }

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
            progress += FishConstants.MINIGAME_CATCH_RATE * compressRate(progressRateMult)
                    * amount
                    * UpgradeManager.getValue(StatIds.MINIGAME_PROGRESS_RATE, 1f)
                    * tackle.progressMult;
        } else {
            progress -= FishConstants.MINIGAME_ESCAPE_RATE * compressRate(escapeRateMult)
                    * amount
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

    // per-fish rate extremes softened toward baseline for the same reason the
    // difficulty curve bends: flat player power has no answer to a raw 2x drain
    protected float compressRate(float mult) {
        return 1f + (mult - 1f) * FishConstants.MINIGAME_RATE_COMPRESSION;
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

    // a fish mostly moves in its own style; the sheet gives each species a share of signature
    // moves and a share of moves borrowed from the MIXED pool
    protected Move chooseMove() {
        FishMotion own = motion == null ? FishMotion.SMOOTH : motion;
        if (own == FishMotion.MIXED) return borrowedMove();

        // no roll without a share, so such a species draws exactly as it did before shares existed
        float roll = mixChance + specialChance > 0f ? MathUtils.getRandomNumberInRange(0f, 1f) : 1f;
        if (roll < mixChance) return borrowedMove();
        if (roll < mixChance + specialChance) return pickSpecialMove(own);

        return new Move(pickTarget(own), own, 1f, 1f, false);
    }

    // the think time starts with the move, not with the tell before it
    protected void startMove(Move move) {
        applyMove(move);
        fishThinkTimer = pickThinkTime();
    }

    // also the opening move, which is replaced on the first frame and so never gets a tell
    protected void applyMove(Move move) {
        fishTarget = move.target();
        activeMotion = move.motion();
        legSpeedMult = move.speedMult();
        legThinkMult = move.thinkMult();
        pendingMove = null;
        tellLeft = 0f;
        tellDirection = 0f;
    }

    // no style predicts a borrowed move, so a big one gets a tell
    protected Move borrowedMove() {
        FishMotion type = pickMixedMotion();
        float target = pickTarget(type);
        boolean big = target != fishTarget
                && Math.abs(target - fishPosition) >= FishConstants.MINIGAME_TELL_DISTANCE;

        return new Move(target, type, 1f, 1f, big);
    }

    // each signature move breaks its style's no-effort answer without leaving the style, and is always telegraphed
    protected Move pickSpecialMove(FishMotion own) {
        switch (own) {
            case DARTER:
                // doubles back to the far end before the bar has settled
                return new Move(fishPosition > 0.5f
                        ? MathUtils.getRandomNumberInRange(0f, 0.25f)
                        : MathUtils.getRandomNumberInRange(0.75f, 1f), own, 1f, FishConstants.MINIGAME_QUICK_THINK, true);

            case SINKER:
                // surges into the upper track, so a bar parked at the bottom loses it
                return new Move(MathUtils.getRandomNumberInRange(FishConstants.MINIGAME_SURGE_MIN,
                        FishConstants.MINIGAME_SURGE_MAX), own, 1f, FishConstants.MINIGAME_SURGE_THINK, true);

            case FLOATER:
                return new Move(MathUtils.getRandomNumberInRange(1f - FishConstants.MINIGAME_SURGE_MAX,
                        1f - FishConstants.MINIGAME_SURGE_MIN), own, 1f, FishConstants.MINIGAME_SURGE_THINK, true);

            case WEAVER: {
                // turns back mid-sweep, or stops short at the end of one, so the rhythm cannot be played blind
                float target = Math.abs(fishPosition - fishTarget) >= FishConstants.MINIGAME_WEAVER_ARRIVE
                        ? fishTarget > 0.5f ? FishConstants.MINIGAME_WEAVER_LOW : FishConstants.MINIGAME_WEAVER_HIGH
                        : MathUtils.getRandomNumberInRange(FishConstants.MINIGAME_WEAVER_SHORT_MIN,
                                FishConstants.MINIGAME_WEAVER_SHORT_MAX);

                return new Move(target, own, 1f, 1f, true);
            }

            case TWITCHER: {
                // bounds away from the nearer edge, so the leap always has room
                float direction = fishPosition > 0.5f ? -1f : 1f;

                return new Move(MathUtils.clamp(fishPosition + direction * MathUtils.getRandomNumberInRange(
                        FishConstants.MINIGAME_TWITCHER_BOUND_MIN, FishConstants.MINIGAME_TWITCHER_BOUND_MAX),
                        0.05f, 0.95f), own, FishConstants.MINIGAME_TWITCHER_BOUND_SPEED, 1f, true);
            }

            case LUNGER:
                return new Move(MathUtils.getRandomNumberInRange(0f, 1f), own, 1f, FishConstants.MINIGAME_QUICK_THINK, true);

            default:
                return new Move(fishPosition > 0.5f
                        ? MathUtils.getRandomNumberInRange(0f, FishConstants.MINIGAME_SMOOTH_BURST_REACH)
                        : MathUtils.getRandomNumberInRange(1f - FishConstants.MINIGAME_SMOOTH_BURST_REACH, 1f),
                        own, FishConstants.MINIGAME_SMOOTH_BURST_SPEED, 1f, true);
        }
    }

    protected float pickTarget(FishMotion type) {
        switch (type) {
            case DARTER:
                // bolts somewhere else entirely rather than drifting a little
                return MathUtils.getRandomNumberInRange(0f, 1f) < 0.5f
                        ? MathUtils.getRandomNumberInRange(0f, 0.25f)
                        : MathUtils.getRandomNumberInRange(0.75f, 1f);

            case SINKER:
                return MathUtils.getRandomNumberInRange(0f, FishConstants.MINIGAME_SINKER_CEILING);

            case FLOATER:
                return MathUtils.getRandomNumberInRange(FishConstants.MINIGAME_FLOATER_FLOOR, 1f);

            case WEAVER:
                // flips only once it has arrived, so the full sweep survives any difficulty -
                // a timer flip mid-crossing would collapse the metronome into centre jitter
                return Math.abs(fishPosition - fishTarget) >= FishConstants.MINIGAME_WEAVER_ARRIVE
                        ? fishTarget
                        : fishPosition > 0.5f
                                ? FishConstants.MINIGAME_WEAVER_LOW
                                : FishConstants.MINIGAME_WEAVER_HIGH;

            case TWITCHER: {
                float hop = MathUtils.getRandomNumberInRange(0f, 1f)
                        < FishConstants.MINIGAME_TWITCHER_LEAP_CHANCE
                        ? FishConstants.MINIGAME_TWITCHER_LEAP
                        : FishConstants.MINIGAME_TWITCHER_HOP;

                return MathUtils.clamp(fishPosition
                        + MathUtils.getRandomNumberInRange(-hop, hop), 0.05f, 0.95f);
            }

            case LUNGER:
                return MathUtils.getRandomNumberInRange(0f, 1f);

            default:
                return MathUtils.getRandomNumberInRange(0f, 1f);
        }
    }

    protected FishMotion pickMixedMotion() {
        FishMotion[] options = {FishMotion.SMOOTH, FishMotion.DARTER, FishMotion.SINKER,
                FishMotion.FLOATER, FishMotion.WEAVER, FishMotion.TWITCHER, FishMotion.LUNGER};

        return options[(int) MathUtils.getRandomNumberInRange(0, options.length - 0.001f)];
    }

    protected float pickThinkTime() {
        float base = MathUtils.getRandomNumberInRange(
                FishConstants.MINIGAME_THINK_TIME_MIN, FishConstants.MINIGAME_THINK_TIME_MAX);

        float divisor = Math.max(0.1f, restlessness * getDifficultyMult());

        // cadence is keyed to the rolled motion, not the sheet's - a MIXED fish that just rolled
        // a lunger has to actually sit still, or the roll changes nothing but the target
        switch (activeMotion == null ? FishMotion.SMOOTH : activeMotion) {
            case DARTER:
                // a darter is defined by the wait before the bolt, so it gets to keep more of it
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

        // the end-rest is the only window a slower bar ever gets against a fast weaver, so
        // difficulty may shorten it but never remove it
        if (activeMotion == FishMotion.WEAVER) {
            think = Math.max(FishConstants.MINIGAME_WEAVER_DWELL_FLOOR, think);
        }

        return think * legThinkMult;
    }

    protected float getDifficultyMult() {
        // square root, not linear: the player's power is flat - there are no minigame
        // upgrades - so the top of the sheet has to compress toward what a bare bar
        // can still chase, while the sheet's ordering survives untouched
        float scaled = FishConstants.MINIGAME_DIFFICULTY_FLOOR
                + FishConstants.MINIGAME_DIFFICULTY_SCALE
                * (float) Math.sqrt(difficulty / FishConstants.MINIGAME_DIFFICULTY_BASELINE);

        return Math.max(0.2f, scaled * FishConstants.MINIGAME_GLOBAL_DIFFICULTY);
    }

    public FishSpec getFish() {
        return fish;
    }

    /** What the fish claims to be on screen - a shell-game body lies about its colour. */
    public java.awt.Color getPresentedColor() {
        return presentedColor != null ? presentedColor : fish.rarity.color;
    }

    public void setPresentedColor(java.awt.Color presentedColor) {
        this.presentedColor = presentedColor;
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
        pendingMove = null;
        tellLeft = 0f;
        tellDirection = 0f;
    }

    // 0 when no tell is showing, otherwise how far the pending move is through its tell
    public float getTellProgress() {
        return pendingMove == null || state != State.RUNNING ? 0f : 1f - tellLeft / FishConstants.MINIGAME_TELL_TIME;
    }

    // +1 up the track, -1 down, 0 when no tell is showing
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

    public void devTakeTreasure() {
        takenTreasures.add(new MinigameTreasure(TreasureRoller.rollRarity(rumorLootRarityBias)));
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
