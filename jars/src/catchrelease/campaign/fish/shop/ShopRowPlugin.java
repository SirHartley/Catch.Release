package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishRarity;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.List;

/**
 * One line of the shelf list: the thing's name, and its state readable without selecting it - lit
 * pips for a ladder, a price-coloured mark for a module, MAX and FITTED said outright.
 * <p>
 * Drawn rather than assembled, because everything on it moves: the row reads level, fit, and
 * affordability off the live data every frame, so a purchase never leaves a stale row behind and
 * nothing has to find and rewrite it. Input is handled by hand for the same reason the look is -
 * a stock button owns its whole rectangle, and this row wants a hover glow, a selection bar, and
 * pips inside the same one.
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

        //a row the scroller has moved out of the window is not there
        if (y + height < view.getY() || y > view.getY() + view.getHeight()) return;

        ShopUi.startClip(view.getX(), view.getY(), view.getWidth(), view.getHeight());

        boolean selected = host.isSelected(entry);
        boolean hovered = !selected && isMouseOver();

        //the field: quiet by default, lit under the mouse, held bright while selected
        if (selected) {
            ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.5f * alphaMult);
            ShopUi.drawQuad(x, y, ACCENT_WIDTH, height, Misc.getBrightPlayerColor(), 0.9f * alphaMult);
        } else if (hovered) {
            ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.3f * alphaMult);
        } else {
            ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.12f * alphaMult);
        }

        renderName(x, y, height, selected, alphaMult);
        renderState(x, y, width, height, alphaMult);

        ShopUi.endClip();
    }

    protected void renderName(float x, float y, float height, boolean selected, float alphaMult) {
        LazyFont font = ShopUi.getBodyFont();
        if (font == null) return;

        if (name == null) {
            name = ShopUi.createText(font, entry.getListName());
            name.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        Color color = entry.isDone() ? Misc.getGrayColor()
                : selected ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

        name.setBaseColor(ShopUi.withAlpha(color, alphaMult));

        //on the pixel, since a bitmap font off the pixel is what blur is
        name.draw(Math.round(x + PAD_SIDE + ACCENT_WIDTH),
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

        if (entry.isDone()) {
            drawMark(entry.isUpgrade() ? "MAX" : "FITTED", Misc.getPositiveHighlightColor(),
                    right, y, height, alphaMult);
            return;
        }

        //an unbought module wears a swatch of what it is paid in
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
            host.onRowClicked(entry);

            return;
        }
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
