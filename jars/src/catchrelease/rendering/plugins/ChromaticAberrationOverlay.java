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
                instance.dispose();
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

        // dialogs and core tabs pause the game; the haunt does not follow into them
        if (Global.getSector().getCampaignUI().isShowingDialog()
                || Global.getSector().getCampaignUI().isShowingMenu()) {
            return;
        }

        float scale = Global.getSettings().getScreenScaleMult();
        renderRegion(0f, 0f, Global.getSettings().getScreenWidthPixels() / scale,
                Global.getSettings().getScreenHeightPixels() / scale, level, 0f);
    }

    public void renderRegion(float x, float y, float regionWidth, float regionHeight, float strength) {
        renderRegion(x, y, regionWidth, regionHeight, strength, MIN_SHIFT_PX);
    }

    private void renderRegion(float x, float y, float regionWidth, float regionHeight, float strength,
                              float minShift) {
        if (strength <= 0f || regionWidth <= 0f || regionHeight <= 0f) return;
        int width = (int) Global.getSettings().getScreenWidthPixels();
        int height = (int) Global.getSettings().getScreenHeightPixels();
        if (width <= 0 || height <= 0) return;

        // draw in UI units: the framebuffer is real pixels, the ortho is scaled
        float scale = Global.getSettings().getScreenScaleMult();
        float w = width / scale;
        float h = height / scale;

        float wobble = 0.8f + 0.2f * (float) Math.sin(
                (System.currentTimeMillis() % 100000L) * 0.007);
        float shift = (minShift + (MAX_SHIFT_PX - minShift) * MathUtils.clamp(strength, 0f, 1f))
                * wobble / scale;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        ensureTexture(width, height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // red left, blue right; green keeps the original frame between them
        GL11.glColorMask(true, false, false, false);
        drawRegion(x, y, regionWidth, regionHeight, shift, w, h);
        GL11.glColorMask(false, false, true, false);
        drawRegion(x, y, regionWidth, regionHeight, -shift, w, h);
        GL11.glColorMask(true, true, true, true);

        GL11.glPopAttrib();
    }

    protected void drawRegion(float x, float y, float w, float h, float shift,
                              float screenWidth, float screenHeight) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f((x + shift) / screenWidth, y / screenHeight);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f((x + w + shift) / screenWidth, y / screenHeight);
        GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f((x + w + shift) / screenWidth, (y + h) / screenHeight);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f((x + shift) / screenWidth, (y + h) / screenHeight);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    public void dispose() {
        if (texture != 0) GL11.glDeleteTextures(texture);
        texture = 0;
        texWidth = 0;
        texHeight = 0;
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
