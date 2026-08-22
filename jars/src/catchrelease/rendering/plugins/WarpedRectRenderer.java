package catchrelease.rendering.plugins;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;

import java.awt.Color;


public class WarpedRectRenderer {

    public static void render(SpriteAPI sprite, WarpGrid warp,
                              float x, float y, float width, float height,
                              Color tint, float alpha, float zoom) {

        if (sprite == null || warp == null) return;
        if (width <= 0f || height <= 0f || alpha <= 0f) return;

        float spriteAspect = sprite.getHeight() <= 0f ? 1f : sprite.getWidth() / sprite.getHeight();
        float rectAspect = width / height;

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


    protected static void getVertex(WarpGrid warp, int i, int j,
                                    float x, float y, float width, float height,
                                    int wide, int tall,
                                    float u0, float v0, float uSpan, float vSpan, float[] out) {

        float alongX = i / (float) (wide - 1);
        float alongY = j / (float) (tall - 1);

        WarpGrid.WarpOffset offset = warp.getOffset(i, j);

        out[0] = x + alongX * width + offset.dx;
        out[1] = y + alongY * height + offset.dy;

        out[2] = u0 + alongX * uSpan;
        out[3] = v0 + (1f - alongY) * vSpan;
    }
}
