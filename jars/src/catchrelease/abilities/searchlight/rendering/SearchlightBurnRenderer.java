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

/**
 * The searchlight's hyperspace burn-through, rendered as a small travelling pond: same mask,
 * starfield, rim colours/thresholds and depth field as the pond, so it reads as the same fabric
 * failing rather than something to learn - plus an added orange ember pass outside the purple to
 * mark it as the beam burning through, not the fabric failing on its own.
 * <p>
 * Holds the light's live location vector, not a copy, so it travels with the beam. The fill instead
 * eases behind it independently - see {@link #advanceFill(float)} - for motion parallax.
 */
public class SearchlightBurnRenderer implements LunaCampaignRenderingPlugin {

    /** Seconds to tear open - quicker than the pond's five-second spool, since a beam should read as working right away. */
    public static final float TEAR_TIME = 1.2f;

    /** Rim colours, alphas and thresholds match the pond's exactly - same fabric, so must not drift from it. */
    public static final Color RIM_DEEP = new Color(170, 20, 200);
    public static final Color RIM_HOT = new Color(255, 120, 255);
    public static final float RIM_DEEP_ALPHA = 0.15f;
    public static final float RIM_HOT_ALPHA = 0.2f;
    public static final float RIM_DEEP_THRESHOLD = 0.2f;
    public static final float RIM_HOT_THRESHOLD = 0.1f;
    public static final float RIM_DEEP_SIZE_MULT = 1.1f;
    public static final float RIM_HOT_SIZE_MULT = 1.15f;

    /** The ember: the searchlight's own orange standing just outside the purple. */
    public static final float EMBER_ALPHA = 0.1f;
    public static final float EMBER_SIZE_MULT = 1.2f;
    public static final float EMBER_RADIUS_PX = 14f;

    /** What the beam's splash rings wear over a burn - ripples on this surface are this colour. */
    public static final Color RING_COLOR = RIM_HOT;

    /**
     * Fill eases toward the beam over TAU seconds, clamped to at most MAX_MULT of the beam's radius
     * behind it - past that a lock-snap onto a mote would leave the fill reading as fallen off, not trailing.
     */
    public static final float FILL_LAG_TAU = 1.5f;
    public static final float FILL_LAG_MAX_MULT = 0.5f;

    /** Beyond this the beam didn't move, it arrived (load or first frame) - the fill snaps with it instead of catching up. */
    public static final float FILL_SNAP_DISTANCE = 2000f;

    /**
     * The fill's own wander (world units) while the beam is stationary - same fix as the pond's under
     * a snapped camera (a held beam contributes no motion, so a still fill reads as flat), but applied
     * to the sprite's position directly rather than its UV offset.
     */
    public static final float FILL_DRIFT = 40f;
    public static final float FILL_DRIFT_PERIOD = 17f;

    /** Least fill ever drawn (multiple of the open mask), in case the starfield's own size falls short of covering lag and wander. */
    public static final float FILL_COVER_MULT = 2f;

    transient protected SpriteAPI mask;
    transient protected SpriteAPI fill;
    transient protected MaskGlowRenderer maskGlow;
    transient protected PondDepthField depthField;

    /** Starfield's natural size, captured before anything resizes it - also the floor it's drawn at. */
    transient protected float fillNaturalSize = 0f;

    /** The light's own vector, not a copy - travelling with the beam means holding this directly. */
    private final Vector2f loc;
    private final float size;

    /** Where the deep currently is, trailing behind the beam's current location. */
    private final Vector2f fillCenter;

    private float elapsed = 0f;
    private float tear = 0f;

    private boolean expired = false;

    //fadeAndExpire
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

    /** Eases the fill after the beam rather than riding it, for free motion parallax; see {@link #FILL_LAG_TAU}/{@link #FILL_LAG_MAX_MULT}/{@link #FILL_SNAP_DISTANCE}. */
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
        // Deep, rim, contents - the pond's three layers.
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

    /** Deep showing through the hole, cropped by the stencil rather than the pond's warp shader - swirl isn't visible at this size, and the lag already carries the motion. */
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

    /** The pond's two rim passes, then the ember over them. */
    protected void renderRim(float alpha, float maskSize) {
        if (maskGlow == null) maskGlow = new MaskGlowRenderer();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);

        maskGlow.setThreshold(RIM_DEEP_THRESHOLD); // keep gradients
        maskGlow.renderAdditive(mask, loc, maskSize * RIM_DEEP_SIZE_MULT,
                RIM_DEEP, RIM_DEEP_ALPHA * alpha, 1f, 1f);

        maskGlow.setThreshold(RIM_HOT_THRESHOLD); // keep gradients
        maskGlow.renderAdditive(mask, loc, maskSize * RIM_HOT_SIZE_MULT,
                RIM_HOT, RIM_HOT_ALPHA * alpha, 8f, 0f);

        maskGlow.renderAdditive(mask, loc, maskSize * EMBER_SIZE_MULT,
                Searchlight.COLOR, EMBER_ALPHA * alpha, EMBER_RADIUS_PX, 0f);

        GL11.glPopAttrib();
    }

    /** Depth field at full radius regardless of tear progress - the mask already opens around it, so it should look already-there, not grown from the middle. */
    protected void renderContents(float alpha, float maskSize) {
        if (depthField == null) depthField = new PondDepthField();

        Stencil.startDepthMask(mask, maskSize, maskSize, loc, true);

        depthField.render(loc, size, alpha);

        Stencil.endDepthMask();
    }

    /** The pond's non-repeating pair of sines, so the path never lands on a beat or retraces. */
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
