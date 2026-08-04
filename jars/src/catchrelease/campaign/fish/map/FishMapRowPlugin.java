package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopUi;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.List;

/**
 * One species in the map's sidebar, in the shop list's language: a rarity swatch, the name, and
 * what the log has on it at the right-hand end. Clicking one asks the map to go there - a list
 * beside a map that cannot point at the map is a legend, not a control.
 */
public class FishMapRowPlugin extends BaseCustomUIPanelPlugin {

    /** What a row needs from the pane it lives in. */
    public interface Host {
        void onRowClicked(FishSpec spec);

        PositionAPI getListViewport();
    }

    public static final float PAD_SIDE = 8f;
    public static final float SWATCH = 8f;

    protected final FishSpec spec;
    protected final String status;
    protected final Host host;

    protected PositionAPI pos;

    protected transient LazyFont.DrawableString name;
    protected transient LazyFont.DrawableString mark;

    public FishMapRowPlugin(FishSpec spec, String status, Host host) {
        this.spec = spec;
        this.status = status;
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

        if (y + height < view.getY() || y > view.getY() + view.getHeight()) return;

        ShopUi.startClip(view.getX(), view.getY(), view.getWidth(), view.getHeight());

        boolean hovered = isMouseOver();

        ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(),
                (hovered ? 0.3f : 0.12f) * alphaMult);

        ShopUi.drawQuad(Math.round(x + PAD_SIDE), Math.round(y + (height - SWATCH) * 0.5f),
                SWATCH, SWATCH, spec.rarity.color, 0.9f * alphaMult);

        LazyFont body = ShopUi.getBodyFont();
        if (body != null) {
            if (name == null) {
                name = ShopUi.createText(body, spec.getDisplayName());
                name.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            name.setBaseColor(ShopUi.withAlpha(
                    hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor(), alphaMult));
            name.draw(Math.round(x + PAD_SIDE * 2f + SWATCH),
                    Math.round(y + height * 0.5f + name.getHeight() * 0.5f));
        }

        LazyFont small = ShopUi.getSmallFont();
        if (small != null && status != null) {
            if (mark == null) {
                mark = ShopUi.createText(small, status);
                mark.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
            }

            mark.setBaseColor(ShopUi.withAlpha(Misc.getGrayColor(), alphaMult));
            mark.draw(Math.round(x + width - PAD_SIDE),
                    Math.round(y + height * 0.5f + mark.getHeight() * 0.5f));
        }

        ShopUi.endClip();
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (pos == null) return;

        for (InputEventAPI event : events) {
            if (event.isConsumed() || !event.isLMBDownEvent()) continue;
            if (!contains(event.getX(), event.getY())) continue;

            event.consume();
            Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
            host.onRowClicked(spec);

            return;
        }
    }

    protected boolean contains(float pointX, float pointY) {
        PositionAPI view = host.getListViewport();

        if (view != null && !ShopUi.contains(view.getX(), view.getY(), view.getWidth(),
                view.getHeight(), pointX, pointY)) {
            return false;
        }

        return ShopUi.contains(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                pointX, pointY);
    }

    protected boolean isMouseOver() {
        return contains(Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
    }
}
