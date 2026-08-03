package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishRarity;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;

/**
 * One line of the shelf list.
 * <p>
 * The row itself is the game's own area checkbox, because the game's own widget is what gets the
 * game's own font, hover, click and sound - a hand-drawn row was a close copy with soft text, and a
 * close copy of a list row is exactly the kind of thing that reads as wrong without saying why.
 * What the widget cannot say is drawn over its right-hand end: lit pips for a ladder, a
 * price-coloured swatch for a module, MAX and FITTED said outright.
 * <p>
 * The checkbox is re-told its checked state every frame rather than being trusted with it - it
 * toggles itself on click like any checkbox, and the selection it is supposed to show lives with
 * the dialog.
 */
public class ShopRowPlugin extends BaseCustomUIPanelPlugin {

    /** What a row needs from the pane it lives in. */
    public interface Host {
        boolean isSelected(ShopEntry entry);

        void onRowClicked(ShopEntry entry);

        /** The list's own rectangle, which is what the overlay clips against. */
        PositionAPI getListViewport();
    }

    public static final float PIP_SIZE = 8f;
    public static final float PIP_GAP = 3f;
    public static final float PAD_SIDE = 10f;

    protected final ShopEntry entry;
    protected final Host host;

    protected PositionAPI pos;
    protected ButtonAPI box;

    protected transient LazyFont.DrawableString mark;
    protected transient String markText;

    protected ShopRowPlugin(ShopEntry entry, Host host) {
        this.entry = entry;
        this.host = host;
    }

    /** The whole row: the checkbox filling it, this plugin drawing over it. */
    public static CustomPanelAPI create(CustomPanelAPI dialogPanel, ShopEntry entry, Host host,
                                        float width, float height) {

        ShopRowPlugin plugin = new ShopRowPlugin(entry, host);
        CustomPanelAPI row = dialogPanel.createCustomPanel(width, height, plugin);

        TooltipMakerAPI content = row.createUIElement(width, height, false);

        Color base = entry.isDone() ? Misc.getGrayColor() : Misc.getBasePlayerColor();

        plugin.box = content.addAreaCheckbox(entry.getName(), entry, base,
                Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), width, height, 0f);
        plugin.box.setChecked(host.isSelected(entry));

        row.addUIElement(content).inTL(0f, 0f);

        return row;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (buttonId == entry) host.onRowClicked(entry);
    }

    /** The checkbox is a display of the selection, not the keeper of it. */
    @Override
    public void advance(float amount) {
        if (box != null) box.setChecked(host.isSelected(entry));
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
        renderState(x, y, width, height, alphaMult);
        ShopUi.endClip();
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

        ShopUi.drawQuad(Math.round(right - PIP_SIZE), Math.round(y + (height - PIP_SIZE) * 0.5f),
                PIP_SIZE, PIP_SIZE, rarity.color, (entry.canAfford() ? 0.9f : 0.35f) * alphaMult);
    }

    protected void drawMark(String text, Color color, float right, float y, float height,
                            float alphaMult) {

        LazyFont font = ShopUi.getSmallFont();
        if (font == null) return;

        if (mark == null || !text.equals(markText)) {
            markText = text;
            mark = font.createText(text, Color.WHITE, 12f);
            mark.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
        }

        mark.setBaseColor(ShopUi.withAlpha(color, alphaMult));

        //on the pixel, since a bitmap font off the pixel is what blur is
        mark.draw(Math.round(right), Math.round(y + height * 0.5f + mark.getHeight() * 0.5f));
    }
}
