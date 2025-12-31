package catchrelease.rendering.renderers;

import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.plugins.NoiseMappedCircularRingRenderer;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;

import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

import static catchrelease.helper.math.TrigHelper.smootherStep;

//each ripple has its own renderer which is inefficient, but cpu cycles are free
public class RippleRingRenderer implements LunaCampaignRenderingPlugin {

    //default params for searchlight
    public static final float START_RADIUS_OFFSET = 0.7f;
    public static final float FEATHER_PX = 2f;
    public static final float RING_WIDTH_PX = 2f;
    public static final float GROW_TIME = 7.0f;

    transient protected SpriteAPI noise;
    transient protected NoiseMappedCircularRingRenderer ringRenderer;

    private float age = 0f;
    private boolean expired = false;

    public float ringWidth = RING_WIDTH_PX;
    public float growTime = GROW_TIME;
    public float startRadiusOffsetMult = START_RADIUS_OFFSET;

    //fade
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    public Vector2f location;
    public float size;
    public Color color;

    public RippleRingRenderer(Vector2f loc, float size, Color color) {
        this.location = loc;
        this.size = size;
        this.color = color;
    }

    /**
     * @param startRadiusOffsetMult multiplies the max radius by 0.x to get the start radius
     */
    public RippleRingRenderer(Color color, float maxSize, Vector2f location, float ringWidth, float growTime, float startRadiusOffsetMult) {
        this.color = color;
        this.size = maxSize;
        this.location = location;
        this.ringWidth = ringWidth;
        this.growTime = growTime;
        this.startRadiusOffsetMult = startRadiusOffsetMult;
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

        age += amount;

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) {
                expired = true;
                return;
            }
        }

        if (!fading && age >= growTime) {
            expired = true;
        }
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;

        loadSpritesIfNeeded();
        renderGrowingFadingRing(location);
    }

    private void renderGrowingFadingRing(Vector2f center) {
        if (ringRenderer == null) ringRenderer = new NoiseMappedCircularRingRenderer();

        float maxRadius = size;
        float minRadius = size * startRadiusOffsetMult;

        float maxRadiusPx = Math.max(minRadius, maxRadius);
        float halfExtent = maxRadiusPx + (ringWidth * 0.5f) + FEATHER_PX + 2f;
        float sizePx = halfExtent * 2f;

        float t = MathUtils.clamp(age / growTime, 0f, 1f);
        float rT = smootherStep(t);
        float radiusPx = lerp(minRadius, maxRadius, rT);

        float up = smootherStep(2 * t); //Fade in twice as fast
        float down = smootherStep(1f - t);
        float alphaMult = up * down * 4f; // *4 normalizes peak to ~1 at t=0.5

        // Apply forced fade-out multiplier if active
        if (fading) {
            float fadeT = MathUtils.clamp(1f - (fadeElapsed / fadeDuration), 0f, 1f);
            alphaMult *= fadeT;
        }

        if (alphaMult <= 0f) return;

        float radiusUv = radiusPx / sizePx;
        float ringWidthUv = ringWidth / sizePx;
        float featherUv = FEATHER_PX / sizePx;

        float angularTiling = 1f;
        float radialTiling = 2f;
        float noiseScroll = 0.005f;

        float noiseCutoff = 0.7f;
        float noiseSoft = 0.3f;

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
        if (noise == null) noise = SpriteLoader.getSprite("ring_noise");
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
