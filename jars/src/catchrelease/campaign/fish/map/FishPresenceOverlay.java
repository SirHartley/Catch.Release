package catchrelease.campaign.fish.map;

import catchrelease.ui.FishIcons;
import catchrelease.rendering.helper.Disc;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.ui.ShopUi;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class FishPresenceOverlay extends BaseCustomUIPanelPlugin {

    public static final float FILL_ALPHA = 0.1f;
    public static final float OUTLINE_ALPHA = 0.6f;
    public static final float OUTLINE_WIDTH = 1.5f;
    public static final float STRIPE_SPACING_WORLD = 600f;

    public static final int STYLE_SOLID = 0;
    public static final int STYLE_STRIPE_RIGHT = 1;
    public static final int STYLE_STRIPE_LEFT = 2;

    public static final float ROUTE_BADGE_RADIUS = 14f;
    public static final float ROUTE_BADGE_LIFT = 14f;
    public static final float ROUTE_ICON = 16f;
    public static final float ROUTE_ICON_GAP = 2f;
    public static final float ROUTE_BADGE_PAD = 5f;

    public static final String NO_DATA_TEXT = "NO DATA";
    public static final float NO_DATA_WIDTH = 180f;
    public static final float NO_DATA_HEIGHT = 54f;
    public static final float NO_DATA_BORDER = 2f;

    protected List<Blob> blobs = new ArrayList<>();

    protected boolean noDataShown;
    protected Object mapWidget;
    protected transient float[] scratch = new float[512];

    protected float mouseX = -1f;
    protected float mouseY = -1f;
    protected PositionAPI panelPos;

    protected Runnable saveRouteRequested;
    protected Object savedStateFor;
    protected boolean savedState;

    protected transient CoherenceHeatField heat;
    protected boolean coherenceShown = false;

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

    public void setMapWidget(Object mapWidget) {
        this.mapWidget = mapWidget;
    }

    public void setBlobs(List<Blob> blobs) {
        this.blobs = blobs == null ? new ArrayList<>() : blobs;
    }

    public void setNoDataShown(boolean shown) {
        noDataShown = shown;
    }

    public void setSaveRouteListener(Runnable listener) {
        saveRouteRequested = listener;
    }

    public void noteRouteSaved() {
        savedStateFor = FishRoute.get();
        savedState = true;
    }

    protected boolean isRouteSaved(FishRoute.Saved route) {
        // cached per route object - the plotted route only changes by being replaced
        if (route != savedStateFor) {
            savedStateFor = route;
            savedState = catchrelease.campaign.fish.intel.FishRouteIntel.isSaved(route);
        }

        return savedState;
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

            if (event.isLMBDownEvent() && FishRoute.get() != null
                    && isInCloseLabel(event.getX(), event.getY())) {
                // a tracked copy of the same route draws the same badges, so the
                // clear takes it too - other saved routes are their own decisions
                catchrelease.campaign.fish.intel.FishRouteIntel.forget(FishRoute.get());
                FishRoute.clear();
                event.consume();
            }

            if (event.isLMBDownEvent() && FishRoute.get() != null
                    && isInSaveLabel(event.getX(), event.getY())) {
                if (saveRouteRequested != null && !isRouteSaved(FishRoute.get())) {
                    saveRouteRequested.run();
                }
                event.consume();
            }
        }
    }

    protected boolean isInCloseLabel(float x, float y) {
        return isInBounds(getCloseLabelBounds(), x, y);
    }

    protected boolean isInSaveLabel(float x, float y) {
        return isInBounds(getSaveLabelBounds(), x, y);
    }

    protected boolean isInBounds(float[] bounds, float x, float y) {
        if (bounds == null) return false;

        return x >= bounds[0] && x <= bounds[0] + bounds[2]
                && y >= bounds[1] && y <= bounds[1] + bounds[3];
    }

    public void setCoherenceShown(boolean shown) {
        coherenceShown = shown;

        if (shown && heat == null) heat = new CoherenceHeatField();
    }

    protected float[] getCloseLabelBounds() {
        return getLabelBounds(saveRouteRequested == null ? 0 : 1,
                saveRouteRequested == null ? 1 : 2);
    }

    protected float[] getSaveLabelBounds() {
        return saveRouteRequested == null ? null : getLabelBounds(0, 2);
    }

    protected float[] getLabelBounds(int slot, int slots) {
        if (panelPos == null) return null;

        LazyFont small = ShopUi.getSmallFont();
        float width = 160f;
        float gap = 8f;
        float height = (small == null ? 14f : small.getBaseHeight()) + 10f;
        float total = width * slots + gap * (slots - 1);

        return new float[]{
                panelPos.getX() + (panelPos.getWidth() - total) * 0.5f + slot * (width + gap),
                panelPos.getY() + panelPos.getHeight() - height - 8f,
                width, height};
    }

    @Override
    public void render(float alphaMult) {
        if (mapWidget == null || alphaMult <= 0f) return;

        Object location = ReflectionUtils.invokeIfExists(mapWidget, "getLocation");
        if (!(location instanceof LocationAPI)) return;

        // the whole camera, in two numbers: everything on the map is world * factor + centre
        Object factorValue = ReflectionUtils.invokeIfExists(mapWidget, "getFactor");
        if (!(factorValue instanceof Float)) return;

        float factor = (Float) factorValue;
        PositionAPI mapPos = ((UIComponentAPI) mapWidget).getPosition();
        float centerX = mapPos.getCenterX();
        float centerY = mapPos.getCenterY();

        if (!((LocationAPI) location).isHyperspace()) return;

        // the heat map goes down first - it is the water the waters sit on
        if (coherenceShown && heat != null) {
            heat.sampleSome();

            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            heat.render(factor, centerX, centerY, alphaMult);

            GL11.glPopAttrib();
        }

        if (!blobs.isEmpty()) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                    | GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // fills first, outlines over all of them - a merged border belongs above every member's fill
            for (Blob blob : blobs) {
                if (blob.drawFill) renderFill(blob, factor, centerX, centerY, alphaMult);
            }
            for (Blob blob : blobs) {
                if (blob.drawOutline) renderOutline(blob, factor, centerX, centerY, alphaMult);
            }

            GL11.glPopAttrib();
        }

        renderRoute(factor, centerX, centerY, alphaMult);
        renderNoData(alphaMult);
    }

    protected void renderNoData(float alphaMult) {
        if (!noDataShown || panelPos == null) return;

        LazyFont bold = ShopUi.getTitleFont();
        if (bold == null) return;

        float x = panelPos.getX() + (panelPos.getWidth() - NO_DATA_WIDTH) * 0.5f;
        float y = panelPos.getY() + (panelPos.getHeight() - NO_DATA_HEIGHT) * 0.5f;
        Color red = Misc.getNegativeHighlightColor();

        ShopUi.drawQuad(x, y, NO_DATA_WIDTH, NO_DATA_HEIGHT, Color.BLACK,
                0.88f * alphaMult);
        ShopUi.drawQuad(x, y, NO_DATA_WIDTH, NO_DATA_BORDER, red, alphaMult);
        ShopUi.drawQuad(x, y + NO_DATA_HEIGHT - NO_DATA_BORDER,
                NO_DATA_WIDTH, NO_DATA_BORDER, red, alphaMult);
        ShopUi.drawQuad(x, y, NO_DATA_BORDER, NO_DATA_HEIGHT, red, alphaMult);
        ShopUi.drawQuad(x + NO_DATA_WIDTH - NO_DATA_BORDER, y,
                NO_DATA_BORDER, NO_DATA_HEIGHT, red, alphaMult);

        LazyFont.DrawableString text = bold.createText(NO_DATA_TEXT,
                ShopUi.withAlpha(red, alphaMult), bold.getBaseHeight());
        text.draw(Math.round(x + (NO_DATA_WIDTH - text.getWidth()) * 0.5f),
                Math.round(y + (NO_DATA_HEIGHT + text.getHeight()) * 0.5f));
    }

    protected void renderRoute(float factor, float centerX, float centerY, float alphaMult) {
        FishRoute.Saved route = FishRoute.get();
        boolean liveShown = route != null && !route.stops.isEmpty();

        // saved routes stay on the map like the plotted one; stops merge by system so an
        // overlap between routes draws one ring carrying the union of their fish
        java.util.Map<String, java.util.LinkedHashSet<String>> bySystem =
                new java.util.LinkedHashMap<>();

        if (liveShown) {
            for (FishRoute.Stop stop : route.stops) {
                bySystem.computeIfAbsent(stop.systemId,
                        k -> new java.util.LinkedHashSet<>()).addAll(stop.fishIds);
            }
        }
        for (catchrelease.campaign.fish.intel.FishRouteIntel intel
                : catchrelease.campaign.fish.intel.FishRouteIntel.getAll()) {
            for (FishRoute.Stop stop : intel.getStops()) {
                bySystem.computeIfAbsent(stop.systemId,
                        k -> new java.util.LinkedHashSet<>()).addAll(stop.fishIds);
            }
        }

        if (bySystem.isEmpty()) return;

        Color player = Misc.getBasePlayerColor();

        for (java.util.Map.Entry<String, java.util.LinkedHashSet<String>> entry
                : bySystem.entrySet()) {
            StarSystemAPI system = FishRoute.getSystemById(entry.getKey());
            if (system == null || system.getLocation() == null) continue;

            float sx = system.getLocation().x * factor + centerX;
            float sy = system.getLocation().y * factor + centerY;

            int count = Math.max(1, entry.getValue().size());

            // full-size icons packed as a cluster - pair, triangle, square, pentagon - so the ring hugs the cluster's reach plus breathing room, never tighter than the base
            float[][] offsets = clusterOffsets(count, ROUTE_ICON + ROUTE_ICON_GAP);

            float reach = 0f;
            for (float[] at : offsets) {
                reach = Math.max(reach, (float) Math.hypot(at[0], at[1]));
            }

            float radius = Math.max(ROUTE_BADGE_RADIUS, reach
                    + (float) Math.hypot(ROUTE_ICON * 0.5f, ROUTE_ICON * 0.5f) + ROUTE_BADGE_PAD);
            float bx = sx;
            float by = sy + ROUTE_BADGE_LIFT + radius;

            ShopUi.drawQuad(sx - 0.5f, sy + 2f, 1f, by - radius - sy - 2f,
                    player, 0.6f * alphaMult);

            Disc.draw(bx, by, radius, Color.BLACK, 0.85f * alphaMult, 0.85f * alphaMult, false);
            Disc.drawOutline(bx, by, radius, player, 0.9f * alphaMult, 1.5f);

            int slot = 0;
            for (String id : entry.getValue()) {
                float[] at = offsets[Math.min(slot++, offsets.length - 1)];

                FishSpec spec = FishPresence.getSpec(id);
                if (spec == null) continue;

                // the art once landed, its rimmed silhouette while only surveyed
                FishIcons.draw(spec, bx + at[0], by + at[1], ROUTE_ICON, alphaMult);
            }
        }

        // the labels close and save the live route; a map showing only saved ones has neither
        if (liveShown) renderLabels(alphaMult);
    }

    protected static float[][] clusterOffsets(int count, float spacing) {
        float[][] out = new float[count][2];
        if (count == 1) return out;

        if (count == 2) {
            out[0][0] = -spacing * 0.5f;
            out[1][0] = spacing * 0.5f;
            return out;
        }

        if (count == 4) {
            float half = spacing * 0.5f;
            out[0] = new float[]{-half, half};
            out[1] = new float[]{half, half};
            out[2] = new float[]{-half, -half};
            out[3] = new float[]{half, -half};
            return out;
        }

        float radius = (float) (spacing / (2.0 * Math.sin(Math.PI / count)));
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 0.5 + i * 2.0 * Math.PI / count;
            out[i][0] = (float) (Math.cos(angle) * radius);
            out[i][1] = (float) (Math.sin(angle) * radius);
        }

        return out;
    }

    protected void renderLabels(float alphaMult) {
        boolean hovered = isInCloseLabel(mouseX, mouseY);
        drawLabel(getCloseLabelBounds(), "X - CLEAR ROUTE",
                hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor(), alphaMult);

        if (saveRouteRequested == null) return;

        if (isRouteSaved(FishRoute.get())) {
            drawLabel(getSaveLabelBounds(), "ROUTE SAVED", Misc.getGrayColor(), alphaMult);
        } else {
            hovered = isInSaveLabel(mouseX, mouseY);
            drawLabel(getSaveLabelBounds(), "SAVE AS INTEL",
                    hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor(), alphaMult);
        }
    }

    protected void drawLabel(float[] bounds, String label, Color color, float alphaMult) {
        if (bounds == null) return;

        LazyFont small = ShopUi.getSmallFont();
        if (small == null) return;

        ShopUi.drawQuad(bounds[0], bounds[1], bounds[2], bounds[3], Color.BLACK, 0.85f * alphaMult);
        ShopUi.drawQuad(bounds[0], bounds[1], bounds[2], 1f, color, alphaMult);
        ShopUi.drawQuad(bounds[0], bounds[1] + bounds[3] - 1f, bounds[2], 1f, color, alphaMult);
        ShopUi.drawQuad(bounds[0], bounds[1], 1f, bounds[3], color, alphaMult);
        ShopUi.drawQuad(bounds[0] + bounds[2] - 1f, bounds[1], 1f, bounds[3], color, alphaMult);

        LazyFont.DrawableString text = small.createText(label, color, small.getBaseHeight());
        text.draw(Math.round(bounds[0] + (bounds[2] - text.getWidth()) * 0.5f),
                Math.round(bounds[1] + (bounds[3] + text.getHeight()) * 0.5f));
    }

    protected void renderFill(Blob blob, float factor,
                              float centerX, float centerY, float alphaMult) {
        if (blob.mesh == null || blob.mesh.isEmpty()) return;

        float r = blob.color.getRed() / 255f;
        float g = blob.color.getGreen() / 255f;
        float b = blob.color.getBlue() / 255f;

        // the blob's box on screen, for the cover pass
        float boxMinX = blob.mesh.minX * factor + centerX;
        float boxMinY = blob.mesh.minY * factor + centerY;
        float boxMaxX = blob.mesh.maxX * factor + centerX;
        float boxMaxY = blob.mesh.maxY * factor + centerY;

        // parity pass: fan rings into the stencil, flipping a bit per crossing - a set bit is inside the shape, however the rings nest or fold
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
            renderStripes(blob.mesh.minX, blob.mesh.minY, blob.mesh.maxX, blob.mesh.maxY,
                    blob.style, factor, centerX, centerY);
        }

        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

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

    protected void renderStripes(float minX, float minY, float maxX, float maxY, int style,
                                 float factor, float centerX, float centerY) {
        float dirX = 0.70710678f;
        float dirY = style == STYLE_STRIPE_LEFT ? -0.70710678f : 0.70710678f;
        float normX = -dirY, normY = dirX;

        // the box's reach along each axis, from its corners - all of it in world units
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

        float width = STRIPE_SPACING_WORLD * 0.5f;

        float start = (float) Math.floor(acrossMin / STRIPE_SPACING_WORLD) * STRIPE_SPACING_WORLD;

        GL11.glBegin(GL11.GL_QUADS);

        for (float across = start; across < acrossMax; across += STRIPE_SPACING_WORLD) {
            float nearX = normX * across, nearY = normY * across;
            float farX = normX * (across + width), farY = normY * (across + width);

            GL11.glVertex2f((nearX + dirX * alongMin) * factor + centerX,
                    (nearY + dirY * alongMin) * factor + centerY);
            GL11.glVertex2f((nearX + dirX * alongMax) * factor + centerX,
                    (nearY + dirY * alongMax) * factor + centerY);
            GL11.glVertex2f((farX + dirX * alongMax) * factor + centerX,
                    (farY + dirY * alongMax) * factor + centerY);
            GL11.glVertex2f((farX + dirX * alongMin) * factor + centerX,
                    (farY + dirY * alongMin) * factor + centerY);
        }

        GL11.glEnd();
    }

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
