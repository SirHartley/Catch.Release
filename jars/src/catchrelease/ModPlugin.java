package catchrelease;

import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.campaign.crime.CatchReleaseCampaignPlugin;
import catchrelease.campaign.crime.HarpoonPatrolResponse;
import catchrelease.campaign.crime.LampPatrolResponse;
import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.legendary.LegendaryHaunt;
import catchrelease.campaign.fish.legendary.LonglinerDecoy;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.coherence.CoherenceOverlayScript;
import catchrelease.campaign.fish.colony.AquariumTankScript;
import catchrelease.campaign.fish.colony.ConservatoryOptionProvider;
import catchrelease.campaign.fish.fisherman.CoreFisherSpawner;
import catchrelease.campaign.fish.fisherman.FishermanQuest;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.campaign.fish.tutorial.RatingBarEvent;
import catchrelease.campaign.fish.tutorial.FishermanInterception;
import catchrelease.campaign.fish.tutorial.TutorialWreck;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import catchrelease.campaign.fish.jobs.fleet.FleetQuestSpawner;
import catchrelease.campaign.fish.jobs.fleet.CatchReleaseDistressProvider;
import catchrelease.campaign.fish.map.FishIntelPlanetPanel;
import catchrelease.campaign.fish.map.FishMapFilterScript;
import catchrelease.memory.charges.ChargeManager;
import catchrelease.campaign.fish.spawner.BuriedMoteSpawner;
import catchrelease.campaign.ponds.listener.OnJumpPondSpawner;
import catchrelease.rendering.spiral.BlackHoleSpiralWarp;
import catchrelease.distress.DistressCallFramework;
import catchrelease.skillshot.SkillshotFramework;
import catchrelease.testing.DevShortcut;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;

import java.util.LinkedHashSet;
import java.util.Set;

public class ModPlugin extends BaseModPlugin {

    public static final String MOD_ID = "catchrelease";

    @Override
    public void onCodexDataGenerated() {
        super.onCodexDataGenerated();

        FishCodex.install();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        FishingIntro.migrateLegacySkipSetting();

        OnJumpPondSpawner.register();
        BuriedMoteSpawner.register();
        ChargeManager.register();

        CatchReleaseCampaignPlugin.register();
        HarpoonPatrolResponse.register();
        LampPatrolResponse.register();

        FleetQuestSpawner.register();

        FishermanSpawner.register();
        CoreFisherSpawner.register();
        FishermanQuest.Keeper.register();

        TutorialWreck.Watcher.register();
        RatingBarEvent.VisitCounter.register();
        FishermanInterception.register();
        FishingIntro.Keeper.register();

        ConservatoryOptionProvider.register();
        AquariumTankScript.register();

        Aberration.Watcher.register();
        FishRanges.register();
        LegendaryHaunt.register();
        LonglinerDecoy.register();
        UpgradeManager.getInstance().updateBaseValues();
        CatchReleaseDistressProvider.register();
        DistressCallFramework.register();
        SkillshotFramework.register();

        // transient - a save should never carry a screen-watcher
        Global.getSector().addTransientScript(new FishMapFilterScript());
        Global.getSector().addTransientScript(new FishIntelPlanetPanel());
        Global.getSector().addTransientScript(new CoherenceOverlayScript());

        // housekeeping, once, before anything is looked at
        sweepPondClaims();
        FishLog.relockLegendaryRangeData();

        // an input listener rather than a script; inert unless dev mode is on
        DevShortcut.register();
    }

    protected void sweepPondClaims() {
        Set<String> known = new LinkedHashSet<>();
        known.add(TutorialConstants.TARGET_KEY);
        known.add(FishermanQuest.STATE_KEY);
        known.add(FishJob.REF_KEY);

        Set<String> live = new LinkedHashSet<>();
        if (FishingIntro.getTarget() != null) live.add(TutorialConstants.TARGET_KEY);
        if (FishermanQuest.getActive() != null) live.add(FishermanQuest.STATE_KEY);

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
            if (intel instanceof FishJob && !intel.isEnded()) {
                live.add(FishJob.REF_KEY);
                break;
            }
        }

        QuestPond.sweep(known, live);
    }

    @Override
    public void beforeGameSave() {
        super.beforeGameSave();
        SkillshotFramework.reset();
    }
}
