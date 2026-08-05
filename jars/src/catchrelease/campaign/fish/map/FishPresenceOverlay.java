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
 * Draws the fish waters over the sector map, riding the map's own pan and zoom.
 * <p>
 * Each blob is painted by stencil parity: its smoothed rings are fanned into the stencil buffer,
 * where crossings flip a bit, and the fill is then one flat pass wherever the bit is set. The
 * fill therefore lands exactly once per pixel however the rings fold - no double-darkened
 * overlaps, no fill peeking past the outline, since both come from the same rings - and one
 * cover pass per blob is also the cheap way to draw it.
 * <p>
 * When more than one blob is up at once, the cover pass is diagonal stripes instead of a solid
 * sheet, each blob's bands offset by its place in the list - so where two waters overlap, the
 * colours interleave instead of stacking. One blob alone gets the solid sheet, since it has
 * nobody to argue with.
 * <p>
 * The geometry is world-space and cached; only the transform is per-frame. The map widget draws
 * everything at {@code world * factor + centre}, so this reads those two numbers off the widget
 * each frame and applies the same arithmetic. The panel rides the map scroller's overlay layer,
 * which the game scissors to the map rectangle unconditionally.
 */
public class FishPresenceOverlay extends BaseCustomUIPanelPlugin {

    public static final float FILL_ALPHA = 0.1f;
    public static final float OUTLINE_ALPHA = 0.6f;
    public static final float OUTLINE_WIDTH = 1.5f;

    public static final float STRIPE_SPACING = 16f;

    /** One set of waters: its cached world-space rings and its colour. */
    public static class Blob {
        public final FishPresenceField.Mesh mesh;
        public final Color color;

        public Blob(FishPresenceField.Mesh mesh, Color color) {
            this.mesh = mesh;
            this.color = color;
        }
    }

    protected List<Blob> blobs = new ArrayList<>();

    /** The map's inner render widget - the thing that knows the camera. */
    protected Object mapWidget;

    /** Scratch for transformed rings, grown on demand - per-frame allocation is a stutter tax. */
    protected transient float[] scratch = new float[512];

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

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        boolean striped = blobs.size() > 1;

        for (int i = 0; i < blobs.size(); i++) {
            renderBlob(blobs.get(i), i, striped, factor, centerX, centerY, alphaMult);
        }

