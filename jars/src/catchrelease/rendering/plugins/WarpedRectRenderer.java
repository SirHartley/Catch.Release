package catchrelease.rendering.plugins;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * Fills a rectangle with a sprite, warping it as it goes.
 * <p>
 * The rectangle is cut into a grid and the inner vertices are pushed around by a {@link WarpGrid},
 * which is what makes the image swim. The outer ones are not: {@link WarpGrid#getOffset} pins its
 * border, so the fill stays exactly inside the rectangle it was given - no clipping, no mask and no
 * shader needed, which is what makes this usable from a UI panel's render pass.
 * <p>
 * The sprite is fitted the way a background is: scaled to cover the rectangle and centred, so it
 * fills without ever being stretched out of shape.
 * <p>
 * All GL state this touches is pushed and popped.
 */
public class WarpedRectRenderer {

    public static void render(SpriteAPI sprite, WarpGrid warp,
                              float x, float y, float width, float height,
                              Color tint, float alpha, float zoom) {

        if (sprite == null || warp == null) return;
        if (width <= 0f || height <= 0f || alpha <= 0f) return;

        float spriteAspect = sprite.getHeight() <= 0f ? 1f : sprite.getWidth() / sprite.getHeight();
        float rectAspect = width / height;

        //cover: take the widest strip of the sprite that keeps its shape at this rectangle's aspect
        float uSpan = 1f;
        float vSpan = 1f;
        if (rectAspect < spriteAspect) uSpan = rectAspect / spriteAspect;
        else vSpan = spriteAspect / rectAspect;

        if (zoom > 0f) {
            uSpan /= zoom;
            vSpan /= zoom;
        }

        float u0 = (1f - uSpan) * 0.5f;
        float v0 = (1f - vSpan) * 0.5f;

        int wide = warp.getWide();
        int tall = warp.getTall();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        sprite.bindTexture();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(tint.getRed() / 255f, tint.getGreen() / 255f, tint.getBlue() / 255f, alpha);

        float[] vertex = new float[4];

        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < wide - 1; i++) {
            for (int j = 0; j < tall - 1; j++) {
                emit(warp, i, j, x, y, width, height, wide, tall, u0, v0, uSpan, vSpan, vertex);
                emit(warp, i + 1, j, x, y, width, height, wide, tall, u0, v0, uSpan, vSpan, vertex);
                emit(warp, i + 1, j + 1, x, y, width, height, wide, tall, u0, v0, uSpan, vSpan, vertex);
                emit(warp, i, j + 1, x, y, width, height, wide, tall, u0, v0, uSpan, vSpan, vertex);
            }
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    protected static void emit(WarpGrid warp, int i, int j,
                               float x, float y, float width, float height,
                               int wide, int tall,
                               float u0, float v0, float uSpan, float vSpan, float[] vertex) {

        getVertex(warp, i, j, x, y, width, height, wide, tall, u0, v0, uSpan, vSpan, vertex);

        GL11.glTexCoord2f(vertex[2], vertex[3]);
        GL11.glVertex2f(vertex[0], vertex[1]);
    }

    /**
     * One grid vertex, as {x, y, u, v}: the texture is sampled where the vertex belongs, and the
     * vertex is drawn where the warp has pushed it. Sampling straight and drawing bent is what bends
     * the image.
     * <p>
     * Written into the array handed in rather than returned, so a frame's worth of vertices costs no
     * allocations - and so the shape can be looked at without a GL context in front of it.
     */
    protected static void getVertex(WarpGrid warp, int i, int j,
                                    float x, float y, float width, float height,
                                    int wide, int tall,
                                    float u0, float v0, float uSpan, float vSpan, float[] out) {

        float alongX = i / (float) (wide - 1);
        float alongY = j / (float) (tall - 1);

        WarpGrid.WarpOffset offset = warp.getOffset(i, j);

        out[0] = x + alongX * width + offset.dx;
        out[1] = y + alongY * height + offset.dy;

        //texture v runs down the image while the rectangle's y runs up it
        out[2] = u0 + alongX * uSpan;
        out[3] = v0 + (1f - alongY) * vSpan;
    }
}
