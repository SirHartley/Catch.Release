package catchrelease.abilities.searchlight.rendering;

import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.ponds.renderer.PondDepthField;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Stencil;
import catchrelease.rendering.plugins.MaskGlowRenderer;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;

public class SearchlightBurnRenderer implements LunaCampaignRenderingPlugin {
    public static final float TEAR_TIME = 1.2f;
    public static final Color RIM_DEEP = new Color(170, 20, 200);
    public static final Color RIM_HOT = new Color(255, 120, 255);
    public static final float RIM_DEEP_ALPHA = 0.15f;
    public static final float RIM_HOT_ALPHA = 0.2f;
    public static final float RIM_DEEP_THRESHOLD = 0.2f;
    public static final float RIM_HOT_THRESHOLD = 0.1f;
    public static final float RIM_DEEP_SIZE_MULT = 1.1f;
    public static final float RIM_HOT_SIZE_MULT = 1.15f;
    public static final float EMBER_ALPHA = 0.1f;
    public static final float EMBER_SIZE_MULT = 1.2f;
    public static final float EMBER_RADIUS_PX = 14f;
    public static final Color RING_COLOR = RIM_HOT;
    public static final float FILL_LAG_TAU = 1.5f;
    public static final float FILL_LAG_MAX_MULT = 0.5f;
    public static final float FILL_SNAP_DISTANCE = 2000f;
    public static final float FILL_DRIFT = 40f;
    public static final float FILL_DRIFT_PERIOD = 17f;
    public static final float FILL_COVER_MULT = 2f;

    transient protected SpriteAPI mask;
    transient protected SpriteAPI fill;
    transient protected MaskGlowRenderer maskGlow;
    transient protected PondDepthField depthField;
    transient protected float fillNaturalSize = 0f;
    private final Vector2f loc;
    private final float size;
    private final Vector2f fillCenter;
    private float elapsed = 0f;
    private float tear = 0f;
    private boolean expired = false;
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    public SearchlightBurnRenderer(Vector2f loc, float size) {
        this.loc = loc;
        this.size = size;
        this.fillCenter = new Vector2f(loc);
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

        elapsed += amount;
        if (tear < 1f) tear = Math.min(1f, tear + amount / TEAR_TIME);

        if (depthField != null) depthField.advance(amount);

        advanceFill(amount);

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) {
                expired = true;
            }
        }
    }

    protected void advanceFill(float amount) {
        Vector2f gap = Vector2f.sub(loc, fillCenter, null);
        float distance = gap.length();

        if (distance > FILL_SNAP_DISTANCE) {
            fillCenter.set(loc);
            return;
        }

        float step = FILL_LAG_TAU <= 0f ? 1f : Math.min(1f, amount / FILL_LAG_TAU);
        fillCenter.x += gap.x * step;
        fillCenter.y += gap.y * step;

        gap = Vector2f.sub(loc, fillCenter, null);
        distance = gap.length();

        float maxLag = size * FILL_LAG_MAX_MULT;
        if (distance > maxLag && distance > 0f) {
            float pull = (distance - maxLag) / distance;
            fillCenter.x += gap.x * pull;
            fillCenter.y += gap.y * pull;
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1, CampaignEngineLayers.TERRAIN_2,
                CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;

        loadSpritesIfNeeded();
        if (mask == null || fill == null) return;

        float alpha = viewport.getAlphaMult();

        if (fading) {
            float fadeT = fadeDuration > 0f ? 1f - (fadeElapsed / fadeDuration) : 0f;
            alpha *= MathUtils.clamp(fadeT, 0f, 1f);
        }
        if (alpha <= 0f) return;

        // Like the pond, the opening is in the mask, not the alpha - it tears wider, doesn't fade in place.
        float maskSize = size * 2f * tear;
        if (maskSize <= 0f) return;

        if (layer == CampaignEngineLayers.TERRAIN_1) {
            renderFill(alpha, maskSize);
            return;
        }

        if (layer == CampaignEngineLayers.TERRAIN_2) {
            renderRim(alpha, maskSize);
            return;
        }

        if (layer == CampaignEngineLayers.ABOVE) {
            renderContents(alpha, maskSize);
        }
    }

    protected void renderFill(float alpha, float maskSize) {
        float fillSize = Math.max(fillNaturalSize, size * 2f * FILL_COVER_MULT);
        Vector2f wander = computeWander();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);

        Stencil.startDepthMask(mask, maskSize, maskSize, loc, true);

        fill.setNormalBlend();
        fill.setSize(fillSize, fillSize);
        fill.setAlphaMult(alpha);
        fill.renderAtCenter(fillCenter.x + wander.x, fillCenter.y + wander.y);

        Stencil.endDepthMask();

        GL11.glPopAttrib();
    }

    protected void renderRim(float alpha, float maskSize) {
        if (maskGlow == null) maskGlow = new MaskGlowRenderer();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);

        maskGlow.setThreshold(RIM_DEEP_THRESHOLD);
        maskGlow.renderAdditive(mask, loc, maskSize * RIM_DEEP_SIZE_MULT,
                RIM_DEEP, RIM_DEEP_ALPHA * alpha, 1f, 1f);

        maskGlow.setThreshold(RIM_HOT_THRESHOLD);
        maskGlow.renderAdditive(mask, loc, maskSize * RIM_HOT_SIZE_MULT,
                RIM_HOT, RIM_HOT_ALPHA * alpha, 8f, 0f);

        maskGlow.renderAdditive(mask, loc, maskSize * EMBER_SIZE_MULT,
                Searchlight.COLOR, EMBER_ALPHA * alpha, EMBER_RADIUS_PX, 0f);

        GL11.glPopAttrib();
    }

    protected void renderContents(float alpha, float maskSize) {
        if (depthField == null) depthField = new PondDepthField();

        Stencil.startDepthMask(mask, maskSize, maskSize, loc, true);

        depthField.render(loc, size, alpha);

        Stencil.endDepthMask();
    }

    protected Vector2f computeWander() {
        double rate = 2.0 * Math.PI / FILL_DRIFT_PERIOD;

        float x = (float) Math.sin(elapsed * rate) * FILL_DRIFT;
        float y = (float) Math.sin(elapsed * rate * 0.61803f + 1.3) * FILL_DRIFT;

        return new Vector2f(x, y);
    }

    public void loadSpritesIfNeeded() {
        if (mask == null) mask = SpriteLoader.getSprite("pond_1");

        if (fill == null) {
            fill = SpriteLoader.getSprite("hs_bg");
            if (fill != null) fillNaturalSize = fill.getWidth();
        }
    }
}
