package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.HashMap;
import java.util.Map;

/**
 * Hosts the catch. Opening an interaction dialog pauses the campaign by itself, which is what holds
 * the drones still for the duration - no pausing of our own required.
 * <p>
 * The dialog is only the frame: it names the fish, hands a {@link FishingMinigamePanel} the middle of
 * the screen, and reports the result back to whoever opened it.
 * <p>
 * Runs as a custom <i>visual</i> dialog rather than a custom dialog, because that is the one without
 * buttons. showCustomDialog always builds a confirm button - its delegate can rename it and can drop
 * the cancel button beside it, but cannot ask for neither - and it wires enter and space straight to
 * it. showCustomVisualDialog hands the panel the whole frame and leaves the keyboard alone, which is
 * how vanilla hosts its own minigame in
 * {@link com.fs.starfarer.api.impl.campaign.eventide.DuelDialogDelegate}.
 */
public class FishingMinigameDialogPlugin implements InteractionDialogPlugin {

    public interface Callback {
        void onCatchResolved(boolean caught);
    }

    protected FishSpec fish;
    protected Callback callback;

    protected InteractionDialogAPI dialog;
    protected FishingMinigame minigame;
    protected Delegate delegate;
    protected boolean resolved = false;

    /** Set while the panel is already on its way out, so closing it again is not attempted. */
    transient protected boolean dismissed = false;

    /** Dev mode only - the line under the buttons showing what they have done. */
    transient protected LabelAPI devLabel;

    /**
     * Opens the catch on a fish, if the UI will have it.
     *
     * @param anchor what to run the dialog against - the pond, so the visuals have something to sit on
     * @return false if a dialog is already up or the UI is mid-transition, in which case the caller
     *         should try again rather than treat the fish as lost
     */
    public static boolean open(SectorEntityToken anchor, FishSpec fish, Callback callback) {
        if (fish == null) return false;

        FishingMinigameDialogPlugin plugin = new FishingMinigameDialogPlugin(fish, callback);

        return Global.getSector().getCampaignUI().showInteractionDialog(plugin, anchor);
    }

    public FishingMinigameDialogPlugin(FishSpec fish, Callback callback) {
        this.fish = fish;
        this.callback = callback;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.minigame = new FishingMinigame(fish);

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        //dialog.setOpacity(0.3f);
        dialog.setBackgroundDimAmount(0.05f);

        this.delegate = new Delegate();

        dialog.showCustomVisualDialog(FishConstants.MINIGAME_PANEL_WIDTH, FishConstants.MINIGAME_PANEL_HEIGHT,
                delegate);
    }

    /** Ends the dialog and tells the caller how it went. Safe to reach twice; it only reports once. */
    protected void resolve(boolean caught) {
        if (resolved) return;
        resolved = true;

        //the panel first, then the dialog behind it - unless the panel closing is what got us here
        if (!dismissed && delegate != null) delegate.dismissPanel();
        if (dialog != null) dialog.dismiss();
        if (callback != null) callback.onCatchResolved(caught);
    }

    /** Wraps the panel for the dialog. The catch ends itself, so there is nothing to press. */
    protected class Delegate implements CustomVisualDialogDelegate, FishingMinigamePanel.Listener {

        protected FishingMinigamePanel panel = new FishingMinigamePanel(minigame, this);

        /** The frame's own handle, and the only way to close the panel from in here. */
        protected DialogCallbacks callbacks;

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.callbacks = callbacks;

            addFramingElements(panel);

            if (Global.getSettings().isDevMode()) addDevControls(panel);
        }

        /**
         * Where the custom framing goes: titles, borders, portraits, anything that wants to be a real
         * UI element rather than something drawn.
         * <p>
         * Deliberately empty and deliberately separate. Elements added here take part in layout and
         * mouse-over as normal; anything that has to line up with the track itself is better drawn in
         * {@link FishingMinigamePanel#renderFrame}, which has the same layout the track uses.
         */
        protected void addFramingElements(CustomPanelAPI panel) {
        }

        /** Retunes the fish in front of you, so difficulty can be felt rather than guessed at. */
        protected void addDevControls(CustomPanelAPI panel) {
            TooltipMakerAPI element = panel.createUIElement(
                    FishConstants.MINIGAME_PANEL_WIDTH, FishConstants.MINIGAME_DEV_ROW_HEIGHT, false);

            devLabel = element.addPara(getDevStatusText(), Misc.getGrayColor(), 0f);
            element.addSpacer(6f);

            for (DevControl control : DevControl.values()) {
                element.addButton(control.label, control, FishConstants.MINIGAME_DEV_BUTTON_WIDTH,
                        FishConstants.MINIGAME_DEV_BUTTON_HEIGHT, 4f);
            }

            panel.addUIElement(element).inBL(8f, 8f);
        }

        @Override
        public CustomUIPanelPlugin getCustomPanelPlugin() {
            return panel;
        }

        /** No holographic wash over the playfield - the panel draws its own look. */
        @Override
        public float getNoiseAlpha() {
            return 0f;
        }

        /** Driven by the frame, so the dev readout keeps up whatever else is or is not advancing. */
        @Override
        public void advance(float amount) {
            updateDevLabel();
        }

        /** Closes the panel, which brings us back through {@link #reportDismissed(int)}. */
        protected void dismissPanel() {
            if (callbacks != null) callbacks.dismissDialog();
        }

        /**
         * The panel has gone, by whatever route - our own catch ending it, or the player pressing
         * escape. Resolving as a miss covers the second: the fish is gone with the panel, and the
         * drone holding that mote would otherwise wait on a callback that never comes.
         */
        @Override
        public void reportDismissed(int option) {
            dismissed = true;
            resolve(false);
        }

        @Override
        public void onMinigameEnded(boolean caught) {
            resolve(caught);
        }
    }

    /** Live readout of what the dev buttons have done to this fish. */
    protected String getDevStatusText() {
        if (minigame == null) return "";

        return String.format("difficulty %.0f   speed %.1f   %s",
                minigame.getDifficulty(), minigame.getMotionSpeed(), minigame.getMotion());
    }

    @Override
    public void advance(float amount) {
        updateDevLabel();
    }

    protected void updateDevLabel() {
        if (devLabel != null) devLabel.setText(getDevStatusText());
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return new HashMap<>();
    }
}
