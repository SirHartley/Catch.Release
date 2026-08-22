package catchrelease.skillshot.input;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventType;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.input.Keyboard;
import catchrelease.skillshot.SkillshotFramework;
import catchrelease.skillshot.SkillshotSettings;
import catchrelease.skillshot.ability.SkillshotAbility;
import catchrelease.skillshot.render.SkillshotRenderer;

import java.util.List;

public class OnKeyPressSkillshotListener implements SkillshotInputListener, CampaignInputListener {

    public static final int FIRST_SLOT_KEY = Keyboard.KEY_1;
    public static final int LAST_SLOT_KEY = Keyboard.KEY_9;

    protected int heldSlotKey = -1;
    protected boolean active = false;
    protected SkillshotRenderer renderer;

    public static OnKeyPressSkillshotListener getInstanceOrRegister() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();

        if (!manager.hasListenerOfClass(OnKeyPressSkillshotListener.class)) {
            OnKeyPressSkillshotListener listener = new OnKeyPressSkillshotListener();
            manager.addListener(listener, false);
            return listener;
        }

        return manager.getListeners(OnKeyPressSkillshotListener.class).get(0);
    }

    @Override
    public int getListenerInputPriority() {
        return 0;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        // ctrl is the vanilla "reassign ability slot" modifier - stay out of its way
        boolean ctrlPressed = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null || ctrlPressed) {
            reset();
            return;
        }

        for (InputEventAPI input : events) {
            if (input.isConsumed()) continue;

            if (input.getEventType().equals(InputEventType.KEY_DOWN)) {
                // Already aiming: a repeat/macro re-sent KEY_DOWN. Consume it so it can't become a button press, but keep scanning - the matching KEY_UP may be later this frame.
                if (isActive()) {
                    if (input.getEventValue() == heldSlotKey) input.consume();
                    continue;
                }

                onSlotKeyDown(input);
            } else if (input.getEventType().equals(InputEventType.KEY_UP)) {
                if (isActive() && input.getEventValue() == heldSlotKey) onSlotKeyUp(input);
            }
        }
    }

    protected void onSlotKeyDown(InputEventAPI input) {
        int keyValue = input.getEventValue();
        if (keyValue < FIRST_SLOT_KEY || keyValue > LAST_SLOT_KEY) return;

        SkillshotAbility ability = getSlottedSkillshotAbility(keyValue - FIRST_SLOT_KEY);
        if (ability == null || !ability.isUsable()) return;

        // Leave unconsumed so the UI turns it into an ordinary button press and activates vanilla-style.
        if (!ability.showReticuleOnActivation()) return;

        SkillshotFramework.log("Starting hotkey targeting for " + ability.getId());

        active = true;
        heldSlotKey = keyValue;
        SkillshotActivationManager.getInstanceOrRegister().setCurrentListener(this);

        renderer = ability.createReticule();
        LunaCampaignRenderer.addRenderer(renderer);

        input.consume();
    }

    protected void onSlotKeyUp(InputEventAPI input) {
        SkillshotAbility ability = getSlottedSkillshotAbility(heldSlotKey - FIRST_SLOT_KEY);

        if (ability == null) {
            reset();
            return;
        }

        if (!renderer.isValidPosition()) {
            Global.getSoundPlayer().playUISound(SkillshotSettings.SOUND_INVALID_TARGET, 1f, 1f);
            reset();
            return;
        }

        SkillshotFramework.log("Firing skillshot on key release: " + ability.getId());

        ability.forceActivation();
        ability.setCooldownLeft(ability.getSpec().getDeactivationCooldown());

        active = false;
        heldSlotKey = -1;
        renderer.setDone();
        renderer = null;

        input.consume();
    }

    protected SkillshotAbility getSlottedSkillshotAbility(int slotIndex) {
        List<PersistentUIDataAPI.AbilitySlotAPI> slots = Global.getSector().getUIData().getAbilitySlotsAPI().getCurrSlotsCopy();
        if (slotIndex < 0 || slotIndex >= slots.size()) return null;

        PersistentUIDataAPI.AbilitySlotAPI slot = slots.get(slotIndex);

        // The bar swaps in a different ability per slot while in hyperspace.
        String abilityId = Global.getSector().getPlayerFleet().isInHyperspace() ? slot.getInHyperAbilityId() : slot.getAbilityId();
        if (abilityId == null || abilityId.isEmpty()) return null;

        if (!Global.getSettings().getAbilitySpec(abilityId).hasTag(SkillshotSettings.TAG_SKILLSHOT)) return null;

        AbilityPlugin plugin = Global.getSector().getPlayerFleet().getAbility(abilityId);
        if (!(plugin instanceof SkillshotAbility)) return null;

        return (SkillshotAbility) plugin;
    }

    @Override
    public void reset() {
        active = false;
        heldSlotKey = -1;
        if (renderer != null) renderer.setDone();
        renderer = null;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {
    }
}
