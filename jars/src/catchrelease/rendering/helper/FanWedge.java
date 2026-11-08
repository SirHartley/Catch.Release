package catchrelease.rendering.helper;

import catchrelease.helper.math.TrigHelper;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

// the fan beam's wedge, shared by the campaign lamp and the Monstrance's combat lights
public final class FanWedge {

    public static final int STEPS_ACROSS = 16;
    public static final int STEPS_ALONG = 24;

    public static final float FLASH_TIME = 0.4f;
    public static final float FLASH_ALPHA = 0.8f;
    public static final float REST_ALPHA = 0.12f;
    public static final float REST_FLICKER = 0.04f;

    private static final Color EDGE_TINT = new Color(230, 35, 255);
    private static final float BAND_CENTER = 0.62f;
    private static final float BAND_WIDTH = 0.38f;

    private FanWedge() {
    }

    public static float flashAlpha(float timeLit) {
        return FLASH_ALPHA * TrigHelper.smootherStep(1f - Math.min(timeLit / FLASH_TIME, 1f));
    }

    public static float restAlpha(float flickerBrightness) {
        return REST_ALPHA - REST_FLICKER * flickerBrightness;
    }

    public static void drawLight(Vector2f origin, float direction, float length, float aimDistance,
                                 float halfAngle, float tipStrength, Color color, float alpha) {
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

            float alongNear = along(uNear, aimFract, tipStrength) * alpha;
            float alongFar = along(uFar, aimFract, tipStrength) * alpha;

            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= STEPS_ACROSS; i++) {
                float t = i / (float) STEPS_ACROSS * 2f - 1f;

                double angle = Math.toRadians(direction + halfAngle * t);
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

    // the fill tiles at one texel per world unit, offset by uOff and vOff in texture units
    public static void drawFill(Vector2f origin, float direction, float length, float aimDistance,
                                float halfAngle, float tipStrength, SpriteAPI fill,
                                float uOff, float vOff, float alpha) {
        float fillSizeWorld = fill.getWidth();
        float aimFract = aimDistance / length;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        fill.bindTexture();

        // world-coordinate uvs run past [0,1], so the texture has to tile
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int band = 0; band < STEPS_ALONG; band++) {
            float uNear = band / (float) STEPS_ALONG;
            float uFar = (band + 1) / (float) STEPS_ALONG;

            float alongNear = along(uNear, aimFract, tipStrength) * alpha;
            float alongFar = along(uFar, aimFract, tipStrength) * alpha;

            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= STEPS_ACROSS; i++) {
                float t = i / (float) STEPS_ACROSS * 2f - 1f;

                double angle = Math.toRadians(direction + halfAngle * t);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);

                float across = TrigHelper.smootherStep(1f - Math.abs(t));

                emitFill(origin.x + cos * length * uFar, origin.y + sin * length * uFar,
                        fillSizeWorld, uOff, vOff, across * alongFar);
                emitFill(origin.x + cos * length * uNear, origin.y + sin * length * uNear,
                        fillSizeWorld, uOff, vOff, across * alongNear);
            }
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    // eases from full at the lamp toward tipStrength, and past the aim point out to nothing at the tip
    public static float along(float u, float aimFract, float tipStrength) {
        float base = tipStrength + (1f - tipStrength) * TrigHelper.smootherStep(1f - u);

        if (u <= aimFract) return base;

        return base * TrigHelper.smootherStep((1f - u) / (1f - aimFract));
    }

    private static void emitFill(float x, float y, float fillSizeWorld, float uOff, float vOff, float alpha) {
        GL11.glColor4f(1f, 1f, 1f, alpha);
        GL11.glTexCoord2f(x / fillSizeWorld + uOff, y / fillSizeWorld + vOff);
        GL11.glVertex2f(x, y);
    }
}
