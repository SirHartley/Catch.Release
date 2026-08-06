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

/**
 * Base class for a skillshot ability. Subclasses implement {@link SkillshotAbility#createReticule()}
 * and {@link #onSkillshotFired(Vector2f, float)}. Wire up in abilities.csv: subclass in "plugin",
 * {@link catchrelease.skillshot.SkillshotSettings#TAG_SKILLSHOT} in "tags"; "deactivationCooldown"
 * becomes the framework-applied rearm time.
 * <p>
 * Override {@link #onConsume()} for consumables. Override {@link #showReticuleOnActivation()} to
 * return false for an ability that isn't always aimed - the framework then steps aside and the press
 * lands in {@link #onActivatedWithoutReticule()} instead.
 */
public abstract class BaseSkillshotAbility extends BaseDurationAbility implements SkillshotAbility {

    /**
     * The payload. Called once the player has committed to an aim point the reticule accepted.
     *
     * @param worldTarget    cursor position in campaign world coordinates
     * @param angleFromFleet degrees from the player fleet to that position
     */
    protected abstract void onSkillshotFired(Vector2f worldTarget, float angleFromFleet);

    /** Runs right after a successful shot. Default does nothing - override to consume an item,
     *  spend a charge, etc. */
    protected void onConsume() {
    }

    /** Ability-specific tooltip body. The framework appends its own "can't fire right now" lines. */
    public abstract void addTooltip(TooltipMakerAPI tooltip);

    /** Runs in place of {@link #onSkillshotFired(Vector2f, float)} when
     *  {@link #showReticuleOnActivation()} is false (vanilla activation, no targeting session).
     *  Default does nothing. */
    protected void onActivatedWithoutReticule() {
    }

    @Override
    public boolean isUsable() {
        //no targeting session to block - usable wherever a vanilla ability would be, including core UI tabs
        if (!showReticuleOnActivation()) return super.isUsable();

        return super.isUsable() && !isTargetingBlocked();
    }

    /** True while a skillshot can't start: another ability is already aiming, or the player is in a
     *  dialog or core UI tab (reticule would render over the wrong thing, click would never reach us).
     *  Only consulted while {@link #showReticuleOnActivation()} is true. */
    public boolean isTargetingBlocked() {
        if (SkillshotActivationManager.getInstanceOrRegister().hasActiveListener()) return true;

        return Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null
                || Global.getSector().getCampaignUI().getCurrentCoreTab() != null;
    }

    @Override
    public boolean isActiveOrInProgress() {
        return super.isActiveOrInProgress() || turnedOn;
    }

    /** Click-path entry point. When a reticule is wanted, OnKeyPressSkillshotListener consumes the
     *  keypress before it becomes a button press (so it can hold the reticule open until key-up);
     *  otherwise the hotkey arrives here as an ordinary button press. */
    @Override
    public void pressButton() {
        //no reticule wanted - hand off to vanilla activation (it plays its own sound)
        if (!showReticuleOnActivation()) {
            super.pressButton();
            return;
        }

        if (!isUsable() || turnedOn) return;
        if (entity == null || !entity.isPlayerFleet()) return;

        playActivationSound();

        final SkillshotAbility self = this;
        SkillshotFramework.log("Starting click targeting for " + getId());

        //delayed one frame - registering the listener now would let the UI's own priority-0 click
        //handling eat this very click
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

    /** Fires without going through {@link #activate()}, which would trigger on the button press
     *  the targeting session was set up to avoid. */
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
        //cursor was never an aim point here - firing at it would shoot wherever the mouse happened to sit
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