        GL11.glPopAttrib();
    }

    protected void renderBlob(Blob blob, int index, boolean striped, float factor,
                              float centerX, float centerY, float alphaMult) {
        if (blob.mesh == null || blob.mesh.isEmpty()) return;

        float r = blob.color.getRed() / 255f;
        float g = blob.color.getGreen() / 255f;
        float b = blob.color.getBlue() / 255f;

        //the blob's box on screen, for the cover pass
        float boxMinX = blob.mesh.minX * factor + centerX;
        float boxMinY = blob.mesh.minY * factor + centerY;
        float boxMaxX = blob.mesh.maxX * factor + centerX;
        float boxMaxY = blob.mesh.maxY * factor + centerY;

        //parity pass: fan every ring into the stencil, flipping a bit per crossing. Where the
        //bit ends up set is inside the shape - however the rings nest or fold
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glColorMask(false, false, false, false);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 1);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INVERT);

        for (float[] loop : blob.mesh.loops) {
            int points = loop.length / 2;
            ensureScratch(loop.length);

            for (int i = 0; i < points; i++) {
                scratch[i * 2] = loop[i * 2] * factor + centerX;
                scratch[i * 2 + 1] = loop[i * 2 + 1] * factor + centerY;
            }

            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            for (int i = 0; i < points; i++) {
                GL11.glVertex2f(scratch[i * 2], scratch[i * 2 + 1]);
            }
            GL11.glEnd();
        }

        //cover pass: one flat sheet - or one set of bands - wherever the parity landed inside
        GL11.glColorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 1);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glColor4f(r, g, b, FILL_ALPHA * alphaMult);

        if (striped) {
            renderStripes(boxMinX, boxMinY, boxMaxX, boxMaxY, index);
        } else {
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(boxMinX, boxMinY);
            GL11.glVertex2f(boxMaxX, boxMinY);
            GL11.glVertex2f(boxMaxX, boxMaxY);
            GL11.glVertex2f(boxMinX, boxMaxY);
            GL11.glEnd();
        }

        GL11.glDisable(GL11.GL_STENCIL_TEST);

        //the outline, stroked straight from the same rings the fill came from
        GL11.glColor4f(r, g, b, OUTLINE_ALPHA * alphaMult);
        GL11.glBegin(GL11.GL_QUADS);

        for (float[] loop : blob.mesh.loops) {
            int points = loop.length / 2;

            for (int i = 0; i < points; i++) {
                int j = (i + 1) % points;

                strokeEdge(loop[i * 2] * factor + centerX, loop[i * 2 + 1] * factor + centerY,
                        loop[j * 2] * factor + centerX, loop[j * 2 + 1] * factor + centerY);
            }
        }

        GL11.glEnd();
    }

    /**
     * Diagonal bands clipped by the blob's stencil, offset by the blob's place in the list - so
     * two overlapping waters take turns at the pixels instead of piling on them.
     */
    protected void renderStripes(float minX, float minY, float maxX, float maxY, int index) {
        //the diagonal's unit vectors: along the stripe, and across it
        float dirX = 0.70710678f, dirY = 0.70710678f;
        float normX = -dirY, normY = dirX;

        //the box's reach along each axis, from its corners
        float alongMin = Float.MAX_VALUE, alongMax = -Float.MAX_VALUE;
        float acrossMin = Float.MAX_VALUE, acrossMax = -Float.MAX_VALUE;

        float[] xs = {minX, maxX, maxX, minX};
        float[] ys = {minY, minY, maxY, maxY};

        for (int i = 0; i < 4; i++) {
            float along = xs[i] * dirX + ys[i] * dirY;
            float across = xs[i] * normX + ys[i] * normY;

            alongMin = Math.min(alongMin, along);
            alongMax = Math.max(alongMax, along);
            acrossMin = Math.min(acrossMin, across);
            acrossMax = Math.max(acrossMax, across);
        }

        float width = STRIPE_SPACING * 0.5f;
        float phase = (index % 2) * width;

        float start = (float) Math.floor(acrossMin / STRIPE_SPACING) * STRIPE_SPACING + phase;

        GL11.glBegin(GL11.GL_QUADS);

        for (float across = start; across < acrossMax; across += STRIPE_SPACING) {
            float nearX = normX * across, nearY = normY * across;
            float farX = normX * (across + width), farY = normY * (across + width);

            GL11.glVertex2f(nearX + dirX * alongMin, nearY + dirY * alongMin);
            GL11.glVertex2f(nearX + dirX * alongMax, nearY + dirY * alongMax);
            GL11.glVertex2f(farX + dirX * alongMax, farY + dirY * alongMax);
            GL11.glVertex2f(farX + dirX * alongMin, farY + dirY * alongMin);
        }

        GL11.glEnd();
    }

    /** One edge of the outline as a quad - thickness as a decision, not a driver setting. */
    protected void strokeEdge(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.0001f) return;

        float px = -dy / length * OUTLINE_WIDTH * 0.5f;
        float py = dx / length * OUTLINE_WIDTH * 0.5f;

        GL11.glVertex2f(x1 + px, y1 + py);
        GL11.glVertex2f(x2 + px, y2 + py);
        GL11.glVertex2f(x2 - px, y2 - py);
        GL11.glVertex2f(x1 - px, y1 - py);
    }

    protected void ensureScratch(int size) {
        if (scratch == null || scratch.length < size) scratch = new float[Math.max(size, 512)];
    }
}
