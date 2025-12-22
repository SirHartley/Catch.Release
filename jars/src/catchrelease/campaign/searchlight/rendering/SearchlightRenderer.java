package catchrelease.campaign.searchlight.rendering;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.util.EnumSet;

public class SearchlightRenderer implements LunaCampaignRenderingPlugin {

    public static final float FADE_OUT_SECONDS = 0.5f;

    private Vector2f loc;

    private boolean shouldFade = false;
    private float baseAlpha = 1f;

    @Override
    public void advance(float amount) {
        if (shouldFade) baseAlpha -= amount / FADE_OUT_SECONDS;

    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {

    }

    public void setLoc(Vector2f loc){
        this.loc = loc;
    }

    @Override
    public boolean isExpired() {
        return baseAlpha <= 0;
    }

    public void fadeOut(){
        shouldFade = true;
    }

    //used when jumping
    public void fadeOut(float forceAlpha){
        baseAlpha = forceAlpha;
        shouldFade = true;
    }
}
