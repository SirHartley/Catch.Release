package catchrelease.abilities.harpoon.ability;

import catchrelease.config.FishingSounds;
import catchrelease.ModPlugin;
import catchrelease.abilities.charges.BaseChargedSkillshotAbility;
import catchrelease.abilities.harpoon.constants.HarpoonConstants;
import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.charges.ChargeManager;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import lunalib.lunaSettings.LunaSettings;
import catchrelease.skillshot.GuideLineStyle;
import catchrelease.skillshot.render.DirectionReticuleRenderer;
import catchrelease.skillshot.render.SkillshotRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class HarpoonAbilityPlugin extends BaseChargedSkillshotAbility {

    public static final String CHARGE_ID = "catchrelease_harpoon";

    protected static class HarpoonRefill extends ChargeManager.Refill {

        protected HarpoonRefill() {
            super(StatIds.HARPOON_CHARGES, HarpoonConstants.CHARGES_FALLBACK,
                    StatIds.HARPOON_RECHARGE_TIME, HarpoonConstants.RECHARGE_FALLBACK);
        }

        @Override
        public void onChargeGained() {
            if (shouldPlayChargeReload()) {
                FishingSounds.playUISound(HarpoonConstants.SOUND_CHARGE_RELOAD, 1f, 1f);
            }
        }
    }

    @Override
    public String getChargeId() {
        return CHARGE_ID;
    }

    @Override
    public ChargeManager.Refill getRefill() {
        return new HarpoonRefill();
    }

    public static boolean retrieveCharge() {
        return ChargeManager.gain(CHARGE_ID, new HarpoonRefill());
    }

    protected static boolean shouldPlayChargeReload() {
        String mode = LunaSettings.getString("catchrelease", HarpoonConstants.RELOAD_SOUND_SETTING);
        if (HarpoonConstants.RELOAD_SOUND_NEVER.equals(mode)) return false;

        if (Global.getSector().getCampaignUI().isShowingDialog()) return false;

        if (HarpoonConstants.RELOAD_SOUND_ALWAYS.equals(mode)) return true;

        CampaignFleetAPI fleet = Global.getSector() == null
                ? null : Global.getSector().getPlayerFleet();

        return SearchlightAbilityPlugin.isBreaching()
                || SearchlightAbilityPlugin.isNearActivePond(fleet);
    }

    @Override
    protected String getActivationText() {
        return "Harpoon";
    }

    @Override
    public String getSpriteName() {
        if (Global.getSector() != null && HarpoonEntityPlugin.isExplosive()) {
            return Global.getSettings().getSpriteName(ModPlugin.MOD_ID, "harpoon_explosive");
        }

        return super.getSpriteName();
    }

    @Override
    public SkillshotRenderer createReticule() {
        return (SkillshotRenderer) new DirectionReticuleRenderer()
                .withTrajectory()
                .withLength(getReach())
                .withLineStyle(GuideLineStyle.DASHED);
    }

    public static float getReach() {
        return UpgradeManager.getValue(StatIds.HARPOON_REACH, HarpoonConstants.RANGE);
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null || worldTarget == null) return;

        if (!spendCharge()) return;

        Vector2f from = new Vector2f(fleet.getLocation());

        // fired at the aim point rather than at a mote: missing is allowed, and is most of the skill
        SectorEntityToken harpoon = fleet.getContainingLocation().addCustomEntity(
                Misc.genUID(), null, HarpoonConstants.ENTITY_ID, null,
                new HarpoonEntityPlugin.Params(from, new Vector2f(worldTarget)));

        harpoon.addTag(HarpoonConstants.TAG);
        harpoon.setLocation(from.x, from.y);
        harpoon.setFacing(Misc.getAngleInDegrees(from, worldTarget));

        FishingSounds.playUISound(HarpoonConstants.SOUND_FIRE, 1f, 1f);
    }

    @Override
    public void pressButton() {
        if (cutIfHauling()) return;

        super.pressButton();
    }

    @Override
    public boolean showReticuleOnActivation() {
        return !HarpoonEntityPlugin.isAnyHauling();
    }

    @Override
    protected void onActivatedWithoutReticule() {
        cutIfHauling();
    }

    protected boolean cutIfHauling() {
        if (entity == null || !entity.isPlayerFleet()) return false;
        if (!HarpoonEntityPlugin.cutAllLines()) return false;

        playActivationSound();

        return true;
    }

    @Override
    public boolean isUsable() {
        if (HarpoonEntityPlugin.isAnyHauling()) return disableFrames <= 0;

        return super.isUsable();
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        Color highlight = Misc.getHighlightColor();
        float pad = 10f;

        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle(spec.getName());
        else tooltip.addSpacer(-10f);

        tooltip.addPara("Hooks fish exposed by ruptures or breach lamps, pushing them back on impact.", pad);

        tooltip.addPara("Charges: %s    Recharge: %s per charge", pad, highlight,
                getCharges() + "/" + getMaxCharges(),
                Misc.getRoundedValueOneAfterDecimalIfNotWhole(Math.max(0.1f, UpgradeManager.getValue(
                        StatIds.HARPOON_RECHARGE_TIME, HarpoonConstants.RECHARGE_FALLBACK))) + " seconds");
        tooltip.addPara("Range: %s    Shot speed: %s", 3f, highlight,
                Misc.getRoundedValue(getReach()) + " units",
                Misc.getRoundedValue(UpgradeManager.getValue(StatIds.HARPOON_SPEED, HarpoonConstants.SPEED)) + " units/s");

        float assist = UpgradeManager.getValue(StatIds.HARPOON_AIM_ASSIST, 0f);
        if (assist > 0f) {
            tooltip.addPara("Extra catch margin: %s", 3f, highlight,
                    Misc.getRoundedValue(assist) + " px");
        }

        Tackle fitted = TackleManager.get(Tackle.Fit.HARPOON);
        if (fitted != Tackle.NONE) tooltip.addPara("Fitted: %s", pad, highlight, fitted.name);
        if (fitted.explosive) {
            tooltip.addPara("Detonates instead of catching fish. Consumed on detonation;"
                    + " fleet hits cause damage and immediate hostility.", Misc.getNegativeHighlightColor(), 3f);
        } else {
            if (fitted.deepStrike) {
                tooltip.addPara("Can also hit diving fish and unexposed lamp contacts.", 3f);
            } else if (fitted.retrievesCharge) {
                tooltip.addPara("A fish hit restores %s.", 3f, highlight, "1 charge");
            }
            tooltip.addPara("Fleet hits pull the weaker fleet toward the stronger one and can damage relations."
                    + " Activate again to cut a tow line.", pad);
        }

        if (getFleet().isInHyperspace()) tooltip.addPara("Cannot be used in hyperspace.", Misc.getNegativeHighlightColor(), pad);
        else tooltip.addPara("Cannot be used in hyperspace.", Misc.getGrayColor(), pad);

        if (!Global.CODEX_TOOLTIP_MODE && !hasCharge()) {
            tooltip.addPara("No harpoons ready.", Misc.getNegativeHighlightColor(), pad);
        }

        if (!Global.CODEX_TOOLTIP_MODE && HarpoonEntityPlugin.isAnyHauling()) {
            tooltip.addPara("Tow line attached. Activate to cut it.", highlight, pad);
        }

        addIncompatibleToTooltip(tooltip, false);
    }
}
