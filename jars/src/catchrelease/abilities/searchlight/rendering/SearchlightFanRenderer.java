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

public class SearchlightFanRenderer implements LunaCampaignRenderingPlugin {

    public static final int STEPS_ACROSS = 16;
    public static final int STEPS_ALONG = 24;

    public static final float SUPERLUMINAL_TIME = 0.4f;
    private static final Color EDGE_TINT = new Color(230, 35, 255);

    private static final float BAND_CENTER = 0.62f;
    private static final float BAND_WIDTH = 0.38f;

    private final Vector2f origin;
    private final Vector2f aim;
    private final float size;
    private final float halfAngle;
    private final Color color;
    private final boolean tracksPlayerScale;

    private boolean expired = false;
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    private float timePassed = 0f;
    private float extraAlphaMult = 1f;
    private FlickerUtilV2 flicker = new FlickerUtilV2(8f);

    public SearchlightFanRenderer(Vector2f origin, Vector2f aim, float size, Color color) {
        this(origin, aim, size, Searchlight.getFanHalfAngle(), color, true);
    }

    public SearchlightFanRenderer(Vector2f origin, Vector2f aim, float size,
            float halfAngle, Color color) {
        this(origin, aim, size, halfAngle, color, false);
    }

    protected SearchlightFanRenderer(Vector2f origin, Vector2f aim, float size,
            float halfAngle, Color color, boolean tracksPlayerScale) {
        this.origin = origin;
        this.aim = aim;
        this.size = size;
        this.halfAngle = halfAngle;
        this.color = color;
        this.tracksPlayerScale = tracksPlayerScale;
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
        // spot's layer - the dents are cut one layer up; drawn level with it, they'd subtract from black underneath instead
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;

        // matches the spot's numbers exactly (flash, flicker) so the module changes shape, not amount
        float alpha;
        if (extraAlphaMult > 0) alpha = extraAlphaMult;
        else alpha = 0.12f - 0.04f * flicker.getBrightness();

        if (fading) {
            float fadeT = MathUtils.clamp(1f - (fadeElapsed / fadeDuration), 0f, 1f);
            alpha *= fadeT;
        }
        if (alpha <= 0f) return;

        // aim on top of the fleet has no direction to open around; catches a degenerate first frame
        float distance = Misc.getDistance(origin, aim);
        if (distance < 1f) return;

        drawWedge(Misc.getAngleInDegrees(origin, aim), distance + getSize(), distance, alpha);
    }

    protected void drawWedge(float direction, float length, float aimDistance, float alpha) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        float er = EDGE_TINT.getRed() / 255f;
        float eg = EDGE_TINT.getGreen() / 255f;
        float eb = EDGE_TINT.getBlue() / 255f;

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
                float t = i / (float) STEPS_ACROSS * 2f - 1f;

                double angle = Math.toRadians(direction + getHalfAngle() * t);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);

                float across = TrigHelper.smootherStep(1f - Math.abs(t));

                float rim = TrigHelper.smootherStep(
                        1f - Math.abs(Math.abs(t) - BAND_CENTER) / BAND_WIDTH);
                float vr = r + (er - r) * rim;
                float vg = g + (eg - g) * rim;
                float vb = b + (eb - b) * rim;

                GL11.glColor4f(vr, vg, vb, across * alongFar);
                GL11.glVertex2f(origin.x + cos * length * uFar, origin.y + sin * length * uFar);
                GL11.glColor4f(vr, vg, vb, across * alongNear);
                GL11.glVertex2f(origin.x + cos * length * uNear, origin.y + sin * length * uNear);
            }
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    protected float along(float u, float aimFract) {
        float base = Searchlight.FAN_TIP_STRENGTH
                + (1f - Searchlight.FAN_TIP_STRENGTH) * TrigHelper.smootherStep(1f - u);

        if (u <= aimFract) return base;

        return base * TrigHelper.smootherStep((1f - u) / (1f - aimFract));
    }

    protected float getSize() {
        return tracksPlayerScale ? Searchlight.getArea() : size;
    }

    protected float getHalfAngle() {
        return tracksPlayerScale ? Searchlight.getFanHalfAngle() : halfAngle;
    }
}
