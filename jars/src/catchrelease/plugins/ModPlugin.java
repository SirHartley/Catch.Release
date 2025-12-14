package catchrelease.plugins;

import catchrelease.testing.TestStencilRenderer;
import com.fs.starfarer.api.BaseModPlugin;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

public class ModPlugin extends BaseModPlugin {
    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        LunaCampaignRenderer.addTransientRenderer(new TestStencilRenderer());
    }
}
