package catchrelease.campaign.fish.treasure;

import catchrelease.campaign.fish.constants.FishConstants;
import org.lazywizard.lazylib.MathUtils;


public class MinigameTreasure {

    public final TreasureRarity rarity;


    public final float position;

    protected float lifetime;
    protected float elapsed = 0f;


    protected float held = 0f;

    protected boolean taken = false;

    public MinigameTreasure(TreasureRarity rarity) {
        this.rarity = rarity;

        this.position = MathUtils.getRandomNumberInRange(
                FishConstants.TREASURE_POSITION_INSET, 1f - FishConstants.TREASURE_POSITION_INSET);

        this.lifetime = MathUtils.getRandomNumberInRange(
                FishConstants.TREASURE_LIFETIME_MIN, FishConstants.TREASURE_LIFETIME_MAX);
    }


    public void advance(float amount, boolean covered) {
        if (taken || isGone()) return;

        elapsed += amount;

        // not a ratchet: letting the bar slip off gives the held time back
        if (covered) held += amount;
        else held = Math.max(0f, held - amount * FishConstants.TREASURE_HOLD_DECAY);

        if (held >= FishConstants.TREASURE_HOLD_TIME) taken = true;
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
        return MathUtils.clamp(held / Math.max(0.01f, FishConstants.TREASURE_HOLD_TIME), 0f, 1f);
    }
}
