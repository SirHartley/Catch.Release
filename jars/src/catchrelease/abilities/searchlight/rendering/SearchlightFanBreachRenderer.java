package catchrelease.abilities.searchlight.rendering;

import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.helper.math.TrigHelper;
import catchrelease.rendering.helper.ParallaxUtil;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.util.EnumSet;

/**
 * The fan's window: {@link SearchlightBreachRenderer} cut as a wedge instead of a disc.
 * <p>
 * Same claim, same construction. The deep is drawn at its natural size with UVs taken from where
 * each vertex stands in the sector, so the wedge pivots and stretches across a starfield that
 * stays put; the parallax lean and wander are the disc's, for the disc's reason. What changes is
 * only the shape of the opening, and the shape is not this renderer's to choose any more than it
 * is the fan's - the geometry and both falloffs are {@link SearchlightFanRenderer}'s exactly, so
 * the window opens precisely where the light over it is bright.
 */
public class SearchlightFanBreachRenderer implements LunaCampaignRenderingPlugin {

    /** The disc's numbers, so the two windows read as the same fabric giving way. */
    public static final float OPEN_TIME = SearchlightBreachRenderer.OPEN_TIME;
    public static final float CENTER_ALPHA = SearchlightBreachRenderer.CENTER_ALPHA;

    public static final int STEPS_ACROSS = SearchlightFanRenderer.STEPS_ACROSS;
    public static final int STEPS_ALONG = SearchlightFanRenderer.STEPS_ALONG;

    public transient SpriteAPI fill;

    /** The live vectors, not copies - the wedge rides the fleet and follows the sweep. */
    private final Vector2f origin;
    private final Vector2f aim;

    private float timePassed = 0f;
    private float open = 0f;

    private boolean expired = false;

    //fadeAndExpire
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    public SearchlightFanBreachRenderer(Vector2f origin, Vector2f aim) {
        this.origin = origin;
        this.aim = aim;
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
        //under the fan on its own layer, by registration order - the light is over the window
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

        //the fan's own degenerate first frame, caught the fan's own way
        float distance = Misc.getDistance(origin, aim);
        if (distance < 1f) return;

        float texW = fill.getWidth();
        float texH = fill.getHeight();
        if (texW <= 0f || texH <= 0f) return;

        float fillSizeWorld = texW;

        //the lean is taken off the aim point rather than the hull - the aim is where the player
        //is looking through the window, so that is where the depth should answer
        Vector2f lean = ParallaxUtil.computeFillUvOffsetPx(viewport, aim,
                SearchlightBreachRenderer.PARALLAX_MAX_DISPLACEMENT, fillSizeWorld, texW, texH);
        Vector2f wander = ParallaxUtil.computeDriftUvOffsetPx(timePassed,
                SearchlightBreachRenderer.DRIFT, SearchlightBreachRenderer.DRIFT_PERIOD,
                fillSizeWorld, texW, texH);

        float uOff = (lean.x + wander.x) / texW;
        float vOff = (lean.y + wander.y) / texH;

        drawWedge(Misc.getAngleInDegrees(origin, aim), distance + Searchlight.getArea(), distance,
                alpha * CENTER_ALPHA, fillSizeWorld, uOff, vOff);
    }

    /** The fan's wedge, vertex for vertex, wearing the window's texture and alphas. */
    protected void drawWedge(float direction, float length, float aimDistance, float alpha,
                             float fillSizeWorld, float uOff, float vOff) {

        float aimFract = aimDistance / length;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        fill.bindTexture();

        //world-coordinate UVs run past [0,1], so the texture has to tile
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int band = 0; band < STEPS_ALONG; band++) {
            float uNear = band / (float) STEPS_ALONG;
            float uFar = (band + 1) / (float) STEPS_ALONG;

            float alongNear = along(uNear, aimFract) * alpha;
            float alongFar = along(uFar, aimFract) * alpha;

            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= STEPS_ACROSS; i++) {
                float t = i / (float) STEPS_ACROSS * 2f - 1f;

                double angle = Math.toRadians(direction + Searchlight.getFanHalfAngle() * t);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);

                float across = TrigHelper.smootherStep(1f - Math.abs(t));

                emit(origin.x + cos * length * uFar, origin.y + sin * length * uFar,
                        fillSizeWorld, uOff, vOff, across * alongFar);
                emit(origin.x + cos * length * uNear, origin.y + sin * length * uNear,
                        fillSizeWorld, uOff, vOff, across * alongNear);
            }
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    /** The fan's brightness curve down the length, so window and light dim together. */
    protected float along(float u, float aimFract) {
        float base = Searchlight.FAN_TIP_STRENGTH
                + (1f - Searchlight.FAN_TIP_STRENGTH) * TrigHelper.smootherStep(1f - u);

        if (u <= aimFract) return base;

        return base * TrigHelper.smootherStep((1f - u) / (1f - aimFract));
    }

    /** One vertex, sampling the deep by where it stands - the disc renderer's emit exactly. */
    protected void emit(float x, float y, float fillSizeWorld, float uOff, float vOff, float alpha) {
        GL11.glColor4f(1f, 1f, 1f, alpha);
        GL11.glTexCoord2f(x / fillSizeWorld + uOff, y / fillSizeWorld + vOff);
        GL11.glVertex2f(x, y);
    }

    public void loadSpritesIfNeeded() {
        if (fill == null) fill = SpriteLoader.getSprite("hs_bg");
    }
}
