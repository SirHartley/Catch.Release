package catchrelease;

import catchrelease.memory.upgrades.UpgradeManager;
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

        //skillshot abilities - installs the hotkey listener. Without it nothing intercepts the
        //ability bar keys, so holding one to aim never starts a hotkey session
        SkillshotFramework.register();

        //data
        UpgradeManager.getInstance().updateBaseValues();

        //Testing
        //LunaCampaignRenderer.addTransientRenderer(new TestMaskedWarpShaderRenderer());
        //LunaCampaignRenderer.addTransientRenderer(new TestStencilRenderer());
        //LunaCampaignRenderer.addTransientRenderer(new RippleRingRenderer());
    }

    @Override
    public void beforeGameSave() {
        super.beforeGameSave();

        //so a half-aimed skillshot never ends up in the save
        SkillshotFramework.reset();
    }
}
