package catchrelease;

import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.campaign.fish.spawner.BuriedMoteSpawner;
import catchrelease.campaign.ponds.listener.OnJumpPondSpawner;
import catchrelease.skillshot.SkillshotFramework;
import com.fs.starfarer.api.BaseModPlugin;

public class ModPlugin extends BaseModPlugin {
    public static final String MOD_ID = "catchrelease";

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
