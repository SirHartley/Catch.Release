package catchrelease.abilities.charges;

import catchrelease.memory.charges.ChargeManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.skillshot.ability.BaseSkillshotAbility;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

/**
 * A skillshot that fires out of a charge pool rather than a rearm timer. The pool alone gates
 * firing - no vanilla flat rearm on top. {@link #getCooldownFraction()} shows whichever wait is
 * actually in front of the player: the short inter-shot blink with charges in hand, or the pool's
 * own refill progress when empty.
 */
public abstract class BaseChargedSkillshotAbility extends BaseSkillshotAbility {

    /** The blink between two shots out of the same pool. */
    public static final float REARM_SECONDS = 0.2f;

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

    /** Progress toward next charge when the pool is empty; otherwise the inter-shot rearm blink. */
    @Override
    public float getCooldownFraction() {
        ChargeManager.Refill refill = defineRefill();

        if (!ChargeManager.hasCharge(getChargeId(), refill.maxStat, refill.maxFallback)) {
            return ChargeManager.getProgressToNext(getChargeId(), refill.maxStat, refill.maxFallback);
        }

        if (rearmLeft <= 0f) return 1f;

        return 1f - rearmLeft / REARM_SECONDS;
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
