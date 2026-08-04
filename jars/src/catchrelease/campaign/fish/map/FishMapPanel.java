package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sector, drawn by this mod's own hand: a pan-and-zoom viewport over hyperspace with the
 * systems as marks, the catches as pins, and everything the log knows lit where it belongs.
 * <p>
 * Drawn rather than borrowed, because the game's map widget would not render inside a custom
 * visual dialog however it was asked. The pan-and-zoom mechanics - wheel zoom anchored on the
 * mouse, carried by a decaying velocity, drag to pan - follow the approach in Kaysaar's AshLib
 * map components, reimplemented here rather than copied: AshLib ships under GPLv3 and this mod
 * does not, so what was taken is the idea, which is the part a license does not own.
 * <p>
 * The panel is built once and handed fresh data when the filters move, so the camera survives a
 * filter change - a map that jumps home every time a checkbox is clicked is a map fighting its
 * own controls.
 */
public class FishMapPanel extends BaseCustomUIPanelPlugin {

    /** A star system on the map: where, what it is called, and what the log says lives there. */
    public static class SystemMark {
        public final Vector2f loc;
        public final String name;

        /** Lit systems carry the colour of the rarest species said to live there. */
        public boolean lit = false;
        public Color litColor = null;
        public final List<String> species = new ArrayList<>();

        public SystemMark(Vector2f loc, String name) {
            this.loc = new Vector2f(loc);
            this.name = name;
        }
    }

    /** A catch on the map: the exact spot a record specimen came out of the water. */
    public static class CatchMark {
        public final Vector2f loc;
        public final Color color;
        public final String label;

        public CatchMark(Vector2f loc, Color color, String label) {
            this.loc = new Vector2f(loc);
            this.color = color;
            this.label = label;
        }
    }

    /**
     * A shaded patch of the sector, in world units: the approximate water a species is said to
     * haunt. A region that is not one rectangle - the rim quadrants are L-shaped - arrives as
     * several of these in the same colour.
     */
    public static class AreaMark {
        public final float x, y, width, height;
        public final Color color;

