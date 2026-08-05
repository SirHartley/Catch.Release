package catchrelease.abilities.searchlight.rendering;

import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.ParallaxUtil;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.util.EnumSet;

/**
 * What the breach lamp's light is sweeping across: hyperspace, seen through the beam.
 * <p>
 * The deep is drawn at its natural size and anchored to the world, not to the light - the UVs are
 * taken from where each vertex stands in the sector, so the beam slides across a starfield that
 * stays put, the way a torch slides across a floor. That anchoring is most of the effect: a window
 * shows what is behind it, a sticker travels with whatever it is stuck to.
 * <p>
 * The searchlight's own shape does the cropping, as a gradient rather than a stencil: the fill is
 * drawn as rings of vertices whose alpha falls off the way the beam's glow sprite falls off, full
 * in the middle and nothing at the rim, so the window has the beam's soft edge rather than a
 * cookie-cutter one. The purple glow drawn over it is what says the light is doing the looking.
 * <p>
 * The parallax is borrowed from the pond, and for the pond's reason: the fill wanders on its own
 * and leans against the camera, because a beam the player is travelling with contributes no motion
 * of its own, and a hole with a dead-still background in it reads as a hole cut in paper.
 */
public class SearchlightBreachRenderer implements LunaCampaignRenderingPlugin {

    /** Seconds the window takes to open. Quicker than the glow's own flash fades, so the burn
     * reads as the lamp punching through rather than the deep fading up behind it. */
    public static final float OPEN_TIME = 0.6f;

    /** The window's reach as a share of the beam's radius, matched to where the glow sprite has
     * visibly fallen off so the two shapes read as one light. */
    public static final float RADIUS_MULT = 0.9f;

    /** How much of the deep shows in the middle of the beam. Short of full on purpose - the
     * fabric thins under the light rather than ceasing to exist. */
    public static final float CENTER_ALPHA = 0.85f;

    /** The gradient's resolution: vertex rings from centre to rim, and segments around each. */
    public static final int RINGS = 4;
    public static final int SEGMENTS = 48;

    /** The camera lean and the standing wander, both in world units - the pond's pair, scaled
     * down to something beam-sized. */
    public static final float PARALLAX_MAX_DISPLACEMENT = 60f;
    public static final float DRIFT = 25f;
    public static final float DRIFT_PERIOD = 17f;

    public transient SpriteAPI fill;

    /** The light's own vector, not a copy - travelling with the beam is holding this. */
    private final Vector2f loc;
    private final float size;

    private float timePassed = 0f;
    private float open = 0f;

    private boolean expired = false;

    //fadeAndExpire
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    public SearchlightBreachRenderer(Vector2f loc, float size) {
        this.loc = loc;
        this.size = size;
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

        timePassed += amount;
        if (open < 1f) open = Math.min(1f, open + amount / OPEN_TIME);

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) {
                expired = true;
            }
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        //the beams' own layer, registered before the glow that wears it - within a layer, draw
        //order is registration order, and the window has to be under the light sweeping it
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;
        loadSpritesIfNeeded();
        if (fill == null) return;

        float alpha = viewport.getAlphaMult() * open;

        if (fading) {
            float fadeT = fadeDuration > 0f ? 1f - (fadeElapsed / fadeDuration) : 0f;
            alpha *= MathUtils.clamp(fadeT, 0f, 1f);
        }
        if (alpha <= 0f) return;

        float radius = size * RADIUS_MULT * open;
        if (radius <= 0f) return;

        float texW = fill.getWidth();
        float texH = fill.getHeight();
        if (texW <= 0f || texH <= 0f) return;

        //natural size: one texture pixel is one world unit, which is the "full size" the deep is
        //drawn at everywhere else it shows through
        float fillSizeWorld = texW;

        //both terms come back in texture pixels, added, and taken down to UVs here
        Vector2f lean = ParallaxUtil.computeFillUvOffsetPx(viewport, loc,
                PARALLAX_MAX_DISPLACEMENT, fillSizeWorld, texW, texH);
        Vector2f wander = ParallaxUtil.computeDriftUvOffsetPx(timePassed,
                DRIFT, DRIFT_PERIOD, fillSizeWorld, texW, texH);

        float uOff = (lean.x + wander.x) / texW;
        float vOff = (lean.y + wander.y) / texH;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        fill.bindTexture();

        //the UVs run past [0,1] because they are world coordinates - the texture has to tile
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        //rim to rim in annuli rather than one fan, so the alpha can fall off in steps between the
        //rings - a single fan only ever draws a linear gradient, and the beam's edge is softer
        for (int ring = 0; ring < RINGS; ring++) {
            float tInner = ring / (float) RINGS;
            float tOuter = (ring + 1) / (float) RINGS;

            float alphaInner = falloff(tInner) * CENTER_ALPHA * alpha;
            float alphaOuter = falloff(tOuter) * CENTER_ALPHA * alpha;

            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int seg = 0; seg <= SEGMENTS; seg++) {
                double angle = 2.0 * Math.PI * seg / SEGMENTS;
                float dx = (float) Math.cos(angle);
                float dy = (float) Math.sin(angle);

                emit(loc.x + dx * radius * tInner, loc.y + dy * radius * tInner,
                        fillSizeWorld, uOff, vOff, alphaInner);
                emit(loc.x + dx * radius * tOuter, loc.y + dy * radius * tOuter,
                        fillSizeWorld, uOff, vOff, alphaOuter);
            }
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    /** The beam's own falloff - squared, like {@code getLitStrength} answers for a spot - so the
     * window is exactly as open as the light over any part of it is bright. */
    protected float falloff(float t) {
        float inBeam = 1f - t;

        return inBeam * inBeam;
    }

    /** One vertex: drawn where it stands, sampling the deep by where it stands - world-anchored
     * UVs at natural scale, plus whatever the parallax has leaned the whole fill by. */
    protected void emit(float x, float y, float fillSizeWorld, float uOff, float vOff, float alpha) {
        GL11.glColor4f(1f, 1f, 1f, alpha);
        GL11.glTexCoord2f(x / fillSizeWorld + uOff, y / fillSizeWorld + vOff);
        GL11.glVertex2f(x, y);
    }

    public void loadSpritesIfNeeded() {
        if (fill == null) fill = SpriteLoader.getSprite("hs_bg");
    }
}
