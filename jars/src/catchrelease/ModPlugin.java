package catchrelease;

import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.campaign.crime.CatchReleaseCampaignPlugin;
import catchrelease.campaign.crime.HarpoonPatrolResponse;
import catchrelease.campaign.crime.LampPatrolResponse;
import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.coherence.CoherenceOverlayScript;
import catchrelease.campaign.fish.colony.AquariumTankScript;
import catchrelease.campaign.fish.colony.ConservatoryOptionProvider;
import catchrelease.campaign.fish.fisherman.CoreFisherSpawner;
import catchrelease.campaign.fish.fisherman.FishermanQuest;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.tutorial.Castaway;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.campaign.fish.tutorial.RatingBarEvent;
import catchrelease.campaign.fish.tutorial.FishermanInterception;
import catchrelease.campaign.fish.tutorial.TutorialWreck;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import catchrelease.campaign.fish.jobs.fleet.FleetQuestSpawner;
import catchrelease.campaign.fish.map.FishIntelPlanetPanel;
import catchrelease.campaign.fish.map.FishMapFilterScript;
import catchrelease.memory.charges.ChargeManager;
import catchrelease.campaign.fish.spawner.BuriedMoteSpawner;
import catchrelease.campaign.ponds.listener.OnJumpPondSpawner;
import catchrelease.skillshot.SkillshotFramework;
import catchrelease.testing.DevShortcut;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;

import java.util.LinkedHashSet;
import java.util.Set;

public class ModPlugin extends BaseModPlugin {
    public static final String MOD_ID = "catchrelease";

    /**
     * The only moment the fish codex category can be added - the codex is generated at load and
     * never rebuilt, so earlier has no ROOT to hang off and later is invisible.
     */
    @Override
    public void onCodexDataGenerated() {
        super.onCodexDataGenerated();

        FishCodex.install();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        //The dev-era ability-bar shop is gone; Fishermen and conservatories own the two doors.
        FishingIntro.removeLegacyOutfitterAbility();

        // static fishing spots
        OnJumpPondSpawner.register();
        BuriedMoteSpawner.register();
        ChargeManager.register();

        // pointing fishing gear at a fleet
        CatchReleaseCampaignPlugin.register();
        HarpoonPatrolResponse.register();
        LampPatrolResponse.register();

        // jobs offering fish for trade
        FleetQuestSpawner.register();

        // the fishing trade - transient watchers, the fleets themselves are what persist
        FishermanSpawner.register();
        CoreFisherSpawner.register();
        FishermanQuest.Keeper.register();

        // how anybody comes to be fishing at all - four hooks, none of them required
        TutorialWreck.Watcher.register();
        Castaway.Watcher.register();
        RatingBarEvent.VisitCounter.register();
        FishermanInterception.register();
        FishingIntro.Keeper.register();

        // the colony conservatory's doors and its tank
        ConservatoryOptionProvider.register();
        AquariumTankScript.register();

        // data - the aberration index fills on arriving somewhere and on opening the sector map
        Aberration.Watcher.register();
        UpgradeManager.getInstance().updateBaseValues();
        SkillshotFramework.register();

        // transient - a save should never carry a screen-watcher
        Global.getSector().addTransientScript(new FishMapFilterScript());
        Global.getSector().addTransientScript(new FishIntelPlanetPanel());
        Global.getSector().addTransientScript(new CoherenceOverlayScript());

        //housekeeping, once, before anything is looked at
        sweepPondClaims();

        //an input listener rather than a script; inert unless dev mode is on
        DevShortcut.register();

        //Testing
        //LunaCampaignRenderer.addTransientRenderer(new TestMaskedWarpShaderRenderer());
        //LunaCampaignRenderer.addTransientRenderer(new TestStencilRenderer());
        //LunaCampaignRenderer.addTransientRenderer(new RippleRingRenderer());
    }

    /**
     * Takes the mission marker off every rupture nothing is using any more.
     * <p>
     * Four things claim ruptures and each of them lets go on its own transitions, which is enough
     * from here on and cannot repair a save that already has a marker burned into it - see
     * {@link QuestPond#sweep}. The list of claimants lives here rather than in {@code QuestPond},
     * because knowing who claims what is wiring and wiring is what this class is.
     * <p>
     * One entity walk per system, on load. The three ids are all of them.
     */
    protected void sweepPondClaims() {
        Set<String> known = new LinkedHashSet<>();
        known.add(TutorialConstants.TARGET_KEY);
        known.add(FishermanQuest.STATE_KEY);
        known.add(FishJob.REF_KEY);

        Set<String> live = new LinkedHashSet<>();
        if (FishingIntro.getTarget() != null) live.add(TutorialConstants.TARGET_KEY);
        if (FishermanQuest.getActive() != null) live.add(FishermanQuest.STATE_KEY);

        //every bar job shares the one reason, so one unfinished job holds the whole id
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
