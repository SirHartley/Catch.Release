package catchrelease.rendering.helper;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * A rounded rectangle outline, drawn in whatever coordinate space is current (screen or world).
 * Matches vanilla's panel styling; a second call a few units out gives it depth. All touched GL
 * state is pushed and popped.
 */
public class RoundedBorder {

    /** Straight cuts per corner. Six is already smooth at the radii a UI uses, and cheap. */
    public static final int CORNER_SEGMENTS = 6;

    public static void draw(float x, float y, float width, float height, float radius,
                            Color color, float alpha, float lineWidthPx) {
        if (width <= 0f || height <= 0f || alpha <= 0f) return;

        List<Vector2f> outline = getOutline(x, y, width, height, radius);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(lineWidthPx);

        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                alpha * (color.getAlpha() / 255f));

        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (Vector2f point : outline) {
            GL11.glVertex2f(point.x, point.y);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    /** Radius is clamped to what fits, so a narrow bar gets the tightest corners its width allows. */
    protected static List<Vector2f> getOutline(float x, float y, float width, float height, float radius) {
        float r = Math.max(0f, Math.min(radius, Math.min(width, height) * 0.5f));

        List<Vector2f> outline = new ArrayList<>();

        if (r <= 0f) {
            outline.add(new Vector2f(x, y));
            outline.add(new Vector2f(x + width, y));
            outline.add(new Vector2f(x + width, y + height));
            outline.add(new Vector2f(x, y + height));
            return outline;
        }

        addCorner(outline, x + r, y + r, r, 180f);                    //bottom left
        addCorner(outline, x + width - r, y + r, r, 270f);            //bottom right
        addCorner(outline, x + width - r, y + height - r, r, 0f);     //top right
        addCorner(outline, x + r, y + height - r, r, 90f);            //top left

        return outline;
    }

    /** A quarter turn about a corner's centre, starting where the last edge left off. */
    protected static void addCorner(List<Vector2f> outline, float centerX, float centerY, float radius,
                                    float startAngle) {
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            double angle = Math.toRadians(startAngle + 90f * i / (float) CORNER_SEGMENTS);

            outline.add(new Vector2f(
                    centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius));
        }
    }
}
