package catchrelease.skillshot.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;
import catchrelease.skillshot.GuideLineStyle;

import java.awt.*;
import java.util.ArrayList;
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

    /** Two points count as the same corner, and so as one continuous path, within this many units. */
    protected static final float JOIN_TOLERANCE = 0.01f;

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

        List<Vector2f> toDraw = cutDashes(vertices, style);
        if (toDraw.size() < 2) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(widthPx);

        GL11.glColor4f(colour.getRed() / 255f, colour.getGreen() / 255f, colour.getBlue() / 255f,
                alpha * (colour.getAlpha() / 255f));

        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i + 1 < toDraw.size(); i += 2) {
            Vector2f from = toDraw.get(i);
            Vector2f to = toDraw.get(i + 1);

            GL11.glVertex2f(from.x, from.y);
            GL11.glVertex2f(to.x, to.y);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    /**
     * Cuts a run of lines into the dashes a style asks for, as more lines.
     * <p>
     * Cut here rather than left to GL_LINE_STIPPLE, which cannot do this job: GL restarts the stipple
     * pattern at every segment of a GL_LINES batch, and a pattern always starts on a drawn bit. Every
     * segment shorter than one dash therefore comes out solid - and a ring built from 72 short
     * segments is nothing but those, which is why it drew as an unbroken circle.
     * <p>
     * Dash lengths are in screen pixels and converted here, so they hold their look at any zoom.
     *
     * @return the vertices to draw, as pairs; the input unchanged for a solid style
     */
    protected static List<Vector2f> cutDashes(List<Vector2f> vertices, GuideLineStyle style) {
        if (style == null || style == GuideLineStyle.SOLID) return vertices;

        ViewportAPI viewport = Global.getSector().getViewport();
        if (viewport == null) return vertices;

        float dash = viewport.convertScreenWidthToWorldWidth(style.getSegmentPx());
        float gap = viewport.convertScreenWidthToWorldWidth(style.getGapPx());
        if (dash <= 0f || gap <= 0f) return vertices;

        float period = dash + gap;

        List<Vector2f> dashes = new ArrayList<>();
        Vector2f previousEnd = null;
        float phase = 0f;

        for (int i = 0; i + 1 < vertices.size(); i += 2) {
            Vector2f from = vertices.get(i);
            Vector2f to = vertices.get(i + 1);

            //a break in the run starts the pattern over; segments that carry on from the last one
            //keep its phase, so dashes run evenly along a path however finely it is chopped up
            if (previousEnd == null || !isSamePoint(previousEnd, from)) phase = 0f;

            phase = cutSegment(dashes, from, to, dash, period, phase);
            previousEnd = to;
        }

        return dashes;
    }

    /**
     * Cuts one segment into dashes, starting {@code phase} units into the pattern.
     *
     * @return how far into the pattern the segment ended, for the next one to carry on from
     */
    protected static float cutSegment(List<Vector2f> dashes, Vector2f from, Vector2f to,
                                      float dash, float period, float phase) {
        Vector2f along = Vector2f.sub(to, from, null);
        float length = along.length();
        if (length <= 0f) return phase;

        along.scale(1f / length);

        float end = phase + length;

        //every period that overlaps this segment contributes the drawn part of itself that lands on it
        for (float start = phase - phase % period; start < end; start += period) {
            float dashFrom = Math.max(phase, start);
            float dashTo = Math.min(end, start + dash);
            if (dashTo <= dashFrom) continue;

            dashes.add(pointAlong(from, along, dashFrom - phase));
            dashes.add(pointAlong(from, along, dashTo - phase));
        }

        //kept inside one period, so a long path does not lose precision counting up
        return end % period;
    }

    protected static Vector2f pointAlong(Vector2f from, Vector2f direction, float distance) {
        return new Vector2f(from.x + direction.x * distance, from.y + direction.y * distance);
    }

    protected static boolean isSamePoint(Vector2f a, Vector2f b) {
        return Math.abs(a.x - b.x) <= JOIN_TOLERANCE && Math.abs(a.y - b.y) <= JOIN_TOLERANCE;
    }
}
