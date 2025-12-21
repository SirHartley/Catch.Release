package catchrelease.rendering;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import java.io.IOException;

public class MaskedWarpedSpriteRenderer {

    private final WarpGrid warp;

    private transient int program = 0;
    private transient int uTex = -1;
    private transient int uMaskTex = -1;
    private transient int uAlphaMult = -1;
    private transient int uMaskThreshold = -1;

    private float maskThreshold = 0f;

    public MaskedWarpedSpriteRenderer(WarpGrid warp) {
        this.warp = warp;
    }

    public void setMaskThreshold(float threshold) {
        this.maskThreshold = threshold;
    }

    public void render(SpriteAPI fillSprite,
                       SpriteAPI maskSprite,
                       Vector2f center,
                       float fillSize,
                       float maskSize,
                       float alphaMult,
                       Vector2f fillUvOffsetPx) {

        if (fillSprite == null || maskSprite == null || center == null) return;
        if (fillSize <= 0f || maskSize <= 0f) return;

        ensureShader();
        if (program == 0) return;

        float uOff = 0f;
        float vOff = 0f;
        if (fillUvOffsetPx != null) {
            uOff = fillUvOffsetPx.x;
            vOff = fillUvOffsetPx.y;
        }

        GL20.glUseProgram(program);

        if (uAlphaMult >= 0) GL20.glUniform1f(uAlphaMult, alphaMult);
        if (uMaskThreshold >= 0) GL20.glUniform1f(uMaskThreshold, maskThreshold);

        // unit0 = fill
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        fillSprite.bindTexture();

        // unit1 = mask
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        maskSprite.bindTexture();

        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        float w = fillSize;
        float h = fillSize;

        // Pixel-space UV extents for fill
        float fillTW = fillSprite.getTextureWidth() - 0.001f;
        float fillTH = fillSprite.getTextureHeight() - 0.001f;

        // Used to convert world-space warp offsets into fill texcoord offsets
        float fillUvPerWorldX = fillTW / fillSize;
        float fillUvPerWorldY = fillTH / fillSize;

        GL11.glPushMatrix();
        GL11.glTranslatef(center.x - w * 0.5f, center.y - h * 0.5f, 0f);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (warp == null) {
            float fillTX0 = 0f + uOff;
            float fillTY0 = 0f + vOff;
            float fillTX1 = fillTW + uOff;
            float fillTY1 = fillTH + vOff;

            // Mask UV in normalized 0..1 for a centered mask square of size maskSize inside fillSize
            // For corners of the fill quad:
            // x,y in [0, fillSize] -> maskUV = (pos - (fillSize-maskSize)/2) / maskSize (thx chatgpt)
            float inset = (fillSize - maskSize) * 0.5f;

            float mU0 = (0f - inset) / maskSize;
            float mV0 = (0f - inset) / maskSize;
            float mU1 = (fillSize - inset) / maskSize;
            float mV1 = (fillSize - inset) / maskSize;

            GL11.glBegin(GL11.GL_QUADS);
            {
                //bottom left
                GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, fillTX0, fillTY0);
                GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, mU0, mV0);
                GL11.glVertex2f(0f, 0f);

                //bottom right
                GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, fillTX1, fillTY0);
                GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, mU1, mV0);
                GL11.glVertex2f(w, 0f);

                //top right
                GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, fillTX1, fillTY1);
                GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, mU1, mV1);
                GL11.glVertex2f(w, h);

                //top left
                GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, fillTX0, fillTY1);
                GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, mU0, mV1);
                GL11.glVertex2f(0f, h);
            }
            GL11.glEnd();

        } else {
            int wide = warp.getWide(); //warp applied to fill
            int tall = warp.getTall();

            float cw = w / (float) (wide - 1);
            float ch = h / (float) (tall - 1);

            float fillCTW = fillTW / (float) (wide - 1);
            float fillCTH = fillTH / (float) (tall - 1);

            float inset = (fillSize - maskSize) * 0.5f;

            for (int i = 0; i < wide - 1; i++) {
                GL11.glBegin(GL11.GL_QUAD_STRIP);
                for (int j = 0; j < tall; j++) {

                    //mask / unwarped
                    float x1 = cw * i;
                    float y1 = ch * j;
                    float x2 = cw * (i + 1f);
                    float y2 = ch * j;

                    //fill base UVs (pixel-space) + parallax offset
                    float fU1 = fillCTW * i + uOff;
                    float fV1 = fillCTH * j + vOff;
                    float fU2 = fillCTW * (i + 1f) + uOff;
                    float fV2 = fillCTH * j + vOff;

                    //warp offsets converted to fill UV offsets (pixel-space)
                    WarpGrid.WarpOffset o1 = warp.getOffset(i, j);
                    WarpGrid.WarpOffset o2 = warp.getOffset(i + 1, j);

                    fU1 += o1.dx * fillUvPerWorldX;
                    fV1 += o1.dy * fillUvPerWorldY;
                    fU2 += o2.dx * fillUvPerWorldX;
                    fV2 += o2.dy * fillUvPerWorldY;

                    //unwarped mask UVs (normalized 0..1 in mask space)
                    float mU1 = (x1 - inset) / maskSize;
                    float mV1 = (y1 - inset) / maskSize;
                    float mU2 = (x2 - inset) / maskSize;
                    float mV2 = (y2 - inset) / maskSize;

                    GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, fU1, fV1);
                    GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, mU1, mV1);
                    GL11.glVertex2f(x1, y1);

                    GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, fU2, fV2);
                    GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, mU2, mV2);
                    GL11.glVertex2f(x2, y2);
                }
                GL11.glEnd();
            }
        }

        GL11.glPopMatrix();

        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private void ensureShader() {
        if (program != 0) return;

        final String vert;
        final String frag;

        try {
            vert = Global.getSettings().loadText("data/catchrelease/shaders/masked_warp_vertex.shader");
            frag = Global.getSettings().loadText("data/catchrelease/shaders/masked_warp_fragment.shader");
        } catch (IOException e) {
            throw new RuntimeException("Shaders not found: Catch.Release", e);
        }

        program = ShaderLib.loadShader(vert, frag);
        if (program == 0) return;

        GL20.glUseProgram(program);

        uTex = GL20.glGetUniformLocation(program, "tex");
        uMaskTex = GL20.glGetUniformLocation(program, "maskTex");
        uAlphaMult = GL20.glGetUniformLocation(program, "alphaMult");
        uMaskThreshold = GL20.glGetUniformLocation(program, "maskThreshold");

        if (uTex >= 0) GL20.glUniform1i(uTex, 0);
        if (uMaskTex >= 0) GL20.glUniform1i(uMaskTex, 1);
        if (uAlphaMult >= 0) GL20.glUniform1f(uAlphaMult, 1f);

        GL20.glUseProgram(0);
    }
}
