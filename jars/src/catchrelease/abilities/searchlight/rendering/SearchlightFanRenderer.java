package catchrelease.abilities.searchlight.rendering;

import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.helper.math.TrigHelper;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;

/**
 * The fan module's wedge-shaped beam, thrown from the hull to the aim point. Cut as geometry rather
 * than a stretched sprite (see {@link catchrelease.rendering.helper.Disc}). Shape must match
 * {@link Searchlight#getLitStrength(Vector2f)}'s hit test exactly - the module's half-angle and
 * reach (aim distance + beam radius), far end cut as an arc since reach is a distance. Falloff uses
 * smootherStep rather than linear ramps for a soft-edged look, with the gameplay's tip-strength
 * floor held up to the aim point and eased to zero across the overshoot beyond it.
 */
public class SearchlightFanRenderer implements LunaCampaignRenderingPlugin {

    /** Mesh resolution; GL interpolates colour linearly per triangle, so this is the coarsest cut that still reads as curved. */
    public static final int STEPS_ACROSS = 16;
    public static final int STEPS_ALONG = 24;

    public static final float SUPERLUMINAL_TIME = 0.4f;

    /** Live vectors, not copies - the wedge follows the fleet and the sweep automatically. */
    private final Vector2f origin;
    private final Vector2f aim;

    private final float size;
    private final Color color;

    private boolean expired = false;

    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    private float timePassed = 0f;
    private float extraAlphaMult = 1f;

    private FlickerUtilV2 flicker = new FlickerUtilV2(8f);

    public SearchlightFanRenderer(Vector2f origin, Vector2f aim, float size, Color color) {
        this.origin = origin;
        this.aim = aim;
        this.size = size;
        this.color = color;
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
        float progress = Math.min(timePassed / SUPERLUMINAL_TIME, 1f);
        extraAlphaMult = 0.8f * TrigHelper.smootherStep(1f - progress);

        flicker.advance(amount);

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) {
                expired = true;
            }
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        //must match the spot's layer - dents are cut out one layer up, and would subtract from black underneath otherwise
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;

        //matches the spot's alpha exactly (flash + flicker) so the module only changes shape, not brightness
        float alpha;
        if (extraAlphaMult > 0) alpha = extraAlphaMult;
        else alpha = 0.12f - 0.03f * flicker.getBrightness();

        if (fading) {
            float fadeT = MathUtils.clamp(1f - (fadeElapsed / fadeDuration), 0f, 1f);
            alpha *= fadeT;
        }
        if (alpha <= 0f) return;

        //degenerate only on an unset first frame - the sweep otherwise keeps aim well clear of origin
        float distance = Misc.getDistance(origin, aim);
        if (distance < 1f) return;

        //reads Searchlight.getArea() live each frame, matching the hit test rather than a cached size
        drawWedge(Misc.getAngleInDegrees(origin, aim), distance + Searchlight.getArea(), distance, alpha);
    }

    /** Triangle-strip bands walked out from the origin in polar coordinates; additive, untextured. */
    protected void drawWedge(float direction, float length, float aimDistance, float alpha) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        float aimFract = aimDistance / length;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (int band = 0; band < STEPS_ALONG; band++) {
            float uNear = band / (float) STEPS_ALONG;
            float uFar = (band + 1) / (float) STEPS_ALONG;

            float alongNear = along(uNear, aimFract) * alpha;
            float alongFar = along(uFar, aimFract) * alpha;

            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= STEPS_ACROSS; i++) {
                float t = i / (float) STEPS_ACROSS * 2f - 1f; //-1 at one edge, 1 at the other

                double angle = Math.toRadians(direction + Searchlight.getFanHalfAngle() * t);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);

                float across = TrigHelper.smootherStep(1f - Math.abs(t));

                GL11.glColor4f(r, g, b, across * alongFar);
                GL11.glVertex2f(origin.x + cos * length * uFar, origin.y + sin * length * uFar);
                GL11.glColor4f(r, g, b, across * alongNear);
                GL11.glVertex2f(origin.x + cos * length * uNear, origin.y + sin * length * uNear);
            }
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    /** Brightness at fraction {@code u} of the beam's length: shoulder-and-die curve out to the aim point, eased to zero beyond it. */
    protected float along(float u, float aimFract) {
        float base = Searchlight.FAN_TIP_STRENGTH
                + (1f - Searchlight.FAN_TIP_STRENGTH) * TrigHelper.smootherStep(1f - u);

        if (u <= aimFract) return base;

        return base * TrigHelper.smootherStep((1f - u) / (1f - aimFract));
    }
}