        public AreaMark(float x, float y, float width, float height, Color color) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
        }
    }

    public static final float GRID_SPACING = 10000f;
    public static final float ZOOM_KICK = 1.5f;
    public static final float ZOOM_DECAY = 0.85f;
    public static final float MAX_PPU = 0.12f;
    public static final float NAME_PPU = 0.016f;
    public static final float HOVER_RANGE = 14f;
    public static final float INFO_STRIP = 24f;

    protected PositionAPI pos;

    /** The camera: the world point under the middle of the viewport, and pixels per world unit. */
    protected float camX = 0f;
    protected float camY = 0f;
    protected float ppu = 0f;
    protected float minPpu = 0.001f;

    protected float zoomVelocity = 0f;
    protected final Vector2f zoomAnchor = new Vector2f();

    protected boolean dragging = false;
    protected final Vector2f lastMouse = new Vector2f();

    protected List<SystemMark> systems = new ArrayList<>();
    protected List<CatchMark> catches = new ArrayList<>();
    protected List<AreaMark> areas = new ArrayList<>();

    protected float worldMinX, worldMinY, worldMaxX, worldMaxY;
    protected boolean fitted = false;

    protected Object hovered = null;

    protected transient Map<String, LazyFont.DrawableString> names;
    protected transient LazyFont.DrawableString infoText;
    protected transient String infoShown;

    /** Fresh marks for the current filter. The camera stays where the player put it. */
    public void setData(List<SystemMark> systems, List<CatchMark> catches) {
        this.systems = systems == null ? new ArrayList<>() : systems;
        this.catches = catches == null ? new ArrayList<>() : catches;

        measureWorld();
    }

    /** The shaded waters - usually the selected species' regions. Empty clears the shading. */
    public void setAreas(List<AreaMark> areas) {
        this.areas = areas == null ? new ArrayList<>() : areas;
    }

    /** Puts a world point in the middle of the glass, zoomed close enough to mean it. */
    public void focus(Vector2f world) {
        if (world == null) return;

        camX = world.x;
        camY = world.y;
        ppu = Math.max(ppu, 0.035f);
        zoomVelocity = 0f;
    }

    protected void measureWorld() {
        worldMinX = worldMinY = Float.MAX_VALUE;
        worldMaxX = worldMaxY = -Float.MAX_VALUE;

        for (SystemMark mark : systems) {
            worldMinX = Math.min(worldMinX, mark.loc.x);
            worldMinY = Math.min(worldMinY, mark.loc.y);
            worldMaxX = Math.max(worldMaxX, mark.loc.x);
            worldMaxY = Math.max(worldMaxY, mark.loc.y);
        }

        if (worldMinX > worldMaxX) {
            worldMinX = -90000f;
            worldMaxX = 90000f;
            worldMinY = -55000f;
            worldMaxY = 55000f;
        }

        //room past the outermost system, so the rim is not glued to the frame
        worldMinX -= 6000f;
        worldMinY -= 6000f;
        worldMaxX += 6000f;
        worldMaxY += 6000f;
    }

    /** The first sight is the whole sector, fitted. Asked once, since after that the camera is the player's. */
    protected void fitOnce() {
        if (fitted || pos == null) return;
        fitted = true;

        float spanX = Math.max(1f, worldMaxX - worldMinX);
        float spanY = Math.max(1f, worldMaxY - worldMinY);

        minPpu = Math.min(pos.getWidth() / spanX, pos.getHeight() / spanY);
        ppu = minPpu;
        camX = (worldMinX + worldMaxX) * 0.5f;
        camY = (worldMinY + worldMaxY) * 0.5f;
    }

    /**
     * Keeps the visible rect inside the world, not just the camera point - clamping the centre
     * to the world bounds lets the middle of the glass reach the world's corner, which is half a
     * screen of nothing past the content. Each axis stands alone: where the view is wider than
     * the world's span the content cannot fill the glass anyway, so the camera pins to the
     * middle - that is what stops the drag when everything already fits.
     */
    protected void clampCamera() {
        if (ppu <= 0f) return;

        float halfX = pos.getWidth() * 0.5f / ppu;
        float halfY = pos.getHeight() * 0.5f / ppu;

        if (halfX * 2f >= worldMaxX - worldMinX) {
            camX = (worldMinX + worldMaxX) * 0.5f;
        } else {
            camX = MathUtils.clamp(camX, worldMinX + halfX, worldMaxX - halfX);
        }

        if (halfY * 2f >= worldMaxY - worldMinY) {
            camY = (worldMinY + worldMaxY) * 0.5f;
        } else {
            camY = MathUtils.clamp(camY, worldMinY + halfY, worldMaxY - halfY);
        }
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    protected float toScreenX(float worldX) {
        return pos.getX() + pos.getWidth() * 0.5f + (worldX - camX) * ppu;
    }

    protected float toScreenY(float worldY) {
        return pos.getY() + pos.getHeight() * 0.5f + (worldY - camY) * ppu;
    }

    protected float toWorldX(float screenX) {
        return camX + (screenX - pos.getX() - pos.getWidth() * 0.5f) / ppu;
    }

    protected float toWorldY(float screenY) {
        return camY + (screenY - pos.getY() - pos.getHeight() * 0.5f) / ppu;
    }

    protected boolean isMouseOver() {
        return pos != null && ShopUi.contains(pos.getX(), pos.getY(), pos.getWidth(),
                pos.getHeight(), Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (pos == null) return;

        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            //the wheel kicks a velocity rather than stepping the zoom, and remembers where the
            //mouse was - the glide toward the cursor is most of what makes a map feel like glass
            if (event.isMouseScrollEvent() && isMouseOver()) {
                zoomVelocity += event.getEventValue() > 0 ? ZOOM_KICK : -ZOOM_KICK;
                zoomAnchor.set(Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

                event.consume();
                continue;
            }

            if (event.isLMBDownEvent() && isMouseOver()) {
                dragging = true;
                lastMouse.set(event.getX(), event.getY());

                event.consume();
            }
        }
    }

    @Override
    public void advance(float amount) {
        if (pos == null) return;

        fitOnce();

        //dragging follows the raw mouse rather than events, and lets go the moment the button does
        if (dragging) {
            if (!Mouse.isButtonDown(0)) {
                dragging = false;
            } else {
                float mouseX = Global.getSettings().getMouseX();
                float mouseY = Global.getSettings().getMouseY();

                camX -= (mouseX - lastMouse.x) / ppu;
                camY -= (mouseY - lastMouse.y) / ppu;
                lastMouse.set(mouseX, mouseY);
            }
        }

        if (Math.abs(zoomVelocity) > 0.001f) {
            //hold the world point under the anchor still while the scale moves through it
            float anchorWorldX = toWorldX(zoomAnchor.x);
            float anchorWorldY = toWorldY(zoomAnchor.y);

            ppu = MathUtils.clamp(ppu * (1f + zoomVelocity * amount), minPpu, MAX_PPU);

            camX += anchorWorldX - toWorldX(zoomAnchor.x);
            camY += anchorWorldY - toWorldY(zoomAnchor.y);

            zoomVelocity *= ZOOM_DECAY;
            if (Math.abs(zoomVelocity) < 0.001f) zoomVelocity = 0f;
        }

        clampCamera();

        updateHover();
    }

    /** Whatever mark is under the cursor, catches before systems - a pin is the finer statement. */
    protected void updateHover() {
        hovered = null;
        if (!isMouseOver() || dragging) return;

        float mouseX = Global.getSettings().getMouseX();
        float mouseY = Global.getSettings().getMouseY();
        float best = HOVER_RANGE;

        for (SystemMark mark : systems) {
            float distance = MathUtils.getDistance(new Vector2f(toScreenX(mark.loc.x),
                    toScreenY(mark.loc.y)), new Vector2f(mouseX, mouseY));

            if (distance < best) {
                best = distance;
                hovered = mark;
            }
        }

        for (CatchMark mark : catches) {
            float distance = MathUtils.getDistance(new Vector2f(toScreenX(mark.loc.x),
                    toScreenY(mark.loc.y)), new Vector2f(mouseX, mouseY));

            if (distance <= best) {
                best = distance;
                hovered = mark;
            }
        }
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        fitOnce();

        float x = pos.getX();
        float y = pos.getY();
        float width = pos.getWidth();
        float height = pos.getHeight();

        ShopUi.startClip(x, y, width, height);

        //the field the sector sits on: near-black, with the faint blue the game's own map has
        ShopUi.drawQuad(x, y, width, height, new Color(8, 12, 20), 0.92f * alphaMult);

        renderGrid(x, y, width, height, alphaMult);
        renderAreas(alphaMult);
        renderSystems(alphaMult);
        renderCatches(alphaMult);
        renderPlayer(alphaMult);
        renderInfoStrip(x, y, width, alphaMult);

        ShopUi.endClip();

        ShopUi.dress(x, y, width, height, alphaMult);
    }

    /** The survey grid, aligned to the world's own zero so it slides with the pan. */
    protected void renderGrid(float x, float y, float width, float height, float alphaMult) {
        Color color = Misc.getDarkPlayerColor();
        float alpha = 0.22f * alphaMult;

        float leftWorld = toWorldX(x);
        float rightWorld = toWorldX(x + width);
        float bottomWorld = toWorldY(y);
        float topWorld = toWorldY(y + height);

        for (float wx = (float) Math.ceil(leftWorld / GRID_SPACING) * GRID_SPACING;
                wx <= rightWorld; wx += GRID_SPACING) {
            ShopUi.drawQuad(Math.round(toScreenX(wx)), y, 1f, height, color, alpha);
        }

        for (float wy = (float) Math.ceil(bottomWorld / GRID_SPACING) * GRID_SPACING;
                wy <= topWorld; wy += GRID_SPACING) {
            ShopUi.drawQuad(x, Math.round(toScreenY(wy)), width, 1f, color, alpha);
        }
    }

    /**
     * The selected species' waters, shaded under the marks. Fill only, no outlines: an L-shaped
     * region is drawn as two rectangles, and at one flat alpha the pieces read as one patch where
     * outlines would draw a seam down the join. The scissor clip handles however far off the
     * glass a region reaches.
     */
    protected void renderAreas(float alphaMult) {
        for (AreaMark mark : areas) {
            float left = toScreenX(mark.x);
            float bottom = toScreenY(mark.y);
            float right = toScreenX(mark.x + mark.width);
            float top = toScreenY(mark.y + mark.height);

            ShopUi.drawQuad(left, bottom, right - left, top - bottom, mark.color, 0.1f * alphaMult);
        }
    }

    protected void renderSystems(float alphaMult) {
        boolean namesOn = ppu >= NAME_PPU;

        for (SystemMark mark : systems) {
            float sx = toScreenX(mark.loc.x);
            float sy = toScreenY(mark.loc.y);
            if (!isOnGlass(sx, sy, 60f)) continue;

            if (mark.lit && mark.litColor != null) {
                //the region light: an area statement, so it breathes wider than the star itself
                float glow = Math.max(10f, 2600f * ppu);
                Disc.draw(sx, sy, glow, mark.litColor, 0.3f * alphaMult, 0f, true);
            }

            float dot = mark.lit ? 2.5f : 1.5f;
            Color dotColor = mark.lit ? Color.WHITE : Misc.getGrayColor();

            ShopUi.drawQuad(Math.round(sx - dot), Math.round(sy - dot), dot * 2f, dot * 2f,
                    dotColor, (mark.lit ? 0.95f : 0.6f) * alphaMult);

            if (mark.lit || namesOn || hovered == mark) {
                drawName(mark, sx, sy, alphaMult);
            }
        }
    }

    protected void renderCatches(float alphaMult) {
        for (CatchMark mark : catches) {
            float sx = toScreenX(mark.loc.x);
            float sy = toScreenY(mark.loc.y);
            if (!isOnGlass(sx, sy, 30f)) continue;

            Disc.draw(sx, sy, 9f, mark.color, 0.5f * alphaMult, 0f, true);
            ShopUi.drawQuad(Math.round(sx - 2.5f), Math.round(sy - 2.5f), 5f, 5f, mark.color,
                    0.95f * alphaMult);
            Disc.drawOutline(sx, sy, 6.5f, mark.color, (hovered == mark ? 1f : 0.7f) * alphaMult, 1f);
        }
    }

    /** The player's fleet, so "where is that" always has a "relative to me". */
    protected void renderPlayer(float alphaMult) {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return;

        Vector2f loc = Global.getSector().getPlayerFleet().getLocationInHyperspace();
        float sx = toScreenX(loc.x);
        float sy = toScreenY(loc.y);
        if (!isOnGlass(sx, sy, 30f)) return;

        Disc.drawOutline(sx, sy, 7f, Misc.getBrightPlayerColor(), 0.9f * alphaMult, 1f);
        ShopUi.drawQuad(Math.round(sx - 1.5f), Math.round(sy - 1.5f), 3f, 3f,
                Misc.getBrightPlayerColor(), 0.9f * alphaMult);
    }

    /** One line along the bottom saying what the cursor is on, so the marks never need labels of their own. */
    protected void renderInfoStrip(float x, float y, float width, float alphaMult) {
        String text = getInfoText();
        if (text == null) return;

        ShopUi.drawQuad(x, y, width, INFO_STRIP, Color.BLACK, 0.65f * alphaMult);
        ShopUi.drawQuad(x, y + INFO_STRIP, width, 1f, Misc.getDarkPlayerColor(), 0.6f * alphaMult);

        LazyFont font = ShopUi.getBodyFont();
        if (font == null) return;

        if (infoText == null || !text.equals(infoShown)) {
            infoShown = text;
            infoText = ShopUi.createText(font, text);
            infoText.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        infoText.setBaseColor(ShopUi.withAlpha(Misc.getBasePlayerColor(), alphaMult));
        infoText.draw(Math.round(x + 10f),
                Math.round(y + INFO_STRIP * 0.5f + infoText.getHeight() * 0.5f));
    }

    protected String getInfoText() {
        if (hovered instanceof CatchMark) return ((CatchMark) hovered).label;

        if (hovered instanceof SystemMark) {
            SystemMark mark = (SystemMark) hovered;

            if (mark.species.isEmpty()) return mark.name;

            return mark.name + "  -  " + String.join(", ", mark.species);
        }

        return "Drag to pan, scroll to zoom";
    }

    protected void drawName(SystemMark mark, float sx, float sy, float alphaMult) {
        LazyFont font = ShopUi.getSmallFont();
        if (font == null) return;

        if (names == null) names = new HashMap<>();

        LazyFont.DrawableString text = names.get(mark.name);
        if (text == null) {
            text = ShopUi.createText(font, mark.name);
            text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            names.put(mark.name, text);
        }

        Color color = mark.lit ? Misc.getBasePlayerColor() : Misc.getGrayColor();

        text.setBaseColor(ShopUi.withAlpha(color, (mark.lit ? 0.95f : 0.6f) * alphaMult));
        text.draw(Math.round(sx - text.getWidth() * 0.5f), Math.round(sy - 6f));
    }

    protected boolean isOnGlass(float sx, float sy, float margin) {
        return sx >= pos.getX() - margin && sx <= pos.getX() + pos.getWidth() + margin
                && sy >= pos.getY() - margin && sy <= pos.getY() + pos.getHeight() + margin;
    }
}
