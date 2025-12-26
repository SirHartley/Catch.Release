package catchrelease.campaign.searchlight.rendering;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.io.IOException;
import java.util.EnumSet;

//render search light in center of... search light
public class SearchlightGlowRenderer implements LunaCampaignRenderingPlugin {
    public transient SpriteAPI sprite;

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (isExpired()) return;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isExpired()) return;
        loadSpritesIfNeeded();

        Vector2f loc = Global.getSector().getPlayerFleet().getLocation();

        sprite.setAdditiveBlend();
        sprite.setSize(RippleRingRenderer.END_RADIUS_PX * 1.8f, RippleRingRenderer.END_RADIUS_PX * 1.8f);
        sprite.setAlphaMult(0.1f);
        sprite.setColor( new Color(255, 180, 50, 255));
        sprite.renderAtCenter(loc.x, loc.y);
    }


    public void loadSpritesIfNeeded() {
        if (sprite == null) {
            try {
                Global.getSettings().loadTexture("graphics/catchrelease/effects/spotlight_circle.png");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            sprite = Global.getSettings().getSprite("graphics/catchrelease/effects/spotlight_circle.png");
        }
    }
}
