package catchrelease.rendering;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.io.IOException;

public class MaskGlowRenderer {

    private transient int program = 0;
    private transient int uMaskTex = -1;
    private transient int uGlowColor = -1;
    private transient int uGlowAlpha = -1;

    private transient int uThreshold = -1;      // optional shaping threshold
    private transient int uRadiusOutPx = -1;    // new
    private transient int uRadiusInPx = -1;     // new
    private transient int uMaskTexelSize = -1;

    private float threshold = 0f;

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    public void renderAdditive(SpriteAPI maskSprite,
                               Vector2f center,
                               float size,
                               Color glowColor,
                               float glowAlpha,
                               float radiusOutPx,
                               float radiusInPx) {

        if (maskSprite == null || center == null || glowColor == null) return;
        if (size <= 0f || glowAlpha <= 0f) return;
        if (radiusOutPx <= 0f && radiusInPx <= 0f) return;

        ensureShader();
        if (program == 0) return;

        GL20.glUseProgram(program);

        float texelX = 1f / maskSprite.getTextureWidth();
        float texelY = 1f / maskSprite.getTextureHeight();

        if (uMaskTexelSize >= 0) GL20.glUniform2f(uMaskTexelSize, texelX, texelY);

        if (uGlowColor >= 0) {
            GL20.glUniform3f(uGlowColor,
                    glowColor.getRed() / 255f,
                    glowColor.getGreen() / 255f,
                    glowColor.getBlue() / 255f);
        }

        if (uGlowAlpha >= 0) GL20.glUniform1f(uGlowAlpha, glowAlpha);

        // Keep for compatibility. If shader does not declare it, location will be -1.
        if (uThreshold >= 0) GL20.glUniform1f(uThreshold, threshold);

        if (uRadiusOutPx >= 0) GL20.glUniform1f(uRadiusOutPx, radiusOutPx);
        if (uRadiusInPx >= 0) GL20.glUniform1f(uRadiusInPx, radiusInPx);

        // Bind mask on unit 1
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        maskSprite.bindTexture();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        float w = size;
        float h = size;

        GL11.glPushMatrix();
        GL11.glTranslatef(center.x - w * 0.5f, center.y - h * 0.5f, 0f);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);

        // Additive color
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // Normalized mask UVs 0..1
        GL11.glBegin(GL11.GL_QUADS);
        {
            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, 0f, 0f); GL11.glVertex2f(0f, 0f);
            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, 1f, 0f); GL11.glVertex2f(w, 0f);
            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, 1f, 1f); GL11.glVertex2f(w, h);
            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, 0f, 1f); GL11.glVertex2f(0f, h);
        }
        GL11.glEnd();

        GL11.glPopMatrix();

        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private void ensureShader() {
        if (program != 0) return;

        final String vert;
        final String frag;

        try {
            vert = Global.getSettings().loadText("data/catchrelease/shaders/mask_glow_vertex.shader");
            frag = Global.getSettings().loadText("data/catchrelease/shaders/mask_glow_fragment.shader");
        } catch (IOException e) {
            throw new RuntimeException("Glow shaders not found: Catch.Release", e);
        }

        program = ShaderLib.loadShader(vert, frag);
        if (program == 0) return;

        GL20.glUseProgram(program);

        uMaskTex = GL20.glGetUniformLocation(program, "maskTex");
        uGlowColor = GL20.glGetUniformLocation(program, "glowColor");
        uGlowAlpha = GL20.glGetUniformLocation(program, "glowAlpha");

        // Optional / shader-dependent
        uThreshold = GL20.glGetUniformLocation(program, "threshold");
        uRadiusOutPx = GL20.glGetUniformLocation(program, "radiusOutPx");
        uRadiusInPx = GL20.glGetUniformLocation(program, "radiusInPx");
        uMaskTexelSize = GL20.glGetUniformLocation(program, "maskTexelSize");

        if (uMaskTex >= 0) GL20.glUniform1i(uMaskTex, 1);

        GL20.glUseProgram(0);
    }
}
