package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.config.FishingSounds;
import catchrelease.ui.ListRow;
import catchrelease.ui.ShopUi;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.List;

// One cell of the Equipment tab's slot picker, shown while a rig has its second slot. The selected cell is where the
// FIT button puts a module; rows and the detail pane are judged against it. Drawn like the module rows beneath it, with
// their fill and left accent, so it reads as part of the list rather than as a second row of tabs.
public class ShopSlotPlugin extends BaseCustomUIPanelPlugin {

    // the rows' name column, so slot and module names line up
    public static final float TEXT_LEFT = ListRow.ACCENT_WIDTH + ShopRowPlugin.MARK_SLOT;
    public static final float TEXT_PAD = 10f;
    public static final float TEXT_GAP = 4f;
    public static final float LINE_GAP = 3f;

    protected final int slot;
    protected final Tackle fitted;
    protected final boolean shipModule;
    // decided for the row, not the cell: see FishShopDialog.needsCompactSlotText
    protected final boolean compact;
    protected final Host host;
    protected PositionAPI pos;

    protected transient LazyFont.DrawableString label;
    protected transient LazyFont.DrawableString content;
    protected transient LazyFont.DrawableString origin;

    public interface Host {

        int getTargetSlot();

        void onSlotClicked(int slot);
    }

    public ShopSlotPlugin(int slot, Tackle fitted, boolean compact, Host host) {
        this.slot = slot;
        this.fitted = fitted == null ? Tackle.NONE : fitted;
        this.compact = compact;
        this.host = host;
        // the distinction the rows draw as SHIP against OWNED: a module that comes with the ship is not the
        // player's, and a cell that shows both alike cannot say which one it is holding
        this.shipModule = this.fitted != Tackle.NONE && FishingShips.isShipModule(this.fitted)
                && !TackleManager.isOwned(this.fitted);
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getX();
        float y = pos.getY();
        float width = pos.getWidth();
        float height = pos.getHeight();

        boolean active = host.getTargetSlot() == slot;
        boolean hovered = !active && isMouseOver();

        // the module rows' fill and accent (ListRow, ShopRowPlugin.getSelectedFieldAlpha); the category tabs above
        // carry theirs along the bottom instead
        ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(),
                (active ? 0.5f : hovered ? 0.3f : 0.12f) * alphaMult);
        ShopUi.drawQuad(x, y, ListRow.ACCENT_WIDTH, height, Misc.getBrightPlayerColor(),
                (active ? 0.9f : hovered ? 0.6f : 0.3f) * alphaMult);

        LazyFont small = ShopUi.getSmallFont();
        LazyFont body = ShopUi.getBodyFont();
        if (small == null || body == null) return;

        if (label == null) {
            label = ShopUi.createText(small, "SLOT " + (slot + 1));
            label.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }
        if (content == null) {
            content = ShopUi.createText(compact ? small : body,
                    fitted == Tackle.NONE ? "Empty" : fitted.name);
            content.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        // both lines measured from the caption: the two line boxes used to meet with no leading between them while
        // five pixels went spare above and below
        float captionTop = y + height - TEXT_GAP;

        label.setBaseColor(ShopUi.withAlpha(active ? Misc.getBrightPlayerColor() : Misc.getGrayColor(), alphaMult));
        label.draw(Math.round(x + TEXT_LEFT), Math.round(captionTop));

        Color color = fitted == Tackle.NONE ? Misc.getGrayColor() : Misc.getPositiveHighlightColor();
        content.setBaseColor(ShopUi.withAlpha(color, alphaMult));
        content.draw(Math.round(x + TEXT_LEFT), Math.round(captionTop - label.getHeight() - LINE_GAP));

        if (!shipModule) return;

        if (origin == null) {
            origin = ShopUi.createText(small, "SHIP");
            origin.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
        }

        origin.setBaseColor(ShopUi.withAlpha(Misc.getGrayColor(), alphaMult));
        origin.draw(Math.round(x + width - TEXT_PAD), Math.round(captionTop));
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (pos == null) return;

        for (InputEventAPI event : events) {
            if (event.isConsumed() || !event.isLMBDownEvent()) continue;
            if (!ShopUi.contains(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                    event.getX(), event.getY())) {
                continue;
            }

            event.consume();

            if (host.getTargetSlot() != slot) {
                FishingSounds.playUISound("ui_button_pressed", 1f, 1f);
                host.onSlotClicked(slot);
            }

            return;
        }
    }

    protected boolean isMouseOver() {
        return ShopUi.contains(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
    }
}
