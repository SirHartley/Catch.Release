package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
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
 * Hosts the catch minigame. Opening an interaction dialog pauses the campaign by itself, holding
 * drones still with no pausing of our own; the dialog itself is just a frame around
 * {@link FishingMinigamePanel} that reports the result back to the caller.
 * <p>
 * Uses {@code showCustomVisualDialog} rather than {@code showCustomDialog}: the latter always wires
 * a confirm button (plus enter/space) with no way to have neither. The former hands over the whole
 * frame and leaves input alone, same as vanilla's
 * {@link com.fs.starfarer.api.impl.campaign.eventide.DuelDialogDelegate}.
 */
public class FishingMinigameDialogPlugin implements InteractionDialogPlugin {

    public interface Callback {
        /** @param landed specimen taken, or null if it got away - same object the readout showed */
        void onCatchResolved(FishCatch landed);
    }

    protected FishSpec fish;
    protected Callback callback;

    /** Where this catch was taken; source of aberration, and read by the log on a win. */
    protected SectorEntityToken anchor;

    /** How it's being taken; recorded in the log, since the three methods aren't interchangeable. */
    protected FishLogEntry.Method method = FishLogEntry.Method.UNKNOWN;

    /**
     * Rolled when the catch opens, not when it's won - the readout shows this while the dialog is
     * up, and the caller stores the same object after it closes.
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
     * @param anchor entity to run the dialog against (the pond)
     * @return false if a dialog is already up or the UI is mid-transition; caller should retry
     *         rather than treat the fish as lost
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
        // tackle sizes the window and rolls treasure; read once before anything is rolled
        Tackle tackle = TackleManager.get(method);
        this.minigame = new FishingMinigame(fish, tackle);

        // aberration comes from where it was taken, not from the fish species - unless this is
        // a rumored stranger, which is a prize specimen wherever it is: quality floored high and
        // coherence capped stable, over whatever the water and tackle would have said
        boolean stranger = fish != null && anchor != null && fish.id.equals(
                catchrelease.campaign.fish.fisherman.FishRumors.getStrangerId(
                        anchor.getContainingLocation()));

        float quality = stranger
                ? Math.max(tackle.qualityBias,
                        catchrelease.campaign.fish.fisherman.FishermanConstants.STRANGER_QUALITY_FLOOR)
                : tackle.qualityBias;
        float aberration = stranger
                ? Math.min(Aberration.of(anchor),
                        catchrelease.campaign.fish.fisherman.FishermanConstants.STRANGER_MAX_ABERRATION)
                : Aberration.of(anchor);

        //tackle that holds a specimen to its shape on the way up, applied to whatever the water
        //and the stranger cap between them decided
        if (tackle.coherenceBonus > 0f) {
            aberration = Math.max(0f, aberration - tackle.coherenceBonus);
        }

        //a chart request is a question about the water rather than about the animal, so what the
        //trade planted always comes up barely holding whatever the local reading would have said -
        //and whatever was on the rig, which is why this is last
        if (catchrelease.campaign.fish.fisherman.FishermanQuest.isQuestFish(anchor)) {
            aberration = 1f;
        }

        this.specimen = FishCatch.roll(fish, aberration, quality,
                anchor == null ? null : SectorRegion.of(anchor.getContainingLocation()));

        // must read off anchor before the catch resolves - the mote is gone afterward
        this.specimen.method = method;
        this.specimen.implement = CatchImplement.of(anchor);
        this.specimen.sourceId = getSourceId(anchor);

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        //dialog.setOpacity(0.3f);
        dialog.setBackgroundDimAmount(0.05f);

        this.delegate = new Delegate();

        dialog.showCustomVisualDialog(FishConstants.MINIGAME_PANEL_WIDTH, FishConstants.MINIGAME_PANEL_HEIGHT,
                delegate);
    }

    /** Resolves both drone catches (anchor is the pond) and harpoon catches (anchor is the mote). */
    protected static String getSourceId(SectorEntityToken anchor) {
        if (anchor == null) return null;

        if (anchor.getCustomPlugin() instanceof FishEntityPlugin) {
            SectorEntityToken pond = ((FishEntityPlugin) anchor.getCustomPlugin()).getPond();
            return pond == null ? null : pond.getId();
        }

        return MaskedFishingPondTerrainPlugin.getPondPlugin(anchor) == null
                ? null : anchor.getId();
    }

    /**
     * Dev mode's fish swap. The fish is fixed with no setter, so this tears down the dialog and
     * reopens on the picked spec through {@link #open}. Marked resolved before teardown so the
     * callback still fires exactly once, via the reopened dialog. Retries every frame since the UI
     * refuses a new dialog while the old one is still closing; gives up after a few seconds.
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

        // panel closes before the dialog, unless panel-close is what triggered this
        if (!dismissed && delegate != null) delegate.dismissPanel();
        if (dialog != null) dialog.dismiss();
        if (callback != null) callback.onCatchResolved(caught ? specimen : null);
    }

    /**
     * Wraps the panel for the dialog. Nothing to press except dev mode's cheat strip, whose buttons
     * live on child panels naming this delegate as their {@link CustomUIPanelPlugin} - presses land
     * in {@link #buttonPressed}, and the rest of the interface stays empty.
     */
    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin,
            FishingMinigamePanel.Listener {

        /** Dev strip sizing; sized to labels, not to layout. */
        protected static final float DEV_BUTTON_WIDTH = 150f;
        protected static final float DEV_BUTTON_HEIGHT = 24f;
        protected static final float DEV_LIST_WIDTH = 220f;
        protected static final float DEV_GAP = 30f;

        protected FishingMinigamePanel panel = new FishingMinigamePanel(minigame, specimen, anchor, method, this);

        /** Frame handle; only way to close the panel from here. */
        protected DialogCallbacks callbacks;

        /** Kept so the dev fish list can be added and removed again. */
        protected CustomPanelAPI framePanel;

        /** Dev species list panel, while shown. */
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
         * Custom framing (titles, borders, portraits) that should be real UI elements, not drawn.
         * Empty apart from dev mode; anything that must line up with the track is drawn instead in
         * {@link FishingMinigamePanel#renderFrame}, which shares its layout.
         */
        protected void addFramingElements(CustomPanelAPI panel) {
            if (Global.getSettings().isDevMode()) addDevControls(panel);
        }

        /**
         * Dev cheat strip off the panel's left edge - outside the panel's own rect, which works fine
         * since input dispatch isn't culled by bounds. Landed-fish cards may draw over it later.
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

        /** Species list beside the strip, one button per fish spec; strip button toggles it. */
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
                // spec itself is the button id
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

        /** Rest of {@link CustomUIPanelPlugin} - dev strip draws and moves nothing itself. */
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
         * The panel closed by whatever route (our own close, escape, frame dismiss). Outcome is read
         * off the minigame state rather than assumed, since the route isn't knowable from here and
         * the caller is waiting on exactly one callback.
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
