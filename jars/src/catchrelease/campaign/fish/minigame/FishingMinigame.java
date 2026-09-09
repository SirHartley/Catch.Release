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

    protected final FishSpec fish;
    protected final Tackle tackle;
    protected final FishingSimulation simulation;
    protected java.awt.Color presentedColor;

    protected MinigameTreasure treasure;
    protected final java.util.List<MinigameTreasure> takenTreasures = new java.util.ArrayList<>();
    protected int treasuresLeft;
    protected float treasureClock;
    protected final float rumorLootMult;
    protected final float rumorLootRarityBias;

    public FishingMinigame(FishSpec fish, Tackle tackle) {
        this(fish, tackle, Global.getSector().getPlayerFleet().getContainingLocation());
    }

    public FishingMinigame(FishSpec fish, Tackle tackle, LocationAPI location) {
        this.fish = fish;
        this.tackle = tackle == null ? Tackle.NONE : tackle;
        boolean legendary = fish.rarity == FishRarity.LEGENDARY;
        rumorLootMult = FishRumors.getLootMult(location);
        rumorLootRarityBias = legendary ? 1f : FishRumors.getLootRarityBias(location);
        simulation = new FishingSimulation(fish.difficulty,
                fish.motionSpeed * (legendary ? 1f : FishRumors.getMotionMult(location)),
                fish.restlessness, fish.progressRateMult, fish.escapeRateMult, fish.motion,
                this.tackle, UpgradeManager.getValue(StatIds.FISHING_BAR_SIZE,
                FishConstants.MINIGAME_BAR_SIZE_FALLBACK), MathUtils::getRandomNumberInRange);
        simulation.setCannotLose(Global.getSettings().isDevMode());
        rollTreasure();
    }

    public void restart() {
        simulation.restart();
        rollTreasure();
    }

    public static float getBarHeight() {
        float pixels = UpgradeManager.getValue(
                StatIds.FISHING_BAR_SIZE, FishConstants.MINIGAME_BAR_SIZE_FALLBACK);

        return MathUtils.clamp(pixels / FishConstants.MINIGAME_TRACK_HEIGHT,
                FishConstants.MINIGAME_BAR_MIN_FRACTION, FishConstants.MINIGAME_BAR_MAX_FRACTION);
    }

    public void advance(float amount, boolean reeling) {
        if (!simulation.isRunning()) return;
        simulation.advanceMotion(amount, reeling);
        advanceTreasure(amount);
        simulation.advanceProgress(amount, simulation.isFishInBar()
                ? UpgradeManager.getValue(StatIds.MINIGAME_PROGRESS_RATE, 1f)
                : UpgradeManager.getValue(StatIds.MINIGAME_ESCAPE_RESIST, 1f));
    }

    protected void rollTreasure() {
        takenTreasures.clear();
        treasureClock = 0f;
        treasuresLeft = TreasureRoller.rollCount(
                tackle.treasureChanceMult * rumorLootMult);

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

    public FishSpec getFish() {
        return fish;
    }

    public java.awt.Color getPresentedColor() {
        return presentedColor != null ? presentedColor : fish.rarity.color;
    }

    public void setPresentedColor(java.awt.Color presentedColor) {
        this.presentedColor = presentedColor;
    }

    public void devTakeTreasure() {
        takenTreasures.add(new MinigameTreasure(TreasureRoller.rollRarity(rumorLootRarityBias)));
    }

    public void devSpawnTreasure() {
        treasuresLeft++;
        treasure = spawnTreasure();
    }

    public boolean isFishInBar() {
        return simulation.isFishInBar();
    }

    public boolean covers(float position) {
        return simulation.covers(position);
    }

    public float getDifficulty() {
        return simulation.getDifficulty();
    }

    public void setDifficulty(float value) {
        simulation.setDifficulty(value);
    }

    public float getMotionSpeed() {
        return simulation.getMotionSpeed();
    }

    public void setMotionSpeed(float value) {
        simulation.setMotionSpeed(value);
    }

    public boolean isCannotLose() {
        return simulation.isCannotLose();
    }

    public void setCannotLose(boolean value) {
        simulation.setCannotLose(value);
    }

    public FishMotion getMotion() {
        return simulation.getMotion();
    }

    public void setMotion(FishMotion value) {
        simulation.setMotion(value);
    }

    public boolean isRunning() {
        return simulation.isRunning();
    }

    public boolean isCaught() {
        return simulation.isCaught();
    }

    public void setEscaped() {
        simulation.setEscaped();
    }

    public void setCaught() {
        simulation.setCaught();
    }

    public float getProgress() {
        return simulation.getProgress();
    }

    public float getBarPosition() {
        return simulation.getBarPosition();
    }

    public float getBarHeightFraction() {
        return simulation.getBarHeightFraction();
    }

    public float getFishVelocity() {
        return simulation.getFishVelocity();
    }

    public float getFishPosition() {
        return simulation.getFishPosition();
    }

    public float getTimeHeld() {
        return simulation.getTimeHeld();
    }

    public float getTimeTotal() {
        return simulation.getTimeTotal();
    }

    public State getState() {
        return State.valueOf(simulation.getState().name());
    }
}
