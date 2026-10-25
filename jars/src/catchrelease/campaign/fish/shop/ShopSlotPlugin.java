package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.config.FishingSounds;
import catchrelease.rendering.helper.RoundedBorder;
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
// FIT button puts a module; rows and the detail pane are judged against it. Drawn as a bordered socket, deliberately
// unlike the category tabs it sits under.
public class ShopSlotPlugin extends BaseCustomUIPanelPlugin {

    public static final float TEXT_PAD = 10f;
    public static final float BORDER_RADIUS = 3f;

    protected final int slot;
    protected final Tackle fitted;
    protected final boolean shipModule;
    protected final Host host;
    protected PositionAPI pos;

    protected transient LazyFont.DrawableString label;
    protected transient LazyFont.DrawableString content;
    protected transient LazyFont.DrawableString origin;

    public interface Host {

        int getTargetSlot();

        void onSlotClicked(int slot);
    }

    public ShopSlotPlugin(int slot, Tackle fitted, Host host) {
        this.slot = slot;
        this.fitted = fitted == null ? Tackle.NONE : fitted;
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

        // A socket, not a tab. The category tabs immediately above are a flat fill plus a bottom accent bar and no
        // outline; a cell is a recessed well with a full rounded border and no accent, so two rows that select
        // different things do not read as one control.
        ShopUi.drawQuad(x, y, width, height, Color.BLACK, 0.5f * alphaMult);
        ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(),
                (active ? 0.38f : hovered ? 0.22f : 0.1f) * alphaMult);
        RoundedBorder.draw(x, y, width, height, BORDER_RADIUS,
                active ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor(),
                (active ? 0.95f : hovered ? 0.6f : 0.28f) * alphaMult, active ? 2f : 1f);

        LazyFont small = ShopUi.getSmallFont();
        LazyFont body = ShopUi.getBodyFont();
        if (small == null || body == null) return;

        if (label == null) {
            label = ShopUi.createText(small, "SLOT " + (slot + 1));
            label.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }
        if (content == null) {
            String name = fitted == Tackle.NONE ? "Empty" : fitted.name;
            content = ShopUi.createText(body, name);
            if (content.getWidth() > width - TEXT_PAD * 2f) content = ShopUi.createText(small, name);
            content.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        label.setBaseColor(ShopUi.withAlpha(Misc.getGrayColor(), alphaMult));
        label.draw(Math.round(x + TEXT_PAD), Math.round(y + height - 5f));

        Color color = fitted == Tackle.NONE ? Misc.getGrayColor() : Misc.getPositiveHighlightColor();
        content.setBaseColor(ShopUi.withAlpha(color, alphaMult));
        content.draw(Math.round(x + TEXT_PAD), Math.round(y + 6f + content.getHeight()));

        if (!shipModule) return;

        if (origin == null) {
            origin = ShopUi.createText(small, "SHIP");
            origin.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
        }

        origin.setBaseColor(ShopUi.withAlpha(Misc.getGrayColor(), alphaMult));
        origin.draw(Math.round(x + width - TEXT_PAD), Math.round(y + height - 5f));
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
