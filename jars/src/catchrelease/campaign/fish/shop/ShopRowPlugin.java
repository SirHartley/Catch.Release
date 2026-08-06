package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.tackle.Tackle;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.List;

/**
 * One line of the shelf list: the selection strip, the shopping-list ring, the name, and state
 * readable without selecting it (lit pips for a ladder, a price-coloured mark for a module,
 * MAX/FITTED said outright).
 * <p>
 * The ring is the mark-for-later toggle, lived-in rather than a button in the detail pane:
 * hollow until clicked, filled quest-yellow while the ware is on the shopping list. Every row
 * indents past the ring's slot whether or not it draws one, so the names stay in a column.
 * <p>
 * Drawn from live data every frame rather than assembled once, so a purchase never leaves a stale
 * row. Input handled by hand since a stock button can't give a hover glow, selection bar, and pips
 * their own regions within one row.
 */
public class ShopRowPlugin extends BaseCustomUIPanelPlugin {

    /** What a row needs from the pane it lives in. */
    public interface Host {
        boolean isSelected(ShopEntry entry);

        void onRowClicked(ShopEntry entry);

        /** The list's own rectangle, which is what rows clip and click against. */
        PositionAPI getListViewport();
    }

    public static final float PIP_SIZE = 8f;
    public static final float PIP_GAP = 3f;
    public static final float PAD_SIDE = 10f;
    public static final float ACCENT_WIDTH = 3f;

    /** The shopping-list ring's slot, sitting between the strip and the name. */
    public static final float MARK_SLOT = 18f;
    public static final float MARK_RADIUS = 5f;

    protected final ShopEntry entry;
    protected final Host host;

    protected PositionAPI pos;

    protected transient LazyFont.DrawableString name;
    protected transient LazyFont.DrawableString mark;
    protected transient String markText;

