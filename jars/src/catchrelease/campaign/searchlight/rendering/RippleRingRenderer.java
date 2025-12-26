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

public class RippleRingRenderer implements LunaCampaignRenderingPlugin {

    public static final String NOISE_TEX_PATH = "graphics/catchrelease/effects/ripple_map_1.png";

    public static final float START_RADIUS_PX = 200f;
    public static final float END_RADIUS_PX = 300f;

    public static final float RING_WIDTH_PX = 2f;
    public static final float FEATHER_PX = 2f;
    public static final float GROW_TIME = 7.0f;

    transient protected SpriteAPI noise;
    transient protected SearchlightRippleRenderer ringRenderer;

    private float age = 0f;
    private boolean expired = false;

    @Override
    public boolean isExpired() {
        return expired;
    }

    @Override
    public void advance(float amount) {
        if (expired) return;

        age += amount;

        if (age >= GROW_TIME) {
            expired = true;
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;

        loadSpritesIfNeeded();
        if (layer != CampaignEngineLayers.ABOVE) return;

        Vector2f center = Global.getSector().getPlayerFleet().getLocation();
        renderGrowingFadingRing(center);
    }

    private void renderGrowingFadingRing(Vector2f center) {
        if (ringRenderer == null) ringRenderer = new SearchlightRippleRenderer();

        float maxRadiusPx = Math.max(START_RADIUS_PX, END_RADIUS_PX);
        float halfExtent = maxRadiusPx + (RING_WIDTH_PX * 0.5f) + FEATHER_PX + 2f;
        float sizePx = halfExtent * 2f;

        float t = (GROW_TIME <= 0f) ? 1f : clamp(age / GROW_TIME, 0f, 1f);
        float rT = smootherSmootherStep(t);
        float radiusPx = lerp(START_RADIUS_PX, END_RADIUS_PX, rT);

        float up = smootherSmootherStep(t);
        float down = smootherSmootherStep(1f - t);
        float alphaMult = up * down * 4f; //*4 normalizes peak to ~1 at t=0.5

        if (alphaMult <= 0f) return;

        float radiusUv = radiusPx / sizePx;
        float ringWidthUv = RING_WIDTH_PX / sizePx;
        float featherUv = FEATHER_PX / sizePx;

        float angularTiling = 1f;
        float radialTiling = 2f;
        float noiseScroll = 0.005f;

        float noiseCutoff = 0.7f;
        float noiseSoft = 0.3f;

        Color color = new Color(255, 180, 50, 255);

        ringRenderer.renderRing(
                noise,
                center,
                sizePx,
                age,
                alphaMult,
                radiusUv,
                ringWidthUv,
                featherUv,
                angularTiling,
                radialTiling,
                noiseScroll,
                noiseCutoff,
                noiseSoft,
                color
        );
    }

    private void loadSpritesIfNeeded() {
        if (noise != null) return;

        try {
            Global.getSettings().loadTexture(NOISE_TEX_PATH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        noise = Global.getSettings().getSprite(NOISE_TEX_PATH);
    }

    private static float clamp(float x, float a, float b) {
        if (x < a) return a;
        if (x > b) return b;
        return x;
    }

    private static float smootherSmootherStep(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
