package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.rendering.helper.Disc;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the fish waters over the sector map, riding the map's own pan and zoom.
 * <p>
 * Each blob is painted by stencil parity: rings are fanned into the stencil buffer (crossings
 * flip a bit), then filled in one flat pass wherever the bit is set - exactly once per pixel
 * regardless of how the rings fold. With more than one blob up, the fill is diagonal stripes
 * (offset per blob) instead of a solid sheet, so overlapping waters interleave rather than stack.
 * <p>
 * Geometry is world-space and cached; only the transform ({@code world * factor + centre}, read
 * off the map widget) is per-frame. The panel rides the map scroller's overlay layer.
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

    /** One set of waters: cached rings, colour, fill style, and which of fill/outline actually
     *  draw - a same-coloured group shares one merged outline while each member keeps its own fill. */
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

    /** The route badges: a ringed disc lifted off its system, sized from the icon row it
     *  carries so the outermost fish always keeps clear water to the ring. */
    public static final float ROUTE_BADGE_RADIUS = 14f;
    public static final float ROUTE_BADGE_LIFT = 14f;
    public static final float ROUTE_ICON = 16f;
    public static final float ROUTE_ICON_GAP = 2f;
    public static final float ROUTE_BADGE_PAD = 5f;

    protected List<Blob> blobs = new ArrayList<>();

    /** The map's inner render widget - the thing that knows the camera. */
    protected Object mapWidget;

    /** Scratch for transformed rings, grown on demand - per-frame allocation is a stutter tax. */
    protected transient float[] scratch = new float[512];

    /** Where the cursor was last seen, for the close label's hover glow. */
    protected float mouseX = -1f;
    protected float mouseY = -1f;

    protected PositionAPI panelPos;

    public void setMapWidget(Object mapWidget) {
        this.mapWidget = mapWidget;
    }

    public void setBlobs(List<Blob> blobs) {
        this.blobs = blobs == null ? new ArrayList<>() : blobs;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        panelPos = position;
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (event.isMouseMoveEvent() || event.isMouseEvent()) {
                mouseX = event.getX();
                mouseY = event.getY();
            }

            //the route's close label, top centre of the map - the one clickable thing out here
            if (event.isLMBDownEvent() && FishRoute.get() != null
                    && isInCloseLabel(event.getX(), event.getY())) {

                FishRoute.clear();
                event.consume();
            }
        }
    }

    protected boolean isInCloseLabel(float x, float y) {
        float[] bounds = getCloseLabelBounds();
        if (bounds == null) return false;

        return x >= bounds[0] && x <= bounds[0] + bounds[2]
                && y >= bounds[1] && y <= bounds[1] + bounds[3];
    }

    /** The close label's rectangle as {x, y, width, height}, or null with nowhere to put it. */
    protected float[] getCloseLabelBounds() {
        if (panelPos == null) return null;

        LazyFont small = ShopUi.getSmallFont();
        float width = 160f;
        float height = (small == null ? 14f : small.getBaseHeight()) + 10f;

        return new float[]{
                panelPos.getX() + (panelPos.getWidth() - width) * 0.5f,
                panelPos.getY() + panelPos.getHeight() - height - 8f,
                width, height};
    }

    @Override
    public void render(float alphaMult) {
        if (mapWidget == null || alphaMult <= 0f) return;

        Object location = ReflectionUtils.invokeIfExists(mapWidget, "getLocation");
        if (!(location instanceof LocationAPI)) return;

        //the whole camera, in two numbers: everything on the map is world * factor + centre
        Object factorValue = ReflectionUtils.invokeIfExists(mapWidget, "getFactor");
        if (!(factorValue instanceof Float)) return;

        float factor = (Float) factorValue;
        PositionAPI mapPos = ((UIComponentAPI) mapWidget).getPosition();
        float centerX = mapPos.getCenterX();
        float centerY = mapPos.getCenterY();

        //the waters and the route are hyperspace geometry; the system view's catch lives on
        //its own component pane now, mounted by the filter script
        if (!((LocationAPI) location).isHyperspace()) return;

        if (!blobs.isEmpty()) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                    | GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            //fills first, outlines over all of them - a merged border belongs above every member's fill
            for (Blob blob : blobs) {
                if (blob.drawFill) renderFill(blob, factor, centerX, centerY, alphaMult);
            }
            for (Blob blob : blobs) {
                if (blob.drawOutline) renderOutline(blob, factor, centerX, centerY, alphaMult);
            }

            GL11.glPopAttrib();
        }

        renderRoute(factor, centerX, centerY, alphaMult);
    }


    /**
     * Plotted route's stops: a ringed badge above each system carrying its fish, a stub down to
     * the system, and the close label. Arrows between stops ride the map's own arrow list (the
     * one intel arrows use) rather than being drawn here.
     */
    protected void renderRoute(float factor, float centerX, float centerY, float alphaMult) {
        FishRoute.Saved route = FishRoute.get();
        if (route == null || route.stops.isEmpty()) return;

        Color player = Misc.getBasePlayerColor();

        for (FishRoute.Stop stop : route.stops) {
            StarSystemAPI system = FishRoute.getSystem(stop);
            if (system == null || system.getLocation() == null) continue;

            float sx = system.getLocation().x * factor + centerX;
            float sy = system.getLocation().y * factor + centerY;

            int count = Math.max(1, stop.fishIds.size());

            //the ring hugs the row's far corner plus breathing room, never tighter than the base
            float rowWidth = count * ROUTE_ICON + (count - 1) * ROUTE_ICON_GAP;
            float radius = Math.max(ROUTE_BADGE_RADIUS, (float) Math.hypot(
                    rowWidth * 0.5f, ROUTE_ICON * 0.5f) + ROUTE_BADGE_PAD);
            float bx = sx;
            float by = sy + ROUTE_BADGE_LIFT + radius;

            //the stub that says which system the badge belongs to
            ShopUi.drawQuad(sx - 0.5f, sy + 2f, 1f, by - radius - sy - 2f,
                    player, 0.6f * alphaMult);

            Disc.draw(bx, by, radius, Color.BLACK, 0.85f * alphaMult, 0.85f * alphaMult, false);
            Disc.drawOutline(bx, by, radius, player, 0.9f * alphaMult, 1.5f);

            //the fish, in a row across the badge
            float iconX = bx - (stop.fishIds.size() - 1) * (ROUTE_ICON + ROUTE_ICON_GAP) * 0.5f;

            for (String id : stop.fishIds) {
                FishSpec spec = FishPresence.getSpec(id);
                if (spec == null) continue;

                String iconPath = FishLog.isCaught(id)
                        ? FishCodex.getIcon(spec) : FishConstants.ITEM_ICON_FALLBACK;

                SpriteAPI icon = SpriteLoader.loadSprite(iconPath);
                if (icon != null) {
                    icon.setSize(ROUTE_ICON, ROUTE_ICON);
                    icon.setColor(Color.WHITE);
                    icon.setNormalBlend();
                    icon.setAlphaMult(alphaMult);
                    icon.renderAtCenter(Math.round(iconX), Math.round(by));
                }

                iconX += ROUTE_ICON + 2f;
            }
        }

        renderCloseLabel(alphaMult);
    }

    /** "X - CLOSE ROUTE", top centre of the map, the only way a route ever goes away. */
    protected void renderCloseLabel(float alphaMult) {
        float[] bounds = getCloseLabelBounds();
        if (bounds == null) return;

        LazyFont small = ShopUi.getSmallFont();
        if (small == null) return;

        boolean hovered = isInCloseLabel(mouseX, mouseY);
        Color color = hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

        ShopUi.drawQuad(bounds[0], bounds[1], bounds[2], bounds[3], Color.BLACK, 0.85f * alphaMult);
        ShopUi.drawQuad(bounds[0], bounds[1], bounds[2], 1f, color, alphaMult);
        ShopUi.drawQuad(bounds[0], bounds[1] + bounds[3] - 1f, bounds[2], 1f, color, alphaMult);
        ShopUi.drawQuad(bounds[0], bounds[1], 1f, bounds[3], color, alphaMult);
        ShopUi.drawQuad(bounds[0] + bounds[2] - 1f, bounds[1], 1f, bounds[3], color, alphaMult);

        LazyFont.DrawableString text = small.createText("X - CLOSE ROUTE", color,
                small.getBaseHeight());
        text.draw(Math.round(bounds[0] + (bounds[2] - text.getWidth()) * 0.5f),
                Math.round(bounds[1] + (bounds[3] + text.getHeight()) * 0.5f));
    }

    protected void renderFill(Blob blob, float factor,
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

        //parity pass: fan rings into the stencil, flipping a bit per crossing - a set bit is
        //inside the shape, however the rings nest or fold
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

    /** Diagonal bands clipped by the blob's stencil - rising right for pick two, left for pick
     *  three, so overlapping waters cross instead of piling. */
    protected void renderStripes(float minX, float minY, float maxX, float maxY, int style) {
        //the diagonal's unit vectors: along the stripe, and across it
        float dirX = 0.70710678f;
        float dirY = style == STYLE_STRIPE_LEFT ? -0.70710678f : 0.70710678f;
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
