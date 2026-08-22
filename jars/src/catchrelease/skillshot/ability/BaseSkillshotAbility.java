package catchrelease.skillshot.ability;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import catchrelease.skillshot.SkillshotFramework;
import catchrelease.skillshot.input.OnClickSkillshotListener;
import catchrelease.skillshot.input.SkillshotActivationManager;
import catchrelease.skillshot.util.DelayedActionScriptRunWhilePaused;
import catchrelease.skillshot.util.SkillshotUtils;


public abstract class BaseSkillshotAbility extends BaseDurationAbility implements SkillshotAbility {


    protected abstract void onSkillshotFired(Vector2f worldTarget, float angleFromFleet);


    protected void onConsume() {
    }


    public abstract void addTooltip(TooltipMakerAPI tooltip);


    protected void onActivatedWithoutReticule() {
    }

    @Override
    public boolean isUsable() {
        // no targeting session to block - usable wherever a vanilla ability would be, including core UI tabs
        if (!showReticuleOnActivation()) return super.isUsable();

        return super.isUsable() && !isTargetingBlocked();
    }


    public boolean isTargetingBlocked() {
        if (SkillshotActivationManager.getInstanceOrRegister().hasActiveListener()) return true;

        return Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null
                || Global.getSector().getCampaignUI().getCurrentCoreTab() != null;
    }

    @Override
    public boolean isActiveOrInProgress() {
        return super.isActiveOrInProgress() || turnedOn;
    }


    @Override
    public void pressButton() {
        // no reticule wanted - hand off to vanilla activation (it plays its own sound)
        if (!showReticuleOnActivation()) {
            super.pressButton();
            return;
        }

        if (!isUsable() || turnedOn) return;
        if (entity == null || !entity.isPlayerFleet()) return;

        playActivationSound();

        final SkillshotAbility self = this;
        SkillshotFramework.log("Starting click targeting for " + getId());

        // delayed one frame - registering the listener now would let the UI's own priority-0 click handling eat this very click
        Global.getSector().getScripts().add(new DelayedActionScriptRunWhilePaused(0f) {
            @Override
            public void doAction() {
                if (SkillshotActivationManager.getInstanceOrRegister().hasActiveListener()) return;
                new OnClickSkillshotListener(self).activate();
            }
        });
    }

    protected void playActivationSound() {
        String soundId = getOnSoundUI();
        if (soundId == null) return;

        if (PLAY_UI_SOUNDS_IN_WORLD_SOURCES) {
            Global.getSoundPlayer().playSound(soundId, 1f, 1f, Global.getSoundPlayer().getListenerPos(), new Vector2f());
        } else {
            Global.getSoundPlayer().playUISound(soundId, 1f, 1f);
        }
    }


    @Override
    public void forceActivation() {
        activateImpl();
        onConsume();
    }

    @Override
    public void activate() {
        super.activate();
        onConsume();
    }

    @Override
    protected void activateImpl() {
        // cursor was never an aim point here - firing at it would shoot wherever the mouse happened to sit
        if (!showReticuleOnActivation()) {
            onActivatedWithoutReticule();
            return;
        }

        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        Vector2f target = SkillshotUtils.getCursorWorldPosition();
        onSkillshotFired(target, Misc.getAngleInDegrees(fleet.getLocation(), target));
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        addTooltip(tooltip);
        addBlockedReasonsToTooltip(tooltip);
    }

    protected void addBlockedReasonsToTooltip(TooltipMakerAPI tooltip) {
        if (!showReticuleOnActivation()) return;

        float opad = 10f;

        if (SkillshotActivationManager.getInstanceOrRegister().hasActiveListener()) {
            tooltip.addPara("Conflicting ability in use!", opad, Misc.getNegativeHighlightColor());
        }

        boolean uiBlocking = Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null
                || Global.getSector().getCampaignUI().getCurrentCoreTab() != null;

        if (uiBlocking) {
            tooltip.addPara("Can only be activated from the ability bar.", opad, Misc.getNegativeHighlightColor());
        }
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    protected void applyEffect(float amount, float level) {
    }

    @Override
    protected void deactivateImpl() {
    }

    @Override
    protected void cleanupImpl() {
    }

    @Override
    public boolean showReticuleOnActivation() {
        return true;
    }
}
