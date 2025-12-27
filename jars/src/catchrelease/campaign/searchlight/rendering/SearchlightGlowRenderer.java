package catchrelease.campaign.searchlight.rendering;

import catchrelease.campaign.memory.upgrades.StatIds;
import catchrelease.campaign.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.io.IOException;
import java.util.EnumSet;

// render search light in center of... search light
public class SearchlightGlowRenderer implements LunaCampaignRenderingPlugin {
    public transient SpriteAPI sprite;

    private boolean expired = false;

    // Fade-and-expire support
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    private float size;
    private Color color;
    private Vector2f loc;

    public SearchlightGlowRenderer(Vector2f loc, float size, Color color) {
        this.size = size;
        this.color = color;
        this.loc = loc;
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    public void fadeAndExpire(float fadeSeconds) {
        if (expired) return;

        fading = true;
        fadeDuration = fadeSeconds;
        fadeElapsed = 0f;
    }

    @Override
    public void advance(float amount) {
        if (expired) return;

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) {
                expired = true;
            }
        }
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;
        loadSpritesIfNeeded();

        float alpha = 0.1f;
        if (fading) {
            float fadeT = MathUtils.clamp(1f - (fadeElapsed / fadeDuration), 0f, 1f);
            alpha *= fadeT;
        }
        if (alpha <= 0f) return;

        sprite.setAdditiveBlend();
        sprite.setSize(size * 1.8f, size * 1.8f); //double because we do radius, not diameter
        sprite.setAlphaMult(alpha);
        sprite.setColor(color);
        sprite.renderAtCenter(loc.x, loc.y);
    }

    public void loadSpritesIfNeeded() {
        if (sprite != null) return;

        try {
            Global.getSettings().loadTexture("graphics/catchrelease/effects/spotlight_circle.png");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        sprite = Global.getSettings().getSprite("graphics/catchrelease/effects/spotlight_circle.png");
    }
}
