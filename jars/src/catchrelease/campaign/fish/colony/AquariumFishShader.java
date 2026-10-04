package catchrelease.campaign.fish.colony;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;

public final class AquariumFishShader {

    public static final float START_ABERRATION = 0.12f;
    public static final String VERT = "data/catchrelease/shaders/aquarium_fish_vertex.shader";
    public static final String FRAG = "data/catchrelease/shaders/aquarium_fish_fragment.shader";

    private static boolean attempted;
    private static boolean validated;
    private static int program;
    private static int uTexture, uRegion, uTexel, uSize, uStrength, uTime, uPhase;

    private AquariumFishShader() {
    }

    public static float strength(float aberration) {
        if (!Float.isFinite(aberration)) return 0f;
        return Math.max(0f, Math.min(1f,
                (aberration - START_ABERRATION) / (1f - START_ABERRATION)));
    }

    // Returns the program to restore, or -1 when drawing without the filter.
    public static int begin(SpriteAPI sprite, float aberration, float time, float phase,
                            float width, float height) {
        float strength = strength(aberration);
        if (strength <= 0f || !ShaderLib.areShadersAllowed() || !load()) return -1;

        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        try {
            GL20.glUniform1i(uTexture, 0);
            GL20.glUniform4f(uRegion, sprite.getTexX(), sprite.getTexY(),
                    sprite.getTexWidth(), sprite.getTexHeight());
            GL20.glUniform2f(uTexel, 1f / Math.max(1f, sprite.getWidth()),
                    1f / Math.max(1f, sprite.getHeight()));
            GL20.glUniform2f(uSize, Math.max(1f, width), Math.max(1f, height));
            GL20.glUniform1f(uStrength, strength);
            GL20.glUniform1f(uTime, time);
            GL20.glUniform1f(uPhase, phase);

            if (!validated) {
                GL20.glValidateProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
                    throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
                }
                validated = true;
            }
            return previous;
        } catch (RuntimeException e) {
            GL20.glUseProgram(previous);
            GL20.glDeleteProgram(program);
            program = 0;
            Global.getLogger(AquariumFishShader.class).warn("Aquarium fish filter disabled", e);
            return -1;
        }
    }

    public static void end(int previous) {
        if (previous >= 0) GL20.glUseProgram(previous);
    }

    private static boolean load() {
        if (attempted) return program != 0;
        attempted = true;
        try {
            program = ShaderLib.loadShader(Global.getSettings().loadText(VERT),
                    Global.getSettings().loadText(FRAG));
        } catch (IOException | RuntimeException e) {
            Global.getLogger(AquariumFishShader.class).warn("Could not load aquarium fish filter", e);
            return false;
        }
        if (program == 0) return false;

        uTexture = GL20.glGetUniformLocation(program, "tex");
        uRegion = GL20.glGetUniformLocation(program, "region");
        uTexel = GL20.glGetUniformLocation(program, "texel");
        uSize = GL20.glGetUniformLocation(program, "imageSize");
        uStrength = GL20.glGetUniformLocation(program, "strength");
        uTime = GL20.glGetUniformLocation(program, "time");
        uPhase = GL20.glGetUniformLocation(program, "phase");
        return true;
    }
}
