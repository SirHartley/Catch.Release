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
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FishingMinigameDialogPlugin implements InteractionDialogPlugin {

    protected static final String CAMPAIGN_MUSIC_VOLUME_KEY = "campaignMusicVolumeMult";

    public interface Callback {

        void onCatchResolved(FishCatch landed);
    }

    protected FishSpec fish;
    protected Callback callback;


    protected SectorEntityToken anchor;


    protected SectorEntityToken catchTarget;


    protected FishLogEntry.Method method = FishLogEntry.Method.UNKNOWN;


    protected FishCatch specimen;

    protected InteractionDialogAPI dialog;
    protected FishingMinigame minigame;
    protected Delegate delegate;
    protected boolean resolved = false;


    transient protected boolean dismissed = false;


    transient protected float previousCampaignMusicVolume;
    transient protected boolean campaignMusicVolumeScoped = false;


    public static boolean open(SectorEntityToken anchor, FishSpec fish, FishLogEntry.Method method,
                               Callback callback) {
        return open(anchor, anchor, fish, method, callback);
    }


    public static boolean open(SectorEntityToken anchor, SectorEntityToken catchTarget,
                               FishSpec fish, FishLogEntry.Method method, Callback callback) {
        if (fish == null) return false;

        FishingMinigameDialogPlugin plugin = new FishingMinigameDialogPlugin(
                fish, anchor, catchTarget, method, callback);

        return showWithoutReplacingLocationMusic(plugin, anchor);
    }


    protected static boolean showWithoutReplacingLocationMusic(
            FishingMinigameDialogPlugin plugin, SectorEntityToken anchor) {
        if (anchor == null) {
            return Global.getSector().getCampaignUI().showInteractionDialog(plugin, null);
        }

        MemoryAPI memory = anchor.getMemoryWithoutUpdate();
        if (memory == null) {
            return Global.getSector().getCampaignUI().showInteractionDialog(plugin, anchor);
        }

        String key = MusicPlayerPluginImpl.KEEP_PLAYING_LOCATION_MUSIC_DURING_ENCOUNTER_MEM_KEY;
        boolean hadValue = memory.contains(key);
        Object previousValue = hadValue ? memory.get(key) : null;
        float previousExpiry = hadValue ? memory.getExpire(key) : -1f;

        memory.set(key, true);
        try {
            return Global.getSector().getCampaignUI().showInteractionDialog(plugin, anchor);
        } finally {
            if (!hadValue) {
                memory.unset(key);
            } else if (previousExpiry >= 0f) {
                memory.set(key, previousValue, previousExpiry);
            } else {
                memory.set(key, previousValue);
            }
        }
    }

    public FishingMinigameDialogPlugin(FishSpec fish, SectorEntityToken anchor,
                                       FishLogEntry.Method method, Callback callback) {
        this(fish, anchor, anchor, method, callback);
    }

    public FishingMinigameDialogPlugin(FishSpec fish, SectorEntityToken anchor,
                                       SectorEntityToken catchTarget,
                                       FishLogEntry.Method method, Callback callback) {
        this.fish = fish;
        this.anchor = anchor;
        this.catchTarget = catchTarget;
        this.method = method == null ? FishLogEntry.Method.UNKNOWN : method;
        this.callback = callback;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        // tackle sizes the window and rolls treasure; read once before anything is rolled
        Tackle tackle = TackleManager.get(method);
        this.minigame = new FishingMinigame(fish, tackle);

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

        // tackle that holds a specimen to its shape on the way up, applied to whatever the water and the stranger cap between them decided
        if (tackle.coherenceBonus > 0f) {
            aberration = Math.max(0f, aberration - tackle.coherenceBonus);
        }

        if (catchrelease.campaign.fish.fisherman.FishermanQuest.isQuestFish(catchTarget)) {
            aberration = 1f;
        }

        this.specimen = FishCatch.roll(fish, aberration, quality,
                anchor == null ? null : SectorRegion.of(anchor.getContainingLocation()));

        // must read off anchor before the catch resolves - the mote is gone afterward
        this.specimen.method = method;
        this.specimen.implement = CatchImplement.of(anchor);
        this.specimen.sourceId = getSourceId(anchor);
        // All landed cargo needs a catch time, not only the Fisherman's planted specimen: jobs can ask whether this exact water was worked after their agreement was made.
        this.specimen.caughtAt = Global.getSector().getClock().getTimestamp();
        catchrelease.campaign.fish.fisherman.FishermanQuest.markCatch(
                this.specimen, catchTarget);

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        dialog.setBackgroundDimAmount(0.05f);

        this.delegate = new Delegate();

        dialog.showCustomVisualDialog(FishConstants.MINIGAME_PANEL_WIDTH, FishConstants.MINIGAME_PANEL_HEIGHT,
                delegate);
        beginCampaignMusicVolumeScope();
    }


    protected void beginCampaignMusicVolumeScope() {
        if (campaignMusicVolumeScoped) return;

        previousCampaignMusicVolume = Global.getSettings().getFloat(CAMPAIGN_MUSIC_VOLUME_KEY);
        float requested = Math.max(0f, Math.min(1f,
                FishConstants.MINIGAME_CAMPAIGN_MUSIC_VOLUME_MULT));
        Global.getSettings().setFloat(CAMPAIGN_MUSIC_VOLUME_KEY, requested);
        campaignMusicVolumeScoped = true;
    }


    protected void restoreCampaignMusicVolume() {
        if (!campaignMusicVolumeScoped) return;

        campaignMusicVolumeScoped = false;
        Global.getSettings().setFloat(CAMPAIGN_MUSIC_VOLUME_KEY, previousCampaignMusicVolume);
    }


    protected static String getSourceId(SectorEntityToken anchor) {
        if (anchor == null) return null;

        if (anchor.getCustomPlugin() instanceof FishEntityPlugin) {
            SectorEntityToken pond = ((FishEntityPlugin) anchor.getCustomPlugin()).getPond();
            return pond == null ? null : pond.getId();
        }

        return MaskedFishingPondTerrainPlugin.getPondPlugin(anchor) == null
                ? null : anchor.getId();
    }


    protected void reopenWith(FishSpec pick) {
        if (pick == null || resolved) return;
        resolved = true;
        restoreCampaignMusicVolume();

        if (delegate != null) delegate.dismissPanel();
        if (dialog != null) dialog.dismiss();

        SectorEntityToken anchor = this.anchor;
        SectorEntityToken catchTarget = this.catchTarget;
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
                done = open(anchor, catchTarget, pick, method, callback) || patience <= 0f;
            }
        });
    }


    protected void resolve(boolean caught) {
        if (resolved) return;
        resolved = true;
        restoreCampaignMusicVolume();

        // panel closes before the dialog, unless panel-close is what triggered this
        if (!dismissed && delegate != null) delegate.dismissPanel();
        if (dialog != null) dialog.dismiss();
        if (callback != null) callback.onCatchResolved(caught ? specimen : null);
    }


    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin,
            FishingMinigamePanel.Listener {


        protected static final float DEV_BUTTON_WIDTH = 150f;
        protected static final float DEV_BUTTON_HEIGHT = 24f;
        protected static final float DEV_LIST_WIDTH = 220f;
        protected static final float DEV_GAP = 30f;

        protected FishingMinigamePanel panel = new FishingMinigamePanel(minigame, specimen, anchor, method, this);


        protected DialogCallbacks callbacks;


        protected CustomPanelAPI framePanel;


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


        protected void addFramingElements(CustomPanelAPI panel) {
            if (Global.getSettings().isDevMode()) addDevControls(panel);
        }


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
                element.addButton(spec.name == null || spec.name.isEmpty() ? spec.id : spec.name,
                        spec, DEV_LIST_WIDTH - 25f, 22f, 3f);
            }

            fishList.addUIElement(element).inTL(0f, 0f);
            framePanel.addComponent(fishList)
                    .inTL(-DEV_BUTTON_WIDTH - DEV_GAP - DEV_LIST_WIDTH - 10f, 0f);
        }


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


        @Override
        public float getNoiseAlpha() {
            return 0f;
        }

        @Override
        public void advance(float amount) {
        }


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


        protected void dismissPanel() {
            if (callbacks != null) callbacks.dismissDialog();
        }


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
        if (!resolved && campaignMusicVolumeScoped) {
            Global.getSector().getCampaignUI().suppressMusic(1f);
        } else {
            restoreCampaignMusicVolume();
        }
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
