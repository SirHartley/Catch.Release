package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.config.FishingSounds;
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
// FIT button puts a module; rows and the detail pane are judged against it. Drawn like a category tab.
public class ShopSlotPlugin extends BaseCustomUIPanelPlugin {

    public static final float TEXT_PAD = 10f;

    protected final int slot;
    protected final Tackle fitted;
    protected final Host host;
    protected PositionAPI pos;

    protected transient LazyFont.DrawableString label;
    protected transient LazyFont.DrawableString content;

    public interface Host {

        int getTargetSlot();

        void onSlotClicked(int slot);
    }

    public ShopSlotPlugin(int slot, Tackle fitted, Host host) {
        this.slot = slot;
        this.fitted = fitted == null ? Tackle.NONE : fitted;
        this.host = host;
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

        ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(),
                (active ? 0.5f : hovered ? 0.35f : 0.18f) * alphaMult);
        ShopUi.drawQuad(x, y, width, ShopTabPlugin.ACCENT_HEIGHT, Misc.getBrightPlayerColor(),
                (active ? 0.95f : 0.35f) * alphaMult);

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
