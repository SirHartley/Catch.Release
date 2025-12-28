package catchrelease.rendering.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.io.IOException;

public class NoiseMappedCircularRingRenderer {
    private transient int program = 0;

    private transient int uNoiseTex = -1;
    private transient int uTime = -1;
    private transient int uAlphaMult = -1;

    private transient int uRadius = -1;
    private transient int uRingWidth = -1;
    private transient int uFeather = -1;

    private transient int uAngularTiling = -1;
    private transient int uRadialTiling = -1;
    private transient int uNoiseScroll = -1;

    private transient int uNoiseCutoff = -1;
    private transient int uNoiseSoft = -1;

    private transient int uRingColor = -1;

    public void renderRing(SpriteAPI noiseSprite,
                           Vector2f center,
                           float sizePx,
                           float timeSec,
                           float alphaMult,
                           float radiusUv,
                           float ringWidthUv,
                           float featherUv,
                           float angularTiling,
                           float radialTiling,
                           float noiseScroll,
                           float noiseCutoff,
                           float noiseSoft,
                           Color color) {

        if (noiseSprite == null || center == null) return;
        if (sizePx <= 0f) return;

        ensureShader();
        if (program == 0) return;

        GL20.glUseProgram(program);

        if (uTime >= 0) GL20.glUniform1f(uTime, timeSec);
        if (uAlphaMult >= 0) GL20.glUniform1f(uAlphaMult, alphaMult);

        if (uRadius >= 0) GL20.glUniform1f(uRadius, radiusUv);
        if (uRingWidth >= 0) GL20.glUniform1f(uRingWidth, ringWidthUv);
        if (uFeather >= 0) GL20.glUniform1f(uFeather, featherUv);

        if (uAngularTiling >= 0) GL20.glUniform1f(uAngularTiling, angularTiling);
        if (uRadialTiling >= 0) GL20.glUniform1f(uRadialTiling, radialTiling);
        if (uNoiseScroll >= 0) GL20.glUniform1f(uNoiseScroll, noiseScroll);

        if (uNoiseCutoff >= 0) GL20.glUniform1f(uNoiseCutoff, noiseCutoff);
        if (uNoiseSoft >= 0) GL20.glUniform1f(uNoiseSoft, noiseSoft);

        if (uRingColor >= 0) GL20.glUniform4f(uRingColor, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);

        // unit0 = noise
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        noiseSprite.bindTexture();

        float w = sizePx;
        float h = sizePx;

        GL11.glPushMatrix();
        GL11.glTranslatef(center.x - w * 0.5f, center.y - h * 0.5f, 0f);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        //GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        GL11.glBegin(GL11.GL_QUADS);
        {
            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0f, 0f);
            GL11.glVertex2f(0f, 0f);

            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, 1f, 0f);
            GL11.glVertex2f(w, 0f);

            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, 1f, 1f);
            GL11.glVertex2f(w, h);

            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0f, 1f);
            GL11.glVertex2f(0f, h);
        }
        GL11.glEnd();

        GL11.glPopMatrix();

        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    //This needs to be fixed holy shit
    private void ensureShader() {
        if (program != 0) return;

        final String vert;
        final String frag;
        try {
            vert = Global.getSettings().loadText("data/catchrelease/shaders/ripple_circle_vertex.shader");
            frag = Global.getSettings().loadText("data/catchrelease/shaders/ripple_circle_fragment.shader");
        } catch (IOException e) {
            throw new RuntimeException("Ripple ring shaders not found: Catch.Release", e);
        }

        program = ShaderLib.loadShader(vert, frag);
        if (program == 0) return;

        GL20.glUseProgram(program);

        uNoiseTex = GL20.glGetUniformLocation(program, "noiseTex");
        uTime = GL20.glGetUniformLocation(program, "time");
        uAlphaMult = GL20.glGetUniformLocation(program, "alphaMult");

        uRadius = GL20.glGetUniformLocation(program, "radius");
        uRingWidth = GL20.glGetUniformLocation(program, "ringWidth");
        uFeather = GL20.glGetUniformLocation(program, "feather");

        uAngularTiling = GL20.glGetUniformLocation(program, "angularTiling");
        uRadialTiling = GL20.glGetUniformLocation(program, "radialTiling");
        uNoiseScroll = GL20.glGetUniformLocation(program, "noiseScroll");

        uNoiseCutoff = GL20.glGetUniformLocation(program, "noiseCutoff");
        uNoiseSoft = GL20.glGetUniformLocation(program, "noiseSoft");

        uRingColor = GL20.glGetUniformLocation(program, "ringColor");

        if (uNoiseTex >= 0) GL20.glUniform1i(uNoiseTex, 0);

        GL20.glUseProgram(0);
    }
}
