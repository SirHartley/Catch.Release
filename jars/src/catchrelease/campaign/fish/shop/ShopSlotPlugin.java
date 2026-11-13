package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.config.FishingSounds;
import catchrelease.ui.ShopUi;
import catchrelease.ui.UiKit;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.List;

// One cell of the Equipment tab's slot picker, shown while a rig has its second slot. The selected cell is where the
// Fit button puts a module; rows and the detail are judged against it.
public class ShopSlotPlugin extends BaseCustomUIPanelPlugin {

    // the filled tile sits this far inside the cell's 1 px outline
    public static final float INSET = 4f;
    public static final float TEXT_LEFT = 6f;
    public static final float LABEL_TOP = 3f;
    public static final float NAME_TOP = 19f;

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
        // the distinction the rows draw as Ship against Owned: a module that comes with the ship is not the
        // player's, and a cell that shows both alike cannot say which one it is holding
        this.shipModule = this.fitted != Tackle.NONE && FishingShips.isShipModule(this.fitted)
                && !TackleManager.isOwned(this.fitted);
    }

    public static float getTextWidth(float cellWidth) {
        return cellWidth - 2f * INSET - 2f * TEXT_LEFT;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = Math.round(pos.getX());
        float y = Math.round(pos.getY());
        float width = pos.getWidth();
        float height = pos.getHeight();

        boolean active = host.getTargetSlot() == slot;
        boolean hovered = !active && isMouseOver();

        UiKit.outline(x, y, width, height, active ? Misc.getBasePlayerColor() : ShopUi.fill(),
                alphaMult);
        ShopUi.drawQuad(x + INSET, y + INSET, width - 2f * INSET, height - 2f * INSET, ShopUi.fill(),
                (active ? ShopUi.FILL_SELECTED : hovered ? ShopUi.FILL_HOVER : ShopUi.FILL_IDLE)
                        * alphaMult);

        LazyFont small = ShopUi.getSmallFont();
        LazyFont body = ShopUi.getBodyFont();
        if (small == null || body == null) return;

        if (label == null) {
            label = ShopUi.createText(small, "Slot " + (slot + 1));
            label.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }
        if (content == null) {
            content = ShopUi.createText(compact ? small : body,
                    fitted == Tackle.NONE ? "Empty" : fitted.name);
            content.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        float left = x + INSET + TEXT_LEFT;
        float top = y + height - INSET;

        label.setBaseColor(ShopUi.withAlpha(active ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor(),
                alphaMult));
        label.draw(left, top - LABEL_TOP);

        Color color = fitted == Tackle.NONE ? Misc.getGrayColor() : Misc.getTextColor();
        content.setBaseColor(ShopUi.withAlpha(color, alphaMult));
        content.draw(left, top - NAME_TOP);

        if (!shipModule) return;

        if (origin == null) {
            origin = ShopUi.createText(small, "Ship");
            origin.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
        }

        origin.setBaseColor(ShopUi.withAlpha(Misc.getGrayColor(), alphaMult));
        origin.draw(x + width - INSET - TEXT_LEFT, top - LABEL_TOP);
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
