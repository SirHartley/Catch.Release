package catchrelease.campaign.fish.treasure;

import catchrelease.campaign.fish.constants.FishConstants;
import org.lazywizard.lazylib.MathUtils;

/**
 * A piece of treasure sitting in the track while the catch runs. Unlike the fish it does not move -
 * taking it is a detour, not a chase. It does time out though, forcing the choice: take it now and
 * give up ground on the fish, or let it go.
 */
public class MinigameTreasure {

    public final TreasureRarity rarity;

    /** Where in the track it sits, 0 at the bottom to 1 at the top. Fixed for its whole life. */
    public final float position;

    protected float lifetime;
    protected float elapsed = 0f;

    /** How long the bar has been over it. Taking one is not instant; it has to be held. */
    protected float held = 0f;

    protected boolean taken = false;

    public MinigameTreasure(TreasureRarity rarity) {
        this.rarity = rarity;

        //away from the very ends, where the bar rests anyway by default
        this.position = MathUtils.getRandomNumberInRange(
                FishConstants.TREASURE_POSITION_INSET, 1f - FishConstants.TREASURE_POSITION_INSET);

        this.lifetime = MathUtils.getRandomNumberInRange(
                FishConstants.TREASURE_LIFETIME_MIN, FishConstants.TREASURE_LIFETIME_MAX);
    }

    /**
     * @param covered whether the bar is over it this frame
     */
    public void advance(float amount, boolean covered) {
        if (taken || isGone()) return;

        elapsed += amount;

        //not a ratchet: letting the bar slip off gives the held time back
        if (covered) held += amount;
        else held = Math.max(0f, held - amount * FishConstants.TREASURE_HOLD_DECAY);

        if (held >= FishConstants.TREASURE_HOLD_TIME) taken = true;
    }

    public boolean isTaken() {
        return taken;
    }

    /** True once it has timed out without being taken. */
    public boolean isGone() {
        return !taken && elapsed >= lifetime;
    }

    /** Still worth drawing: either it is sitting there, or it is being taken. */
    public boolean isActive() {
        return !taken && !isGone();
    }

    /** 1 when it arrives, 0 when it goes. What the bar under it shows. */
    public float getTimeLeft() {
        return MathUtils.clamp(1f - elapsed / Math.max(0.01f, lifetime), 0f, 1f);
    }

    /** 0 to 1 towards being taken, for the ring that closes as it is held. */
    public float getHeldFraction() {
        return MathUtils.clamp(held / Math.max(0.01f, FishConstants.TREASURE_HOLD_TIME), 0f, 1f);
    }
}
