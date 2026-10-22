package catchrelease.campaign.fish.treasure;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.config.Setting;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

import java.awt.Color;

public class MinigameTreasure {

    public final TreasureRarity rarity;
    // a quest's own chest (TreasureRumour): drawn in the quest highlight and never rolled from a rarity
    public final boolean golden;
    public final float position;
    protected float lifetime;
    protected float elapsed = 0f;
    protected float held = 0f;
    protected boolean taken = false;
    protected final float holdTime = FishConstants.TREASURE_HOLD_TIME * Setting.TREASURE_HOLD.mult();
    protected final float decay = FishConstants.TREASURE_HOLD_DECAY * Setting.TREASURE_DECAY.mult();

    public MinigameTreasure(TreasureRarity rarity) {
        this(rarity, false);
    }

    public MinigameTreasure(TreasureRarity rarity, boolean golden) {
        this.rarity = rarity;
        this.golden = golden;

        this.position = MathUtils.getRandomNumberInRange(
                FishConstants.TREASURE_POSITION_INSET, 1f - FishConstants.TREASURE_POSITION_INSET);

        this.lifetime = MathUtils.getRandomNumberInRange(
                FishConstants.TREASURE_LIFETIME_MIN, FishConstants.TREASURE_LIFETIME_MAX) * Setting.TREASURE_LIFE.mult();
    }

    public void advance(float amount, boolean covered) {
        if (taken || isGone()) return;

        elapsed += amount;

        // not a ratchet: letting the bar slip off gives the held time back
        if (covered) held += amount;
        else held = Math.max(0f, held - amount * decay);

        if (held >= holdTime) taken = true;
    }

    public Color getColor() {
        return golden ? Misc.getHighlightColor() : rarity.color;
    }

    public boolean isTaken() {
        return taken;
    }

    public boolean isGone() {
        return !taken && elapsed >= lifetime;
    }

    public boolean isActive() {
        return !taken && !isGone();
    }

    public float getTimeLeft() {
        return MathUtils.clamp(1f - elapsed / Math.max(0.01f, lifetime), 0f, 1f);
    }

    public float getHeldFraction() {
        return MathUtils.clamp(held / Math.max(0.01f, holdTime), 0f, 1f);
    }
}
