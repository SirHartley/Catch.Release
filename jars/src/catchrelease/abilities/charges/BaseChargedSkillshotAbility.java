package catchrelease.abilities.charges;

import catchrelease.memory.charges.ChargeManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.skillshot.ability.BaseSkillshotAbility;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

import java.awt.Color;

public abstract class BaseChargedSkillshotAbility extends BaseSkillshotAbility {

    public static final float REARM_SECONDS = 0.2f;
    public static final int AVAILABLE_REGEN_COOLDOWN_ALPHA = 90;

    protected float rearmLeft = 0f;

    public abstract String getChargeId();

    public abstract ChargeManager.Refill getRefill();

    protected ChargeManager.Refill defineRefill() {
        ChargeManager.Refill refill = getRefill();
        ChargeManager.define(getChargeId(), refill);

        return refill;
    }

    public boolean hasCharge() {
        ChargeManager.Refill refill = defineRefill();

        return ChargeManager.hasCharge(getChargeId(), refill.maxStat, refill.maxFallback);
    }

    public int getCharges() {
        ChargeManager.Refill refill = defineRefill();

        return ChargeManager.getCharges(getChargeId(), refill.maxStat, refill.maxFallback);
    }

    public int getMaxCharges() {
        ChargeManager.Refill refill = getRefill();

        return (int) Math.max(1f, UpgradeManager.getValue(refill.maxStat, refill.maxFallback));
    }

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

    @Override
    public boolean isOnCooldown() {
        return rearmLeft > 0f || !hasCharge();
    }

    @Override
    public Color getCooldownColor() {
        if (!hasCharge()) return super.getCooldownColor();

        return new Color(0, 0, 0, AVAILABLE_REGEN_COOLDOWN_ALPHA);
    }

    @Override
    public boolean isUsable() {
        return hasCharge() && !isInHyperspace() && super.isUsable();
    }

    protected boolean isInHyperspace() {
        CampaignFleetAPI fleet = getFleet();

        return fleet != null && fleet.getContainingLocation() != null
                && fleet.getContainingLocation().isHyperspace();
    }
}
