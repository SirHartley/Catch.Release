package catchrelease.abilities.searchlight.rendering;

import catchrelease.helper.loading.SpriteLoader;
import catchrelease.helper.math.TrigHelper;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

// render search light in center of... search light
public class SearchlightGlowRenderer implements LunaCampaignRenderingPlugin {
    public transient SpriteAPI sprite;

    private boolean expired = false;

    public static final float SUPERLUMINAL_TIME = 0.4f;

    //fadeAndExpire
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    private float size;
    private Color color;
    private Vector2f loc;

    private float timePassed = 0f;
    private float extraAlphaMult = 1f;

    //flicker
    private FlickerUtilV2 flicker = new FlickerUtilV2(8f);

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

        // superluminance
        timePassed += amount;
        float progress = Math.min(timePassed / SUPERLUMINAL_TIME, 1f);
        extraAlphaMult = 0.8f * TrigHelper.smootherStep(1f - progress);

        //flicker
        flicker.advance(amount);

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

        float alpha;
        if (extraAlphaMult > 0) alpha = extraAlphaMult;
        else alpha = 0.12f - 0.03f * flicker.getBrightness();

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
        if (sprite == null) sprite = SpriteLoader.getSprite("spotlight_circle");
    }
}
