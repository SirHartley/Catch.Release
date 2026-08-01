package catchrelease.skillshot.util;

import com.fs.starfarer.api.Global;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

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

    /**
     * Draws straight lines in campaign world coordinates - call it from a renderer's render pass, the
     * campaign layers are already in world space.
     * <p>
     * All GL state this touches is pushed and popped, so sprites drawn after the call are unaffected.
     *
     * @param vertices consecutive pairs, i.e. start, end, start, end - a trailing odd vertex is
     *                 dropped
     * @param widthPx  line width in screen pixels, so it stays readable at any zoom level
     */
    public static void drawLines(List<Vector2f> vertices, Color colour, float alpha, float widthPx) {
        if (vertices == null || vertices.size() < 2) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(widthPx);
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
}
