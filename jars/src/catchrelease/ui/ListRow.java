package catchrelease.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;


public abstract class ListRow extends BaseCustomUIPanelPlugin {

    public static final float ACCENT_WIDTH = 3f;

    protected PositionAPI pos;

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }


    protected abstract PositionAPI getViewport();

    protected abstract boolean isSelected();


    protected abstract Color getAccentColor();


    protected abstract void renderContent(float x, float y, float width, float height,
                                          boolean selected, boolean hovered, float alphaMult);


    protected abstract void onRowClick(float pointX, float pointY);


    protected float getSelectedFieldAlpha() {
        return 0.4f;
    }


    protected void beforeCull(float x, float y, float width, float height) {
    }


    protected boolean handleKey(InputEventAPI event) {
        return false;
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        PositionAPI view = getViewport();
        if (view == null) return;

        float x = pos.getX();
        float y = pos.getY();
        float width = pos.getWidth();
        float height = pos.getHeight();

        beforeCull(x, y, width, height);

        if (y + height < view.getY() || y > view.getY() + view.getHeight()) return;

        ShopUi.startClip(view.getX(), view.getY(), view.getWidth(), view.getHeight());

        boolean selected = isSelected();
        boolean hovered = !selected && isMouseOver();

        float field = selected ? getSelectedFieldAlpha() : hovered ? 0.3f : 0.12f;
        ShopUi.drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), field * alphaMult);

        ShopUi.drawQuad(x, y, ACCENT_WIDTH, height, getAccentColor(),
                (selected ? 0.9f : hovered ? 0.6f : 0.3f) * alphaMult);

        renderContent(x, y, width, height, selected, hovered, alphaMult);

        ShopUi.endClip();
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (pos == null) return;

        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (event.isKeyDownEvent()) {
                if (handleKey(event)) return;
                continue;
            }

            if (!event.isLMBDownEvent()) continue;
            if (!contains(event.getX(), event.getY())) continue;

            Global.getSoundPlayer().playUISound(PaneWidgets.CLICK_SOUND, 1f, 1f);
            onRowClick(event.getX(), event.getY());
            event.consume();

            return;
        }
    }


    protected boolean contains(float pointX, float pointY) {
        PositionAPI view = getViewport();

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
