package catchrelease;

import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.campaign.crime.CatchReleaseCampaignPlugin;
import catchrelease.campaign.crime.HarpoonPatrolResponse;
import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.coherence.CoherenceOverlayScript;
import catchrelease.campaign.fish.colony.AquariumTankScript;
import catchrelease.campaign.fish.colony.ConservatoryOptionProvider;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import catchrelease.campaign.fish.jobs.fleet.FleetQuestSpawner;
import catchrelease.campaign.fish.map.FishIntelPlanetPanel;
import catchrelease.campaign.fish.map.FishMapFilterScript;
import catchrelease.memory.charges.ChargeManager;
import catchrelease.campaign.fish.spawner.BuriedMoteSpawner;
import catchrelease.campaign.ponds.listener.OnJumpPondSpawner;
import catchrelease.skillshot.SkillshotFramework;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

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

        // static fishing spots
        OnJumpPondSpawner.register();
        BuriedMoteSpawner.register();
        ChargeManager.register();

        // pointing fishing gear at a fleet
        CatchReleaseCampaignPlugin.register();
        HarpoonPatrolResponse.register();

        // jobs offering fish for trade
        FleetQuestSpawner.register();

        // the wandering fisherman - transient watcher, the fleet itself is what persists
        FishermanSpawner.register();

        // the colony conservatory's doors and its tank
        ConservatoryOptionProvider.register();
        AquariumTankScript.register();

        // data
        UpgradeManager.getInstance().updateBaseValues();
        SkillshotFramework.register();

        // transient - a save should never carry a screen-watcher
        Global.getSector().addTransientScript(new FishMapFilterScript());
        Global.getSector().addTransientScript(new FishIntelPlanetPanel());
        Global.getSector().addTransientScript(new CoherenceOverlayScript());

        //Testing
        //LunaCampaignRenderer.addTransientRenderer(new TestMaskedWarpShaderRenderer());
        //LunaCampaignRenderer.addTransientRenderer(new TestStencilRenderer());
        //LunaCampaignRenderer.addTransientRenderer(new RippleRingRenderer());
    }

    @Override
    public void beforeGameSave() {
        super.beforeGameSave();
        SkillshotFramework.reset();
    }
}
