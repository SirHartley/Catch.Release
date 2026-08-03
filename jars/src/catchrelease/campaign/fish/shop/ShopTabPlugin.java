package catchrelease.campaign.fish.shop;

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

/**
 * One tab over the shelf list, drawn in the rows' own language: a quiet field that lights under
 * the mouse and holds bright while it is the open one, with its mark - the underline along its
 * bottom edge - where a row carries its bar down the side. The main pair wear an icon beside the
 * label; the gear row is too narrow for one and says its word alone.
 */
public class ShopTabPlugin extends BaseCustomUIPanelPlugin {

    /** What a tab needs from the pane it lives in. */
    public interface Host {
        boolean isActiveTab(Object id);

        void onTabClicked(Object id);
    }

    public static final float ACCENT_HEIGHT = 2f;
    public static final float ICON_SIZE = 18f;
    public static final float ICON_GAP = 6f;

    protected final Object id;
    protected final String label;
    protected final String iconId;
    protected final float textSize;
    protected final Host host;

    protected PositionAPI pos;

    protected transient LazyFont.DrawableString text;

    public ShopTabPlugin(Object id, String label, String iconId, float textSize, Host host) {
        this.id = id;
        this.label = label;
        this.iconId = iconId;
        this.textSize = textSize;
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
            ShopUi.drawQuad(x, y, width, ACCENT_HEIGHT, Misc.getBrightPlayerColor(), 0.9f * alphaMult);
        } else if (hovered) {
            ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.3f * alphaMult);
        } else {
            ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.12f * alphaMult);
        }

        LazyFont font = ShopUi.getBodyFont();
        if (font == null) return;

        if (text == null) {
            text = font.createText(label, Color.WHITE, textSize);
            text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        SpriteAPI icon = iconId == null ? null : SpriteLoader.getSprite(iconId);
        float contentWidth = text.getWidth() + (icon == null ? 0f : ICON_SIZE + ICON_GAP);
        float left = x + (width - contentWidth) * 0.5f;

        if (icon != null) {
            icon.setSize(ICON_SIZE, ICON_SIZE);
            icon.setColor(active ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor());
            icon.setNormalBlend();
            icon.setAlphaMult(alphaMult);
            icon.renderAtCenter(Math.round(left + ICON_SIZE * 0.5f), Math.round(y + height * 0.5f));

            left += ICON_SIZE + ICON_GAP;
        }

        Color color = active ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

        text.setBaseColor(ShopUi.withAlpha(color, alphaMult));

        //on the pixel, since a bitmap font off the pixel is what blur is
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

            //an already-open tab takes the click and does nothing with it
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
