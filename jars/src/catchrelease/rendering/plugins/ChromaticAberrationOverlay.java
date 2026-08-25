package catchrelease.rendering.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Whole-screen chromatic aberration, UI included. Runs in vanilla's above-UI-and-tooltips
 * render hook: the finished frame - world, panels, tooltips - is copied into a texture and
 * redrawn with the red and blue channels shifted apart. Fixed-function GL only, so it works
 * with GraphicsLib's shaders switched off. Purely level-driven: at zero it does nothing and
 * holds no registration.
 */
public class ChromaticAberrationOverlay implements CampaignUIRenderingListener {

    protected static final float MAX_SHIFT_PX = 6f;
    protected static final float MIN_SHIFT_PX = 1.5f;

    protected static ChromaticAberrationOverlay instance;

    protected float level = 0f;

    protected int texture = 0;
    protected int texWidth = 0;
    protected int texHeight = 0;

    public static void setLevel(float level) {
        if (level <= 0f) {
            if (instance != null) {
                instance.level = 0f;
                Global.getSector().getListenerManager().removeListener(instance);
                instance = null;
            }
            return;
        }

        if (instance == null) instance = new ChromaticAberrationOverlay();

        // re-checked every call: the listener registration is per-save, the instance is not
        if (!Global.getSector().getListenerManager().hasListener(instance)) {
            Global.getSector().getListenerManager().addListener(instance, true);
        }

        instance.level = MathUtils.clamp(level, 0f, 1f);
    }

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        if (level <= 0f) return;

        int width = (int) Global.getSettings().getScreenWidthPixels();
        int height = (int) Global.getSettings().getScreenHeightPixels();
        if (width <= 0 || height <= 0) return;

        ensureTexture(width, height);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);

        // draw in UI units: the framebuffer is real pixels, the ortho is scaled
        float scale = Global.getSettings().getScreenScaleMult();
        float w = width / scale;
        float h = height / scale;

        float wobble = 0.8f + 0.2f * (float) Math.sin(
                (System.currentTimeMillis() % 100000L) * 0.007);
        float shift = (MIN_SHIFT_PX + (MAX_SHIFT_PX - MIN_SHIFT_PX) * level)
                * wobble / scale;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // red left, blue right; green keeps the original frame between them
        GL11.glColorMask(true, false, false, false);
        drawScreenQuad(-shift, 0f, w, h);
        GL11.glColorMask(false, false, true, false);
        drawScreenQuad(shift, 0f, w, h);
        GL11.glColorMask(true, true, true, true);

        GL11.glPopAttrib();
    }

    protected void drawScreenQuad(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    protected void ensureTexture(int width, int height) {
        if (texture != 0 && texWidth == width && texHeight == height) return;

        if (texture != 0) GL11.glDeleteTextures(texture);

        texture = GL11.glGenTextures();
        texWidth = width;
        texHeight = height;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, width, height, 0,
                GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
    }
}
