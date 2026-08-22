package catchrelease.rendering.helper;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

public class Stencil {

    public static void startDepthMask(SpriteAPI mask,
                                      float width,
                                      float height,
                                      Vector2f center,
                                      boolean renderside) {

        final boolean alphaTestWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        final boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);

        // clear so only the mask writes the "key" depth value
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        // first pass: write depth wherever the mask passes alpha test
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glColorMask(false, false, false, false);

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.5f);

        GL11.glDisable(GL11.GL_BLEND);

        // force all written fragments to the same key depth
        GL11.glDepthRange(0.5, 0.5);

        if (mask != null) {
            float oldW = mask.getWidth();
            float oldH = mask.getHeight();

            mask.setSize(width, height);
            mask.renderAtCenter(center.x, center.y);

            mask.setSize(oldW, oldH);
        }

        GL11.glDepthRange(0.0, 1.0);
        GL11.glColorMask(true, true, true, true);

        GL11.glDepthFunc(renderside ? GL11.GL_EQUAL : GL11.GL_NOTEQUAL);
        GL11.glDepthMask(false);

        if (!alphaTestWasEnabled) GL11.glDisable(GL11.GL_ALPHA_TEST);
        if (blendWasEnabled) GL11.glEnable(GL11.GL_BLEND);
        else GL11.glDisable(GL11.GL_BLEND);
    }

    public static void endDepthMask() {
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDepthRange(0.0, 1.0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }


    @Deprecated
    public static void startStencil(SpriteAPI mask, float width, float height, Vector2f center, boolean reverse) {
        GL11.glClearStencil(0);
        GL11.glStencilMask(0xFF);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

        GL11.glColorMask(false, false, false, false);
        GL11.glEnable(GL11.GL_STENCIL_TEST);

        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // write "1" to the stencil wherever fragments pass
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
        GL11.glStencilMask(0xFF);

        boolean alphaTestWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.5f);

        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_BLEND);

        if (mask != null) {
            float oldW = mask.getWidth();
            float oldH = mask.getHeight();

            mask.setSize(width, height);
            mask.renderAtCenter(center.x, center.y);

            mask.setSize(oldW, oldH);
        }

        GL11.glDisable(GL11.GL_ALPHA_TEST);
        if (alphaTestWasEnabled) GL11.glEnable(GL11.GL_ALPHA_TEST);
        if (blendWasEnabled) GL11.glEnable(GL11.GL_BLEND);

        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glColorMask(true, true, true, true);

        if (reverse) {
            GL11.glStencilFunc(GL11.GL_NOTEQUAL, 1, 0xFF);
        } else {
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        }

        if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Deprecated
    public static void endStencil() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

}
