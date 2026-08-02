package catchrelease.rendering.helper;

import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * A circle, filled or outlined, drawn where the caller says in whatever coordinates are current.
 * <p>
 * The filled form runs from one alpha at the middle to another at the rim, which is what a backlight
 * is: light with no edge to it. The outlined form is the same circle as a line, for when the edge is
 * the point.
 * <p>
 * All GL state this touches is pushed and popped, so anything drawn after the call is unaffected.
 */
public class Disc {

    /** Straight cuts around the circle. Thirty-two is smooth at any radius a panel uses, and cheap. */
    public static final int SEGMENTS = 32;

    /**
     * A fan from the middle out. Additive is what makes it read as light rather than as paint - use
     * it for anything meant to be glowing, and the normal blend for anything meant to be a surface.
     */
    public static void draw(float x, float y, float radius, Color color,
                            float centerAlpha, float edgeAlpha, boolean additive) {

        if (radius <= 0f || (centerAlpha <= 0f && edgeAlpha <= 0f)) return;

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(r, g, b, centerAlpha);
        GL11.glVertex2f(x, y);
        GL11.glColor4f(r, g, b, edgeAlpha);
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.toRadians(i * 360.0 / SEGMENTS);
            GL11.glVertex2f(x + (float) Math.cos(angle) * radius, y + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    public static void drawOutline(float x, float y, float radius, Color color, float alpha,
                                   float lineWidthPx) {

        if (radius <= 0f || alpha <= 0f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(lineWidthPx);

        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                alpha * (color.getAlpha() / 255f));

        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < SEGMENTS; i++) {
            double angle = Math.toRadians(i * 360.0 / SEGMENTS);
            GL11.glVertex2f(x + (float) Math.cos(angle) * radius, y + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }
}
