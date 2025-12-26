package catchrelease;

import catchrelease.campaign.ponds.listener.OnJumpPondSpawner;
import catchrelease.campaign.searchlight.rendering.SearchlightGlowRenderer;
import catchrelease.campaign.searchlight.scripts.RippleMaker;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

public class ModPlugin extends BaseModPlugin {
    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        //Static fishing spots
        OnJumpPondSpawner.register();

        //Testing
        //LunaCampaignRenderer.addTransientRenderer(new TestMaskedWarpShaderRenderer());
        //LunaCampaignRenderer.addTransientRenderer(new TestStencilRenderer());
        //LunaCampaignRenderer.addTransientRenderer(new RippleRingRenderer());
        LunaCampaignRenderer.addTransientRenderer(new SearchlightGlowRenderer());
        Global.getSector().addTransientScript(new RippleMaker());
    }
}
