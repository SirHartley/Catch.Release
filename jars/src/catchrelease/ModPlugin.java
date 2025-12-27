package catchrelease;

import catchrelease.campaign.memory.upgrades.UpgradeManager;
import catchrelease.campaign.ponds.listener.OnJumpPondSpawner;
import catchrelease.campaign.searchlight.rendering.SearchlightGlowRenderer;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

public class ModPlugin extends BaseModPlugin {
    public static final String MOD_ID = "catchrelease";

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        //Static fishing spots
        OnJumpPondSpawner.register();

        //data
        UpgradeManager.getInstance().updateBaseValues();

        //Testing
        //LunaCampaignRenderer.addTransientRenderer(new TestMaskedWarpShaderRenderer());
        //LunaCampaignRenderer.addTransientRenderer(new TestStencilRenderer());
        //LunaCampaignRenderer.addTransientRenderer(new RippleRingRenderer());
    }
}
