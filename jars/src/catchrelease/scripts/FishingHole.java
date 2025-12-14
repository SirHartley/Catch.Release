package catchrelease.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;

import java.util.EnumSet;

public class FishingHole implements EveryFrameScript, LunaCampaignRenderingPlugin {
    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void advance(float amount) {

    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return null;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {

    }
}
