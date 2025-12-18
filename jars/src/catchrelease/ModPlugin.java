package catchrelease;

import catchrelease.campaign.ponds.listener.OnJumpPondSpawner;
import catchrelease.testing.TestStencilRenderer;
import com.fs.starfarer.api.BaseModPlugin;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

public class ModPlugin extends BaseModPlugin {
    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        //Static fishing spots
        OnJumpPondSpawner.register();

        //Testing
        //LunaCampaignRenderer.addTransientRenderer(new TestStencilRenderer());
    }
}
