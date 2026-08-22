package catchrelease.campaign.fish.shop;

import catchrelease.ui.ShopUi;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.List;

public class ShopTabPlugin extends BaseCustomUIPanelPlugin {

    public static final float ACCENT_HEIGHT = 2f;
    public static final float ICON_SIZE = 18f;
    public static final float ICON_GAP = 6f;

    protected final Object id;
    protected final String label;
    protected final String iconId;
    protected final boolean vertical;
    protected final Host host;
    protected PositionAPI pos;
    protected transient LazyFont.DrawableString text;

    public interface Host {

        boolean isActiveTab(Object id);

        void onTabClicked(Object id);
    }

    public ShopTabPlugin(Object id, String label, String iconId, boolean vertical, Host host) {
        this.id = id;
        this.label = label;
        this.iconId = iconId;
        this.vertical = vertical;
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

        boolean active = host.isActiveTab(id);
        boolean hovered = !active && isMouseOver();

        if (active) {
            ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.5f * alphaMult);
            ShopUi.drawQuad(x, y, width, ACCENT_HEIGHT, Misc.getBrightPlayerColor(), 0.95f * alphaMult);
        } else {
            ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(),
                    (hovered ? 0.35f : 0.18f) * alphaMult);
            ShopUi.drawQuad(x, y, width, ACCENT_HEIGHT, Misc.getBrightPlayerColor(), 0.35f * alphaMult);
        }

        LazyFont font = ShopUi.getBodyFont();
        if (font == null) return;

        if (text == null) {
            text = ShopUi.createText(font, label);
            text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        SpriteAPI icon = iconId == null ? null : SpriteLoader.getSprite(iconId);
        Color color = active ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();
        text.setBaseColor(ShopUi.withAlpha(color, alphaMult));

        if (vertical && icon != null) {
            icon.setSize(ICON_SIZE, ICON_SIZE);
            icon.setColor(color);
            icon.setNormalBlend();
            icon.setAlphaMult(alphaMult);
            icon.renderAtCenter(Math.round(x + width * 0.5f),
                    Math.round(y + height - ACCENT_HEIGHT - 4f - ICON_SIZE * 0.5f));

            text.draw(Math.round(x + (width - text.getWidth()) * 0.5f),
                    Math.round(y + 3f + text.getHeight()));
            return;
        }

        float contentWidth = text.getWidth() + (icon == null ? 0f : ICON_SIZE + ICON_GAP);
        float left = x + (width - contentWidth) * 0.5f;

        if (icon != null) {
            icon.setSize(ICON_SIZE, ICON_SIZE);
            icon.setColor(color);
            icon.setNormalBlend();
            icon.setAlphaMult(alphaMult);
            icon.renderAtCenter(Math.round(left + ICON_SIZE * 0.5f), Math.round(y + height * 0.5f));

            left += ICON_SIZE + ICON_GAP;
        }

        text.draw(Math.round(left), Math.round(y + height * 0.5f + text.getHeight() * 0.5f));
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

            if (!host.isActiveTab(id)) {
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
                host.onTabClicked(id);
            }

            return;
        }
    }

    protected boolean isMouseOver() {
        return ShopUi.contains(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
    }
}
