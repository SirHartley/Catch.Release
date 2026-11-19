package catchrelease.campaign.fish.shop;

import catchrelease.config.FishingSounds;
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

    public static final float ICON_SIZE = 24f;
    public static final float ICON_TOP = 3f;
    public static final float LABEL_TOP = 28f;

    protected final Object id;
    protected final String label;
    protected final String iconId;
    protected final Host host;
    protected PositionAPI pos;
    protected transient LazyFont.DrawableString text;

    protected SpriteAPI cachedIcon;
    protected boolean iconChecked;

    public interface Host {

        boolean isActiveTab(Object id);

        void onTabClicked(Object id);
    }

    public ShopTabPlugin(Object id, String label, String iconId, Host host) {
        this.id = id;
        this.label = label;
        this.iconId = iconId;
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
        float top = Math.round(y + height);

        boolean active = host.isActiveTab(id);
        boolean hovered = !active && isMouseOver();

        ShopUi.drawQuad(x, y, width, height, ShopUi.fill(), (active ? ShopUi.FILL_SELECTED
                : hovered ? ShopUi.FILL_HOVER : ShopUi.FILL_IDLE) * alphaMult);

        Color color = active ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

        SpriteAPI icon = getIcon();
        if (icon != null && icon.getWidth() > 0f) {
            icon.setSize(ICON_SIZE, ICON_SIZE);
            icon.setColor(color);
            icon.setNormalBlend();
            icon.setAlphaMult(alphaMult);
            icon.render(Math.round(x + (width - ICON_SIZE) * 0.5f), top - ICON_TOP - ICON_SIZE);
        }

        LazyFont font = ShopUi.getSmallFont();
        if (font == null) return;

        if (text == null) {
            text = ShopUi.createText(font, label);
            text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        text.setBaseColor(ShopUi.withAlpha(color, alphaMult));
        text.draw(Math.round(x + (width - text.getWidth()) * 0.5f), top - LABEL_TOP);
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
                FishingSounds.playUISound("ui_button_pressed", 1f, 1f);
                host.onTabClicked(id);
            }

            return;
        }
    }

    protected boolean isMouseOver() {
        return ShopUi.contains(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
    }

    // looked up once: every settings lookup allocates a fresh sprite, and this runs per frame
    protected SpriteAPI getIcon() {
        if (iconChecked) return cachedIcon;
        iconChecked = true;

        if (iconId == null || iconId.isEmpty()) return null;

        try {
            cachedIcon = SpriteLoader.getSprite(iconId);
        } catch (Exception e) {
            cachedIcon = null;
        }

        return cachedIcon;
    }
}
