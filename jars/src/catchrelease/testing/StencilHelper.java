package catchrelease.testing;

import com.fs.graphics.Sprite;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

public class StencilHelper {

    public static void startDepthMask(SpriteAPI mask,
                                      float width,
                                      float height,
                                      Vector2f center,
                                      boolean renderside) {

        // Save minimal state we are about to change
        final boolean alphaTestWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        final boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);

        // Enable depth test and allow depth writes
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);

        // Clear depth buffer so only the mask writes the "key" depth value
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        // First pass: write depth at a fixed value wherever mask passes alpha test
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glColorMask(false, false, false, false);

        // Discard transparent pixels from the sprite
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.5f);

        // Blending can affect alpha; disable it while writing the mask
        GL11.glDisable(GL11.GL_BLEND);

        // Force all written fragments to land at depth 0.5
        GL11.glDepthRange(0.5, 0.5);

        // Draw the sprite as the depth mask
        if (mask != null) {
            float oldW = mask.getWidth();
            float oldH = mask.getHeight();

            mask.setSize(width, height);
            mask.renderAtCenter(center.x, center.y);

            mask.setSize(oldW, oldH);
        }

        // Restore depth range for normal rendering
        GL11.glDepthRange(0.0, 1.0);

        // Restore color writes
        GL11.glColorMask(true, true, true, true);

        // Second pass: restrict rendering by comparing against the written depth
        GL11.glDepthFunc(renderside ? GL11.GL_EQUAL : GL11.GL_NOTEQUAL);
        GL11.glDepthMask(false); // prevent further depth writes while masked

        // Restore alpha/blend enables to previous state (leave depth test on for masking)
        if (!alphaTestWasEnabled) GL11.glDisable(GL11.GL_ALPHA_TEST);
        if (blendWasEnabled) GL11.glEnable(GL11.GL_BLEND);
        else GL11.glDisable(GL11.GL_BLEND);

        // If alpha test was previously enabled, keep it enabled (already is)
        // If it was disabled, we disabled it above.
    }

    public static void endDepthMask() {
        // Restore default-ish depth behavior
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDepthRange(0.0, 1.0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    /**
     * This breaks the campaign radar
     */
    @Deprecated
    public static void startStencil(SpriteAPI mask, float width, float height, Vector2f center, boolean reverse) {
        //clear old
        GL11.glClearStencil(0);
        GL11.glStencilMask(0xFF);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

        //start stencil
        GL11.glColorMask(false, false, false, false);
        GL11.glEnable(GL11.GL_STENCIL_TEST);

        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        //configure stencil to write "1" where fragments pass
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
        GL11.glStencilMask(0xFF);

        //draw the mask sprite into the stencil, but discard "black" pixels via alpha test
        boolean alphaTestWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.5f); // keep only alpha > 0.5

        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_BLEND);

        // Bind the sprite texture
        if (mask != null) {
            float oldW = mask.getWidth();
            float oldH = mask.getHeight();

            mask.setSize(width, height);
            mask.renderAtCenter(center.x, center.y);

            mask.setSize(oldW, oldH);
        }

        //restore alpha test + blending state
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        if (alphaTestWasEnabled) GL11.glEnable(GL11.GL_ALPHA_TEST);
        if (blendWasEnabled) GL11.glEnable(GL11.GL_BLEND);

        //finish stencil
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glColorMask(true, true, true, true);

        if (reverse) {
            GL11.glStencilFunc(GL11.GL_NOTEQUAL, 1, 0xFF);
        } else {
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        }

        // Restore depth state
        if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Deprecated
    public static void endStencil() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

}
