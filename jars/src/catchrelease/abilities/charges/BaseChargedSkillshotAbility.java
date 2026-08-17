package catchrelease.abilities.charges;

import catchrelease.memory.charges.ChargeManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.skillshot.ability.BaseSkillshotAbility;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

import java.awt.Color;

/**
 * A skillshot that fires out of a charge pool rather than a rearm timer. The pool alone gates
 * firing - no vanilla flat rearm on top. {@link #getCooldownFraction()} shows the pool's progress
 * toward its next charge whenever the pool is not full, including while another charge remains
 * ready to fire. The short inter-shot blink is only visible when the pool refills completely before
 * that rearm ends.
 */
public abstract class BaseChargedSkillshotAbility extends BaseSkillshotAbility {

    /** The blink between two shots out of the same pool. */
    public static final float REARM_SECONDS = 0.2f;

    /** Vanilla's cooldown shade is alpha 171; partial pools stay legible under this lighter veil. */
    public static final int AVAILABLE_REGEN_COOLDOWN_ALPHA = 90;

    /** Counts in campaign seconds, like the pool it sits next to. */
    protected float rearmLeft = 0f;

    /** Which pool this ability spends from. */
    public abstract String getChargeId();

    /** How big that pool is and how fast it fills. */
    public abstract ChargeManager.Refill getRefill();

    /** Registers the refill rule and returns it. Called every query since the manager lives on the sector and forgets on load. */
    protected ChargeManager.Refill defineRefill() {
        ChargeManager.Refill refill = getRefill();
        ChargeManager.define(getChargeId(), refill);

        return refill;
    }

    public boolean hasCharge() {
        ChargeManager.Refill refill = defineRefill();

        return ChargeManager.hasCharge(getChargeId(), refill.maxStat, refill.maxFallback);
    }

    /** How many whole charges are in hand. */
    public int getCharges() {
        ChargeManager.Refill refill = defineRefill();

        return ChargeManager.getCharges(getChargeId(), refill.maxStat, refill.maxFallback);
    }

    /** How many the pool holds, upgrades included. */
    public int getMaxCharges() {
        ChargeManager.Refill refill = getRefill();

        return (int) Math.max(1f, UpgradeManager.getValue(refill.maxStat, refill.maxFallback));
    }

    /**
     * Takes a charge and starts the rearm.
     *
     * @return false if the pool was empty, in which case nothing was taken and nothing should fire
     */
    protected boolean spendCharge() {
        ChargeManager.Refill refill = defineRefill();

        if (!ChargeManager.spend(getChargeId(), refill.maxStat, refill.maxFallback)) return false;

        rearmLeft = REARM_SECONDS;

        return true;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (rearmLeft <= 0f) return;

        rearmLeft -= amount;
        if (rearmLeft < 0f) rearmLeft = 0f;
    }

    /** Progress toward the next missing charge; at a full pool, only the inter-shot rearm remains. */
    @Override
    public float getCooldownFraction() {
        ChargeManager.Refill refill = defineRefill();

        int charges = ChargeManager.getCharges(getChargeId(), refill.maxStat, refill.maxFallback);
        if (charges < getMaxCharges()) {
            return ChargeManager.getProgressToNext(getChargeId(), refill.maxStat, refill.maxFallback);
        }

        if (rearmLeft <= 0f) return 1f;

        return 1f - rearmLeft / REARM_SECONDS;
    }

    /**
     * Refill progress and ability lockout are deliberately separate: a partial pool paints the
     * cooldown indicator but does not disable a charge that is already ready. Vanilla's ability
     * base asks this virtual method from {@code isUsable()}, while its button renderer asks
     * {@link #getCooldownFraction()} independently.
     */
    @Override
    public boolean isOnCooldown() {
        return rearmLeft > 0f || !hasCharge();
    }

    /** Empty uses vanilla's strong disabled shade; a usable, refilling pool gets a lighter veil. */
    @Override
    public Color getCooldownColor() {
        if (!hasCharge()) return super.getCooldownColor();

        return new Color(0, 0, 0, AVAILABLE_REGEN_COOLDOWN_ALPHA);
    }

    @Override
    public boolean isUsable() {
        return hasCharge() && !isInHyperspace() && super.isUsable();
    }

    /** No fishing gear works in hyperspace - fishing is done from realspace into it, through the fabric. */
    protected boolean isInHyperspace() {
        CampaignFleetAPI fleet = getFleet();

        return fleet != null && fleet.getContainingLocation() != null
                && fleet.getContainingLocation().isHyperspace();
    }
}
