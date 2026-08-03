package catchrelease.campaign.fish.treasure;

import catchrelease.campaign.fish.constants.FishConstants;
import org.lazywizard.lazylib.MathUtils;

/**
 * A piece of treasure sitting in the track while the catch runs.
 * <p>
 * It does not move. That is the whole of what makes it different from the fish: the fish is the
 * thing you are chasing and this is a thing you can choose to detour for, and a detour is only a
 * decision if the thing you are detouring to stays put.
 * <p>
 * It does not last, either. The clock under it is the pressure - take it now and give up ground on
 * the fish, or let it go.
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

        //away from the very ends, where the bar rests anyway - treasure you get by doing nothing is
        //not treasure
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

        //the hold is not a ratchet: letting the bar slip off it gives the time back, so taking one
        //is a commitment rather than something that happens by accident on the way past
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
