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
 * What a searchlight is in hyperspace once the burn-through has been bought: not a torch shining
 * across the deep but a hole burned into it - a small, travelling pond.
 * <p>
 * Built from the pond's own vocabulary on purpose, piece by piece: the same ragged mask, the same
 * starfield showing through it, the same two rim colours at the same thresholds, and the same depth
 * field spiralling inside. The player has stood at the rim of a rupture before this upgrade is ever
 * affordable, and the burn should be recognised rather than learned - the one thing added is a pass
 * of the searchlight's own orange outside the purple, which is what says the beam is doing the
 * burning rather than the fabric failing on its own.
 * <p>
 * It travels the way the beam's other renderers travel, by holding the light's live location vector
 * rather than a copy of it. The fill deliberately does not: it eases after the beam and wanders on
 * its own, so the mask slides across a deep that stays put - which is the whole difference between
 * looking through a hole and looking at a sticker. See {@link #advanceFill(float)}.
 */
public class SearchlightBurnRenderer implements LunaCampaignRenderingPlugin {

    /**
     * Seconds the burn takes to tear open. Much quicker than the pond's five-second spool: a pond
     * is scenery that opens while the player approaches it, a burn is something a light does while
     * they watch, and a light that takes five seconds to start working reads as broken.
     */
    public static final float TEAR_TIME = 1.2f;

    /**
     * The rim, in the pond's exact colours, alphas and thresholds. The rim is the claim being made
     * - this is the same fabric giving way, only smaller - so nothing here is allowed to drift from
     * what the pond draws.
     */
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
     * How the deep trails the moving beam. The fill closes the gap to the beam on TAU seconds and
     * is never allowed further behind than MAX_MULT of the beam's radius - a lock snapping onto a
     * mote drags the beam fast enough to leave it further than that, and past there the fill stops
     * reading as behind the hole and starts reading as falling off it.
     */
    public static final float FILL_LAG_TAU = 1.5f;
    public static final float FILL_LAG_MAX_MULT = 0.5f;

    /** Past this the beam did not move, it arrived - a load, or the first frame - and the fill
     * arrives with it rather than crossing the map to catch up. */
    public static final float FILL_SNAP_DISTANCE = 2000f;

    /**
     * The deep's own wander, in world units, for when the beam has stopped on something. Same
     * problem the pond has under a snapped camera: a held beam contributes no motion, and a hole
     * with a still background in it reads as a hole cut in paper. Same fix too, only in world units
     * rather than UV pixels because this fill is moved bodily instead of through its texture.
     */
    public static final float FILL_DRIFT = 40f;
    public static final float FILL_DRIFT_PERIOD = 17f;

    /** The least fill ever drawn, as a multiple of the fully open mask, in case the starfield's own
     * size ever comes up short of covering lag and wander. */
    public static final float FILL_COVER_MULT = 2f;

    transient protected SpriteAPI mask;
    transient protected SpriteAPI fill;
    transient protected MaskGlowRenderer maskGlow;
    transient protected PondDepthField depthField;

    /** The starfield's natural size, captured before anything resizes it - drawn smaller the stars
     * shrink and the deep flattens, so natural is also the floor. */
    transient protected float fillNaturalSize = 0f;

    /** The light's own vector, not a copy - travelling with the beam is holding this. */
    private final Vector2f loc;
    private final float size;

    /** Where the deep currently is, which is behind wherever the beam currently is. */
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

    /**
     * The deep eases after the beam instead of riding it. While the beam sweeps, the mask slides
     * across a fill that lags it, which is motion parallax and reads as depth for free; the clamp
     * keeps a lock-snap from dragging the hole clean off its own contents, and the snap distance
     * catches the beam arriving somewhere rather than travelling there.
     */
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
        //the pond's three layers, doing the pond's three jobs: the deep, the rim, the contents
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

        //like the pond, the opening is in the mask and not in the alpha - the hole tears wider
        //rather than the whole thing fading up in place
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

    /**
     * The deep showing through the hole: the pond's starfield, cropped to the mask by the depth
     * stencil rather than by the pond's warp shader - at a beam's size the swirl is not visible
     * enough to pay for the heavier renderer, and the lag is doing the living instead.
     */
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

    /**
     * The depth field, at the full radius whatever the tear is doing, for the pond's own reason:
     * the mask is already opening around it, so a tearing burn wipes across a field that was always
     * there rather than growing one from a knot in the middle.
     */
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
