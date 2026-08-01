package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.HashMap;
import java.util.Map;

/**
 * Hosts the catch. Opening an interaction dialog pauses the campaign by itself, which is what holds
 * the drones still for the duration - no pausing of our own required.
 * <p>
 * The dialog is only the frame: it names the fish, hands a {@link FishingMinigamePanel} the middle of
 * the screen, and reports the result back to whoever opened it.
 */
public class FishingMinigameDialogPlugin implements InteractionDialogPlugin {

    public interface Callback {
        void onCatchResolved(boolean caught);
    }

    protected FishSpec fish;
    protected Callback callback;

    protected InteractionDialogAPI dialog;
    protected FishingMinigame minigame;
    protected boolean resolved = false;

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

        dialog.getTextPanel().addPara("Something takes the line: " + fish.getDisplayName() + ".");
        dialog.getTextPanel().addPara("Hold the left mouse button to reel in. Keep it in the bracket.",
                Misc.getGrayColor());

        dialog.showCustomDialog(FishConstants.MINIGAME_PANEL_WIDTH, FishConstants.MINIGAME_PANEL_HEIGHT,
                new Delegate());
    }

    /** Ends the dialog and tells the caller how it went. Safe to reach twice; it only reports once. */
    protected void resolve(boolean caught) {
        if (resolved) return;
        resolved = true;

        if (dialog != null) dialog.dismiss();
        if (callback != null) callback.onCatchResolved(caught);
    }

    /** Wraps the panel for the dialog. The catch ends itself, so neither button is offered. */
    protected class Delegate extends BaseCustomDialogDelegate implements FishingMinigamePanel.Listener {

        protected FishingMinigamePanel panel = new FishingMinigamePanel(minigame, this);

        @Override
        public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        }

        @Override
        public CustomUIPanelPlugin getCustomPanelPlugin() {
            return panel;
        }

        @Override
        public boolean hasCancelButton() {
            return true;
        }

        @Override
        public String getCancelText() {
            return "Cut the line";
        }

        @Override
        public String getConfirmText() {
            return null;
        }

        /** Escape, or cutting the line on purpose - either way the fish is gone. */
        @Override
        public void customDialogCancel() {
            resolve(false);
        }

        @Override
        public void onMinigameEnded(boolean caught) {
            resolve(caught);
        }
    }

    @Override
    public void advance(float amount) {
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
