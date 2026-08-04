package catchrelease.campaign.fish.map;

import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the fish waters over the sector map: each shown species' merged blob, filled faint and
 * outlined a little less faint, in the rarity's colour, riding the map's own pan and zoom.
 * <p>
 * The geometry is world-space and cached - see {@link FishPresenceField} - and only the transform
 * is per-frame: the map widget draws everything at {@code world * factor + centre}, so this reads
 * the factor and the centre off the widget each frame and applies the same arithmetic on the CPU.
 * The panel this plugin rides sits in the map scroller's overlay layer, which the scroller
 * scissors to the map rectangle unconditionally - so a blob reaching past the map's edge is cut
 * by the game itself, the way every other map mark is.
 * <p>
 * The outline is quads rather than GL lines: line width above one pixel is driver roulette, and
 * the outline is most of what sells the shape as one thing.
 */
public class FishPresenceOverlay extends BaseCustomUIPanelPlugin {

    public static final float FILL_ALPHA = 0.1f;
    public static final float FILL_ALPHA_LIT = 0.18f;
    public static final float OUTLINE_ALPHA = 0.45f;
    public static final float OUTLINE_ALPHA_LIT = 0.85f;
    public static final float OUTLINE_WIDTH = 1.5f;

    /** One species' shape: its cached world-space mesh, its colour, and whether it is the chosen one. */
    public static class Blob {
        public final FishPresenceField.Mesh mesh;
        public final Color color;
        public boolean lit;

        public Blob(FishPresenceField.Mesh mesh, Color color, boolean lit) {
            this.mesh = mesh;
            this.color = color;
            this.lit = lit;
        }
    }

    protected List<Blob> blobs = new ArrayList<>();

    /** The map's inner render widget - the thing that knows the camera. */
    protected Object mapWidget;

    public void setMapWidget(Object mapWidget) {
        this.mapWidget = mapWidget;
    }

    public void setBlobs(List<Blob> blobs) {
        this.blobs = blobs == null ? new ArrayList<>() : blobs;
    }

    @Override
    public void render(float alphaMult) {
        if (blobs.isEmpty() || mapWidget == null || alphaMult <= 0f) return;

        //the geometry is hyperspace geometry; the same screen also shows single systems, and
        //waters drawn over a star system would be marking coordinates that mean nothing there
        Object location = ReflectionUtils.invokeIfExists(mapWidget, "getLocation");
        if (!(location instanceof LocationAPI) || !((LocationAPI) location).isHyperspace()) return;

        //the whole camera, in two numbers: everything on the map is world * factor + centre
        Object factorValue = ReflectionUtils.invokeIfExists(mapWidget, "getFactor");
        if (!(factorValue instanceof Float)) return;

        float factor = (Float) factorValue;
        PositionAPI mapPos = ((UIComponentAPI) mapWidget).getPosition();
        float centerX = mapPos.getCenterX();
        float centerY = mapPos.getCenterY();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        //unlit shapes first, so the chosen one's outline is never buried under a neighbour's fill
        for (Blob blob : blobs) {
            if (!blob.lit) renderBlob(blob, factor, centerX, centerY, alphaMult);
        }
        for (Blob blob : blobs) {
            if (blob.lit) renderBlob(blob, factor, centerX, centerY, alphaMult);
        }

        GL11.glPopAttrib();
    }

    protected void renderBlob(Blob blob, float factor, float centerX, float centerY, float alphaMult) {
        if (blob.mesh == null || blob.mesh.isEmpty()) return;

        float r = blob.color.getRed() / 255f;
        float g = blob.color.getGreen() / 255f;
        float b = blob.color.getBlue() / 255f;

        GL11.glColor4f(r, g, b, (blob.lit ? FILL_ALPHA_LIT : FILL_ALPHA) * alphaMult);
        GL11.glBegin(GL11.GL_TRIANGLES);

        for (float[] tri : blob.mesh.fill) {
            GL11.glVertex2f(tri[0] * factor + centerX, tri[1] * factor + centerY);
            GL11.glVertex2f(tri[2] * factor + centerX, tri[3] * factor + centerY);
            GL11.glVertex2f(tri[4] * factor + centerX, tri[5] * factor + centerY);
        }

        GL11.glEnd();

        GL11.glColor4f(r, g, b, (blob.lit ? OUTLINE_ALPHA_LIT : OUTLINE_ALPHA) * alphaMult);
        GL11.glBegin(GL11.GL_QUADS);

        for (float[] seg : blob.mesh.outline) {
            float x1 = seg[0] * factor + centerX;
            float y1 = seg[1] * factor + centerY;
            float x2 = seg[2] * factor + centerX;
            float y2 = seg[3] * factor + centerY;

            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 0.0001f) continue;

            //half a width to each side, perpendicular - a line drawn as geometry, so its
            //thickness is a decision rather than a driver setting
            float px = -dy / length * OUTLINE_WIDTH * 0.5f;
            float py = dx / length * OUTLINE_WIDTH * 0.5f;

            GL11.glVertex2f(x1 + px, y1 + py);
            GL11.glVertex2f(x2 + px, y2 + py);
            GL11.glVertex2f(x2 - px, y2 - py);
            GL11.glVertex2f(x1 - px, y1 - py);
        }

        GL11.glEnd();
    }
}