    public ShopRowPlugin(ShopEntry entry, Host host) {
        this.entry = entry;
        this.host = host;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        PositionAPI view = host.getListViewport();
        if (view == null) return;

        float x = pos.getX();
        float y = pos.getY();
        float width = pos.getWidth();
        float height = pos.getHeight();

        //culled if scrolled out of the visible window
        if (y + height < view.getY() || y > view.getY() + view.getHeight()) return;

        ShopUi.startClip(view.getX(), view.getY(), view.getWidth(), view.getHeight());

        boolean selected = host.isSelected(entry);
        boolean hovered = !selected && isMouseOver();

        if (selected) {
            ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.5f * alphaMult);
            ShopUi.drawQuad(x, y, ACCENT_WIDTH, height, Misc.getBrightPlayerColor(), 0.9f * alphaMult);
        } else if (hovered) {
            ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.3f * alphaMult);
        } else {
            ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.12f * alphaMult);
        }

        renderMarkRing(x, y, height, alphaMult);
        renderName(x, y, height, selected, alphaMult);
        renderState(x, y, width, height, alphaMult);

        ShopUi.endClip();
    }

    /** The shopping-list ring: hollow until clicked, filled quest-yellow while marked. */
    protected void renderMarkRing(float x, float y, float height, float alphaMult) {
        boolean marked = ShopMarks.isMarked(entry.getKey());
        if (!marked && !ShopMarks.isMarkable(entry)) return;

        float cx = x + ACCENT_WIDTH + MARK_SLOT * 0.5f;
        float cy = y + height * 0.5f;

        boolean hovered = isMouseOverMark();

        if (marked) {
            catchrelease.rendering.helper.Disc.draw(cx, cy, MARK_RADIUS,
                    Misc.getHighlightColor(), 0.95f * alphaMult, 0.95f * alphaMult, false);
        }

        Color rim = marked ? Misc.getHighlightColor()
                : hovered ? Misc.getBrightPlayerColor() : Misc.getGrayColor();

        catchrelease.rendering.helper.Disc.drawOutline(cx, cy, MARK_RADIUS, rim,
                (marked ? 0.95f : hovered ? 0.9f : 0.6f) * alphaMult, 1.2f);
    }

    protected void renderName(float x, float y, float height, boolean selected, float alphaMult) {
        LazyFont font = ShopUi.getBodyFont();
        if (font == null) return;

        if (name == null) {
            name = ShopUi.createText(font, entry.getListName());
            name.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        Color color = entry.isDone() || (entry.isCurio() && !entry.isOn()) ? Misc.getGrayColor()
                : selected ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

        name.setBaseColor(ShopUi.withAlpha(color, alphaMult));

        //rounded to the pixel - bitmap fonts blur off-pixel. Indented past the ring's slot
        //whether or not this row draws one, so the names stay in a column
        name.draw(Math.round(x + ACCENT_WIDTH + MARK_SLOT + 4f),
                Math.round(y + height * 0.5f + name.getHeight() * 0.5f));
    }

    /** The right-hand end of the row: pips for a ladder, a mark or a price tag for a module. */
    protected void renderState(float x, float y, float width, float height, float alphaMult) {
        float right = x + width - PAD_SIDE;

        if (entry.isUpgrade() && !entry.isMaxed()) {
            float pipsWidth = ShopUi.getPipRowWidth(entry.getMaxLevel(), PIP_SIZE, PIP_GAP);

            ShopUi.drawPips(Math.round(right - pipsWidth), Math.round(y + (height - PIP_SIZE) * 0.5f),
                    PIP_SIZE, PIP_GAP, entry.getLevel(), entry.getMaxLevel(),
                    Misc.getHighlightColor(), alphaMult);
            return;
        }

        //a switch says which way it is thrown, both ways - OFF has to be as readable as ON, since
        //an unmarked row would look like the shelf had simply not loaded
        if (entry.isCurio()) {
            drawMark(entry.isOn() ? "ON" : "OFF",
                    entry.isOn() ? Misc.getPositiveHighlightColor() : Misc.getGrayColor(),
                    right, y, height, alphaMult);
            return;
        }

        if (entry.isDone()) {
            drawMark(entry.isUpgrade() ? "MAX" : "FITTED", Misc.getPositiveHighlightColor(),
                    right, y, height, alphaMult);
            return;
        }

        //empty slot (NONE) counts as owned but stays unmarked - OWNED on an absence would claim there's a thing there
        if (entry.isOwned() && entry.tackle != Tackle.NONE) {
            drawMark("OWNED", Misc.getGrayColor(), right, y, height, alphaMult);
            return;
        }

        FishRarity rarity = entry.getPriceRarity();
        if (rarity == null) return;

        ShopUi.drawQuad(right - PIP_SIZE, y + (height - PIP_SIZE) * 0.5f, PIP_SIZE, PIP_SIZE,
                rarity.color, (entry.canAfford() ? 0.9f : 0.35f) * alphaMult);
    }

    protected void drawMark(String text, Color color, float right, float y, float height,
                            float alphaMult) {

        LazyFont font = ShopUi.getSmallFont();
        if (font == null) return;

        if (mark == null || !text.equals(markText)) {
            markText = text;
            mark = ShopUi.createText(font, text);
            mark.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
        }

        mark.setBaseColor(ShopUi.withAlpha(color, alphaMult));
        mark.draw(Math.round(right), Math.round(y + height * 0.5f + mark.getHeight() * 0.5f));
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (pos == null) return;

        for (InputEventAPI event : events) {
            if (event.isConsumed() || !event.isLMBDownEvent()) continue;
            if (!contains(event.getX(), event.getY())) continue;

            event.consume();
            Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);

            //the ring's slot toggles the mark; everywhere else selects the row
            if (isInMarkSlot(event.getX()) &&
                    (ShopMarks.isMarked(entry.getKey()) || ShopMarks.isMarkable(entry))) {
                ShopMarks.toggle(entry.getKey());
                return;
            }

            host.onRowClicked(entry);

            return;
        }
    }

    protected boolean isInMarkSlot(float pointX) {
        return pointX >= pos.getX() + ACCENT_WIDTH
                && pointX <= pos.getX() + ACCENT_WIDTH + MARK_SLOT;
    }

    protected boolean isMouseOverMark() {
        return isMouseOver() && isInMarkSlot(Global.getSettings().getMouseX());
    }

    /** Inside the row and inside the list - a row scrolled out of the window takes no clicks. */
    protected boolean contains(float pointX, float pointY) {
        PositionAPI view = host.getListViewport();

        if (view != null && !ShopUi.contains(view.getX(), view.getY(),
                view.getWidth(), view.getHeight(), pointX, pointY)) {
            return false;
        }

        return ShopUi.contains(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                pointX, pointY);
    }

    protected boolean isMouseOver() {
        return contains(Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
    }
}
