package catchrelease.campaign.ponds.renderer;

import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.rendering.helper.Stencil;
import catchrelease.rendering.plugins.WarpGrid;
import catchrelease.rendering.plugins.WarpedRectRenderer;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * The pond as a hole: mask stencilled out of the fabric, warping hyperspace showing through,
 * shaded dark at center and at the rim to read as an aperture. No shader - built from
 * {@link Stencil} and {@link WarpedRectRenderer}, both already used elsewhere. Alternative to the
 * shader-based look behind {@link PondConstants#POND_HOLE_LOOK}.
 */
public class PondHoleRenderer {

    public static final int FAN_SEGMENTS = 48;

    /**
     * @param maskSize the mask quad's edge length - the rupture as currently open
     * @param elapsed  the pond's own clock, for the background's slow wander
     */
    public void render(SpriteAPI starfield, SpriteAPI mask, WarpGrid warp, Vector2f loc,
                       float maskSize, float alpha, float elapsed) {

        if (starfield == null || mask == null || warp == null || alpha <= 0f || maskSize <= 0f) return;

        Stencil.startStencil(mask, maskSize, maskSize, loc, false);

        float radius = maskSize * 0.5f;

        //opaque floor so the hole never shows terrain through a thin background
        drawDisc(loc, radius * 1.05f, 0f, Color.BLACK, 0.95f * alpha, Color.BLACK, 0.95f * alpha);

        //own drift motion since a camera snapped to the pond kills parallax (see PondDepthField)
        float driftPhase = elapsed * (float) (Math.PI * 2.0) / PondConstants.HOLE_DRIFT_PERIOD;
        float driftX = (float) Math.cos(driftPhase) * PondConstants.HOLE_DRIFT;
        float driftY = (float) Math.sin(driftPhase * 0.7f) * PondConstants.HOLE_DRIFT;

        float fill = maskSize * PondConstants.HOLE_FILL_MULT;

        WarpedRectRenderer.render(starfield, warp,
                loc.x - fill * 0.5f + driftX, loc.y - fill * 0.5f + driftY, fill, fill,
                PondConstants.HOLE_BG_TINT, PondConstants.HOLE_BG_ALPHA * alpha,
                PondConstants.HOLE_BG_ZOOM);

        //funnel: dark pooling at center, thinning outward, reads as depth
        drawDisc(loc, radius * PondConstants.HOLE_WELL_REACH, 0f,
                Color.BLACK, PondConstants.HOLE_WELL_ALPHA * alpha, Color.BLACK, 0f);

        //wall shadow just inside the rim, reads as an aperture edge
        drawRing(loc, radius * PondConstants.HOLE_RIM_START, radius * 1.02f,
                Color.BLACK, 0f, PondConstants.HOLE_RIM_SHADOW * alpha);

        Stencil.endStencil();
    }

    /** A radial gradient disc: one colour and alpha at the middle, another at the edge. */
    protected void drawDisc(Vector2f center, float radius, float innerRadius,
                            Color centerColor, float centerAlpha, Color edgeColor, float edgeAlpha) {
        if (radius <= 0f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);

        GL11.glColor4f(centerColor.getRed() / 255f, centerColor.getGreen() / 255f,
                centerColor.getBlue() / 255f, centerAlpha);
        GL11.glVertex2f(center.x, center.y);

        GL11.glColor4f(edgeColor.getRed() / 255f, edgeColor.getGreen() / 255f,
                edgeColor.getBlue() / 255f, edgeAlpha);

        for (int i = 0; i <= FAN_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / FAN_SEGMENTS;
            GL11.glVertex2f(center.x + (float) Math.cos(angle) * radius,
                    center.y + (float) Math.sin(angle) * radius);
        }

        GL11.glEnd();
        GL11.glPopAttrib();
    }

    /** A gradient ring: transparent at the inner radius, shaded at the outer one. */
    protected void drawRing(Vector2f center, float innerRadius, float outerRadius,
                            Color color, float innerAlpha, float outerAlpha) {
        if (outerRadius <= innerRadius) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);

        for (int i = 0; i <= FAN_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / FAN_SEGMENTS;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            GL11.glColor4f(r, g, b, innerAlpha);
            GL11.glVertex2f(center.x + cos * innerRadius, center.y + sin * innerRadius);

            GL11.glColor4f(r, g, b, outerAlpha);
            GL11.glVertex2f(center.x + cos * outerRadius, center.y + sin * outerRadius);
        }

        GL11.glEnd();
        GL11.glPopAttrib();
    }
}
