package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.HashMap;
import java.util.List;
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
        /**
         * @param landed the specimen that was taken, or null if it got away. The same object the
         *               readout showed, so what goes in the hold is what the player was told they
         *               caught rather than a second roll of the same species.
         */
        void onCatchResolved(FishCatch landed);
    }

    protected FishSpec fish;
    protected Callback callback;

    /** Where this one was taken. Its aberration is read off it early, and the log reads it on a win. */
    protected SectorEntityToken anchor;

    /** How it is being taken. Recorded in the log, since the three ways are not interchangeable. */
    protected FishLogEntry.Method method = FishLogEntry.Method.UNKNOWN;

    /**
     * Rolled when the catch opens rather than when it is won.
     * <p>
     * The readout has to show the specimen while the dialog is still up, and the caller has to store
     * that same specimen after it closes. One roll, held here, is the only way both can be true.
     */
    protected FishCatch specimen;

    protected InteractionDialogAPI dialog;
    protected FishingMinigame minigame;
    protected Delegate delegate;
    protected boolean resolved = false;

    /** Set while the panel is already on its way out, so closing it again is not attempted. */
    transient protected boolean dismissed = false;

    /**
     * Opens the catch on a fish, if the UI will have it.
     *
     * @param anchor what to run the dialog against - the pond, so the visuals have something to sit on
     * @return false if a dialog is already up or the UI is mid-transition, in which case the caller
     *         should try again rather than treat the fish as lost
     */
    public static boolean open(SectorEntityToken anchor, FishSpec fish, FishLogEntry.Method method,
                               Callback callback) {
        if (fish == null) return false;

        FishingMinigameDialogPlugin plugin = new FishingMinigameDialogPlugin(fish, anchor, method, callback);

        return Global.getSector().getCampaignUI().showInteractionDialog(plugin, anchor);
    }

    public FishingMinigameDialogPlugin(FishSpec fish, SectorEntityToken anchor,
                                       FishLogEntry.Method method, Callback callback) {
        this.fish = fish;
        this.anchor = anchor;
        this.method = method == null ? FishLogEntry.Method.UNKNOWN : method;
        this.callback = callback;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        //whatever is fitted to the rig this was hooked on. Read once, before anything is rolled -
        //and handed to the catch on the way in, since it is what sizes the window and rolls the
        //treasure rather than something the catch can be told about afterwards
        Tackle tackle = TackleManager.get(method);
        this.minigame = new FishingMinigame(fish, tackle);

        //how loosely it holds to reality comes from where it was taken, not from the fish
        this.specimen = FishCatch.roll(fish, Aberration.of(anchor), tackle.qualityBias,
                anchor == null ? null : SectorRegion.of(anchor.getContainingLocation()));

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        //dialog.setOpacity(0.3f);
        dialog.setBackgroundDimAmount(0.05f);

        this.delegate = new Delegate();

        dialog.showCustomVisualDialog(FishConstants.MINIGAME_PANEL_WIDTH, FishConstants.MINIGAME_PANEL_HEIGHT,
                delegate);
    }

    /**
     * Dev mode's fish swap. The fish cannot be changed under a running catch - it is fixed in three
     * constructors with no setter - so the whole dialog is torn down and reopened on the picked
     * spec, through the same entry point everything else opens it through.
     * <p>
     * Marked resolved before the teardown so the callback stays quiet about it: the caller is owed
     * exactly one resolution, and the reopened catch - carrying the same callback - is what pays
     * it. The reopen retries every frame because the UI refuses a new dialog while the old one is
     * still on its way out; if it still refuses after a few seconds, the fish and its resolution
     * are let go rather than retried forever.
     */
    protected void reopenWith(FishSpec pick) {
        if (pick == null || resolved) return;
        resolved = true;

        if (delegate != null) delegate.dismissPanel();
        if (dialog != null) dialog.dismiss();

        SectorEntityToken anchor = this.anchor;
        FishLogEntry.Method method = this.method;
        Callback callback = this.callback;

        Global.getSector().addTransientScript(new EveryFrameScript() {
            protected float patience = 5f;
            protected boolean done = false;

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public boolean runWhilePaused() {
                return true;
            }

            @Override
            public void advance(float amount) {
                if (done) return;

                patience -= amount;
                done = open(anchor, pick, method, callback) || patience <= 0f;
            }
        });
    }

    /** Ends the dialog and tells the caller how it went. Safe to reach twice; it only reports once. */
    protected void resolve(boolean caught) {
        if (resolved) return;
        resolved = true;

        //the panel first, then the dialog behind it - unless the panel closing is what got us here
        if (!dismissed && delegate != null) delegate.dismissPanel();
        if (dialog != null) dialog.dismiss();
        if (callback != null) callback.onCatchResolved(caught ? specimen : null);
    }

    /**
     * Wraps the panel for the dialog. The catch ends itself, so there is nothing to press - except
     * in dev mode, where a strip of cheat buttons hangs off the panel's edge. Those buttons live on
     * child panels that name this delegate as their {@link CustomUIPanelPlugin}, which is the only
     * reason this implements it: presses land in {@link #buttonPressed}, and the rest of the
     * interface stays empty.
     */
    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin,
            FishingMinigamePanel.Listener {

        /** Dev strip sizing. Sized to its labels, not to the layout - it is scaffolding. */
        protected static final float DEV_BUTTON_WIDTH = 150f;
        protected static final float DEV_BUTTON_HEIGHT = 24f;
        protected static final float DEV_LIST_WIDTH = 220f;
        protected static final float DEV_GAP = 30f;

        protected FishingMinigamePanel panel = new FishingMinigamePanel(minigame, specimen, anchor, method, this);

        /** The frame's own handle, and the only way to close the panel from in here. */
        protected DialogCallbacks callbacks;

        /** The dialog's own panel, kept so the dev fish list can be put up and taken down again. */
        protected CustomPanelAPI framePanel;

        /** The dev species list while it is up, by the reference that can take it off again. */
        protected CustomPanelAPI fishList;

        protected final Object devWinId = new Object();
        protected final Object devWinTreasureId = new Object();
        protected final Object devTreasureId = new Object();
        protected final Object devFishId = new Object();

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.callbacks = callbacks;
            this.framePanel = panel;

            addFramingElements(panel);
        }

        /**
         * Where the custom framing goes: titles, borders, portraits, anything that wants to be a real
         * UI element rather than something drawn.
         * <p>
         * Deliberately empty - dev mode's cheat strip excepted - and deliberately separate. Elements
         * added here take part in layout and mouse-over as normal; anything that has to line up with
         * the track itself is better drawn in {@link FishingMinigamePanel#renderFrame}, which has the
         * same layout the track uses.
         */
        protected void addFramingElements(CustomPanelAPI panel) {
            if (Global.getSettings().isDevMode()) addDevControls(panel);
        }

        /**
         * Dev mode's cheat strip, hung off the panel's left edge - the panel itself is cut to the
         * playfield, so there is no room inside it, and children take input fine from outside their
         * parent's rectangle since event dispatch never culls by bounds. The landed-fish cards can
         * end up drawn over it, which is fine: by then the strip has done its job.
         */
        protected void addDevControls(CustomPanelAPI panel) {
            float height = (DEV_BUTTON_HEIGHT + 4f) * 4f + 4f;

            CustomPanelAPI strip = panel.createCustomPanel(DEV_BUTTON_WIDTH, height, this);
            TooltipMakerAPI element = strip.createUIElement(DEV_BUTTON_WIDTH, height, false);

            element.addButton("Win", devWinId, DEV_BUTTON_WIDTH, DEV_BUTTON_HEIGHT, 4f);
            element.addButton("Win with treasure", devWinTreasureId,
                    DEV_BUTTON_WIDTH, DEV_BUTTON_HEIGHT, 4f);
            element.addButton("Spawn treasure", devTreasureId,
                    DEV_BUTTON_WIDTH, DEV_BUTTON_HEIGHT, 4f);
            element.addButton("Spawn fish...", devFishId,
                    DEV_BUTTON_WIDTH, DEV_BUTTON_HEIGHT, 4f);

            strip.addUIElement(element).inTL(0f, 0f);
            panel.addComponent(strip).inTL(-DEV_BUTTON_WIDTH - DEV_GAP, 0f);
        }

        /**
         * The species list, beside the strip: one button per row of the fish table, straight off
         * the loader. The strip's button toggles it - pressed again, the list goes away unpicked.
         */
        protected void toggleFishList() {
            if (fishList != null) {
                framePanel.removeComponent(fishList);
                fishList = null;
                return;
            }

            List<FishSpec> specs = FishSpecLoader.getAllFishSpecs();
            if (specs.isEmpty() || framePanel == null) return;

            float height = FishConstants.MINIGAME_PANEL_HEIGHT;

            fishList = framePanel.createCustomPanel(DEV_LIST_WIDTH, height, this);
            TooltipMakerAPI element = fishList.createUIElement(DEV_LIST_WIDTH, height, true);

            for (FishSpec spec : specs) {
                //the spec itself is the button's id, so a press says which fish with no second map
                element.addButton(spec.name == null || spec.name.isEmpty() ? spec.id : spec.name,
                        spec, DEV_LIST_WIDTH - 25f, 22f, 3f);
            }

            fishList.addUIElement(element).inTL(0f, 0f);
            framePanel.addComponent(fishList)
                    .inTL(-DEV_BUTTON_WIDTH - DEV_GAP - DEV_LIST_WIDTH - 10f, 0f);
        }

        /** The dev strip's presses. Nothing else in the dialog has a button to press. */
        @Override
        public void buttonPressed(Object buttonId) {
            if (buttonId == devWinId) {
                minigame.setCaught();
            } else if (buttonId == devWinTreasureId) {
                minigame.devTakeTreasure();
                minigame.setCaught();
            } else if (buttonId == devTreasureId) {
                minigame.devSpawnTreasure();
            } else if (buttonId == devFishId) {
                toggleFishList();
            } else if (buttonId instanceof FishSpec) {
                reopenWith((FishSpec) buttonId);
            }
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

        @Override
        public void advance(float amount) {
        }

        /** The rest of {@link CustomUIPanelPlugin} - the dev strip draws and moves nothing itself. */
        @Override
        public void positionChanged(PositionAPI position) {
        }

        @Override
        public void renderBelow(float alphaMult) {
        }

        @Override
        public void render(float alphaMult) {
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
        }

        /** Closes the panel, which brings us back through {@link #reportDismissed(int)}. */
        protected void dismissPanel() {
            if (callbacks != null) callbacks.dismissDialog();
        }

        /**
         * The panel has gone, by whatever route - our own close, or the player pressing escape, or
         * the frame deciding to dismiss itself.
         * <p>
         * The outcome is read off the catch rather than assumed, because the route here is not
         * knowable from in here. A fish that was landed is landed however the panel came down; one
         * that was still being played is lost. Getting this wrong either way leaves the drone or the
         * line holding that mote waiting on a callback that never comes.
         */
        @Override
        public void reportDismissed(int option) {
            dismissed = true;
            resolve(minigame != null && minigame.isCaught());
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
