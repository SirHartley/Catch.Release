package catchrelease.abilities.charges;

import catchrelease.memory.charges.ChargeManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.skillshot.ability.BaseSkillshotAbility;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

/**
 * A skillshot that fires out of a charge pool rather than off a rearm timer.
 * <p>
 * The pool is the whole gate, so the ability's own cooldown has to get out of its way. Left to the
 * vanilla one the two contradict each other: the pool says "you have two harpoons, spend them how
 * you like" and a flat rearm answers "one at a time anyway", which is the decision the pool exists
 * to hand back to the player.
 * <p>
 * So the icon shows the wait that is actually in front of you. With charges in hand that is the
 * blink between two shots - long enough that one press is one shot, short enough that a pass over a
 * shoal is a burst. With the pool empty it is the next charge arriving, and the sweep fills at
 * exactly the rate the pool does.
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

    /**
     * Registers the refill rule and hands it back.
     * <p>
     * Done on every query rather than once: the manager lives on the sector, so every new game and
     * every load has to be told again. It takes the last call, so repeating it costs nothing.
     */
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

    /**
     * The wait in front of the player, rather than the one in the spec.
     * <p>
     * Empty pool: the next charge, which the manager is already tracking as a fraction - so the
     * sweep is the pool filling rather than a second timer that happens to run alongside it.
     * Otherwise: the blink between shots, and nothing once that is up.
     */
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

    /**
     * No fishing gear works in hyperspace. All fishing is done from realspace into hyperspace -
     * the fabric is what the gear works through, and out there is the wrong side of it. The gate
     * sits here so every charged skillshot answers the same way, rather than each rig
     * rediscovering the rule.
     */
    protected boolean isInHyperspace() {
        CampaignFleetAPI fleet = getFleet();

        return fleet != null && fleet.getContainingLocation() != null
                && fleet.getContainingLocation().isHyperspace();
    }
}
