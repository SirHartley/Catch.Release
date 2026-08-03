package catchrelease;

import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.spawner.BuriedMoteSpawner;
import catchrelease.campaign.ponds.listener.OnJumpPondSpawner;
import catchrelease.skillshot.SkillshotFramework;
import com.fs.starfarer.api.BaseModPlugin;

public class ModPlugin extends BaseModPlugin {
    public static final String MOD_ID = "catchrelease";

    /**
     * Runs once after the codex has been built, which is the only moment the fish category can be
     * added - the codex is generated at load and never rebuilt, so anything added later is invisible
     * and anything added earlier has no ROOT to hang off.
     */
    @Override
    public void onCodexDataGenerated() {
        super.onCodexDataGenerated();

        FishCodex.install();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        //Static fishing spots
        OnJumpPondSpawner.register();
        BuriedMoteSpawner.register();

        //data
        UpgradeManager.getInstance().updateBaseValues();
        SkillshotFramework.register();

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
