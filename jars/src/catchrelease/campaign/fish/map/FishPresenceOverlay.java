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
 * Draws the fish waters over the sector map, riding its pan and zoom.
 * <p>
 * Each blob fills by stencil parity: its rings are fanned into the stencil buffer (crossings flip a
 * bit), then one flat cover pass fills wherever the bit ends up set - exact coverage regardless of how
 * the rings fold, and cheap. With more than one blob up, the cover pass is diagonal stripes offset per
 * blob instead of a solid sheet, so overlapping waters interleave rather than stack.
 * <p>
 * Geometry is world-space and cached; only the transform ({@code world * factor + centre}, read off
 * the map widget each frame) changes per frame.
 */
public class FishPresenceOverlay extends BaseCustomUIPanelPlugin {

    public static final float FILL_ALPHA = 0.1f;
    public static final float OUTLINE_ALPHA = 0.6f;
    public static final float OUTLINE_WIDTH = 1.5f;

    public static final float STRIPE_SPACING = 16f;

    /** How a blob's fill is painted. The three picks each get their own, so overlaps read. */
    public static final int STYLE_SOLID = 0;
    public static final int STYLE_STRIPE_RIGHT = 1;
    public static final int STYLE_STRIPE_LEFT = 2;

    /**
     * One set of waters: cached world-space rings, colour, fill style, and which of fill/outline to
     * draw - a same-coloured group shares one merged outline while each member keeps its own fill.
     */
    public static class Blob {
        public final FishPresenceField.Mesh mesh;
        public final Color color;
        public final int style;
        public final boolean drawFill;
        public final boolean drawOutline;

        public Blob(FishPresenceField.Mesh mesh, Color color, int style,
                    boolean drawFill, boolean drawOutline) {
            this.mesh = mesh;
            this.color = color;
            this.style = style;
            this.drawFill = drawFill;
            this.drawOutline = drawOutline;
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

        // Geometry is hyperspace-only; the same widget also shows single systems, where these coordinates mean nothing.
        Object location = ReflectionUtils.invokeIfExists(mapWidget, "getLocation");
        if (!(location instanceof LocationAPI) || !((LocationAPI) location).isHyperspace()) return;

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

        // Fills first, then all outlines - a merged border must sit on top of every member's fill.
        for (Blob blob : blobs) {
            if (blob.drawFill) renderFill(blob, factor, centerX, centerY, alphaMult);
        }
        for (Blob blob : blobs) {
            if (blob.drawOutline) renderOutline(blob, factor, centerX, centerY, alphaMult);
        }

        GL11.glPopAttrib();
    }

    protected void renderFill(Blob blob, float factor,
                              float centerX, float centerY, float alphaMult) {
        if (blob.mesh == null || blob.mesh.isEmpty()) return;

        float r = blob.color.getRed() / 255f;
        float g = blob.color.getGreen() / 255f;
        float b = blob.color.getBlue() / 255f;

        // Blob's box on screen, for the cover pass.
        float boxMinX = blob.mesh.minX * factor + centerX;
        float boxMinY = blob.mesh.minY * factor + centerY;
        float boxMaxX = blob.mesh.maxX * factor + centerX;
        float boxMaxY = blob.mesh.maxY * factor + centerY;

        // Parity pass: fan every ring into the stencil, flipping a bit per crossing.
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

        // Cover pass: fill (sheet or stripes) wherever the parity landed inside.
        GL11.glColorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 1);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glColor4f(r, g, b, FILL_ALPHA * alphaMult);

        if (blob.style == STYLE_SOLID) {
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(boxMinX, boxMinY);
            GL11.glVertex2f(boxMaxX, boxMinY);
            GL11.glVertex2f(boxMaxX, boxMaxY);
            GL11.glVertex2f(boxMinX, boxMaxY);
            GL11.glEnd();
        } else {
            renderStripes(boxMinX, boxMinY, boxMaxX, boxMaxY, blob.style);
        }

        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    /** The border, stroked straight from the rings - the fill's own, or a group's merged ones. */
    protected void renderOutline(Blob blob, float factor,
                                 float centerX, float centerY, float alphaMult) {
        if (blob.mesh == null || blob.mesh.isEmpty()) return;

        GL11.glColor4f(blob.color.getRed() / 255f, blob.color.getGreen() / 255f,
                blob.color.getBlue() / 255f, OUTLINE_ALPHA * alphaMult);
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
     * Diagonal bands clipped by the blob's stencil - rising to the right for the second pick,
     * to the left for the third, so where waters overlap the weaves cross instead of piling.
     */
    protected void renderStripes(float minX, float minY, float maxX, float maxY, int style) {
        // Diagonal's unit vectors: along the stripe, and across it.
        float dirX = 0.70710678f;
        float dirY = style == STYLE_STRIPE_LEFT ? -0.70710678f : 0.70710678f;
        float normX = -dirY, normY = dirX;

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

        float start = (float) Math.floor(acrossMin / STRIPE_SPACING) * STRIPE_SPACING;

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
