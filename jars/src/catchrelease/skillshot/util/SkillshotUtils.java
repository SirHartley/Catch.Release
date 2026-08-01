package catchrelease.skillshot.util;

import com.fs.starfarer.api.Global;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;
import catchrelease.skillshot.GuideLineStyle;

import java.awt.*;
import java.util.List;

public class SkillshotUtils {

    /**
     * The cursor position in campaign world coordinates. Both the reticules and the fire hook read
     * the aim point through here, so they can never disagree about where the player is pointing.
     */
    public static Vector2f getCursorWorldPosition() {
        return new Vector2f(
                Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
                Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));
    }

    /** Bits in a GL stipple pattern - the window the dash-plus-gap period has to fit into. */
    protected static final int STIPPLE_BITS = 16;

    /** Solid lines. */
    public static void drawLines(List<Vector2f> vertices, Color colour, float alpha, float widthPx) {
        drawLines(vertices, colour, alpha, widthPx, GuideLineStyle.SOLID);
    }

    /**
     * Draws straight lines in campaign world coordinates - call it from a renderer's render pass, the
     * campaign layers are already in world space.
     * <p>
     * All GL state this touches is pushed and popped, so sprites drawn after the call are unaffected.
     *
     * @param vertices consecutive pairs, i.e. start, end, start, end - a trailing odd vertex is
     *                 dropped
     * @param widthPx  line width in screen pixels, so it stays readable at any zoom level
     * @param style    dash pattern; its lengths are in screen pixels too, so the dashes do not thin
     *                 out as the map zooms
     */
    public static void drawLines(List<Vector2f> vertices, Color colour, float alpha, float widthPx, GuideLineStyle style) {
        if (vertices == null || vertices.size() < 2) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(widthPx);

        if (style != null && style != GuideLineStyle.SOLID) {
            GL11.glEnable(GL11.GL_LINE_STIPPLE);
            GL11.glLineStipple(getStippleFactor(style), getStipplePattern(style));
        }
        GL11.glColor4f(colour.getRed() / 255f, colour.getGreen() / 255f, colour.getBlue() / 255f,
                alpha * (colour.getAlpha() / 255f));

        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i + 1 < vertices.size(); i += 2) {
            Vector2f from = vertices.get(i);
            Vector2f to = vertices.get(i + 1);

            GL11.glVertex2f(from.x, from.y);
            GL11.glVertex2f(to.x, to.y);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    /**
     * Screen pixels each bit of the stipple pattern covers. Picked so one dash plus its gap fills the
     * whole 16-bit pattern, which is what makes the dashes tile evenly down the line.
     */
    protected static int getStippleFactor(GuideLineStyle style) {
        float period = style.getSegmentPx() + style.getGapPx();
        if (period <= 0f) return 1;

        return Math.max(1, Math.min(256, Math.round(period / STIPPLE_BITS)));
    }

    /**
     * The pattern itself: the dash's share of the period as a run of low bits, the gap left unset. GL
     * reads it from the low bit up, so a solid run is all the pattern needs to be.
     */
    protected static short getStipplePattern(GuideLineStyle style) {
        float period = style.getSegmentPx() + style.getGapPx();
        if (period <= 0f) return (short) 0xFFFF;

        int onBits = Math.round(STIPPLE_BITS * style.getSegmentPx() / period);
        onBits = Math.max(1, Math.min(STIPPLE_BITS - 1, onBits));

        return (short) ((1 << onBits) - 1);
    }
}
