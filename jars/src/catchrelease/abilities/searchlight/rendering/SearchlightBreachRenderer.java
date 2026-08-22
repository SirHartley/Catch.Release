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

public class SearchlightBreachRenderer implements LunaCampaignRenderingPlugin {

    public static final float OPEN_TIME = 0.6f;
    public static final float RADIUS_MULT = 0.9f;
    public static final float CENTER_ALPHA = 0.85f;
    public static final int RINGS = 4;
    public static final int SEGMENTS = 48;
    public static final float PARALLAX_MAX_DISPLACEMENT = 60f;

    public static final float DRIFT = 25f;
    public static final float DRIFT_PERIOD = 17f;

    public transient SpriteAPI fill;
    private final Vector2f loc;
    private final float size;
    private float timePassed = 0f;
    private float open = 0f;

    private boolean expired = false;
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

        if (fadeSeconds <= 0f) {
            expired = true;
            return;
        }

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
        // must register before the glow layer, since draw order within a layer follows registration order
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

        // natural size: 1 texture pixel = 1 world unit, matching how the deep is drawn elsewhere
        float fillSizeWorld = texW;

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

        // UVs run past [0,1] (world coords), so the texture must tile
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // drawn as annuli rather than one fan, so alpha can fall off in steps rather than linearly
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

    protected float falloff(float t) {
        float inBeam = 1f - t;

        return inBeam * inBeam;
    }

    protected void emit(float x, float y, float fillSizeWorld, float uOff, float vOff, float alpha) {
        GL11.glColor4f(1f, 1f, 1f, alpha);
        GL11.glTexCoord2f(x / fillSizeWorld + uOff, y / fillSizeWorld + vOff);
        GL11.glVertex2f(x, y);
    }

    public void loadSpritesIfNeeded() {
        if (fill == null) fill = SpriteLoader.getSprite("hs_bg");
    }
}
