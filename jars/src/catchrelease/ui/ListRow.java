package catchrelease.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;

/**
 * The skeleton every scrolling-list row shares - the sidebar's species rows, the planner's, the
 * shop's shelf - so the grammar is written once: cull against the list's window, clip to it,
 * a dark field graded by state, the identity-coloured accent strip down the left edge, and a
 * click that only lands while the row is actually in view.
 * <p>
 * What a row says past that is its own - {@link #renderContent} draws inside the clip, and the
 * hooks let a subclass brighten its selected field, watch the cursor before the cull, or take
 * keys. Drawn from live data every frame rather than assembled once, so nothing here goes stale.
 */
public abstract class ListRow extends BaseCustomUIPanelPlugin {

    public static final float ACCENT_WIDTH = 3f;

    protected PositionAPI pos;

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    /** The list's own rectangle, which is what rows cull, clip and click against. */
    protected abstract PositionAPI getViewport();

    protected abstract boolean isSelected();

    /** The accent strip's colour - the row's identity, usually a rarity. */
    protected abstract Color getAccentColor();

    /** Everything past the field and the accent, drawn inside the list's clip. */
    protected abstract void renderContent(float x, float y, float width, float height,
                                          boolean selected, boolean hovered, float alphaMult);

    /** A click inside the row (and inside the list); the press sound has already played. */
    protected abstract void onRowClick(float pointX, float pointY);

    /** How bright the field sits while selected; the shop's rows hold a shade brighter. */
    protected float getSelectedFieldAlpha() {
        return 0.4f;
    }

    /** The field's colour - the dark player colour, unless a row has a reason to go grey. */
    protected Color getFieldColor() {
        return Misc.getDarkPlayerColor();
    }

    /** Runs even for a row scrolled out of view, before the cull - for hover bookkeeping
     *  that has to clear when the row leaves the window with the cursor still parked. */
    protected void beforeCull(float x, float y, float width, float height) {
    }

    /** A key while unconsumed events last; return true after consuming to stop the frame. */
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

        //culled if scrolled out of the visible window
        if (y + height < view.getY() || y > view.getY() + view.getHeight()) return;

        ShopUi.startClip(view.getX(), view.getY(), view.getWidth(), view.getHeight());

        boolean selected = isSelected();
        boolean hovered = !selected && isMouseOver();

        float field = selected ? getSelectedFieldAlpha() : hovered ? 0.3f : 0.12f;
        ShopUi.drawQuad(x, y, width, height, getFieldColor(), field * alphaMult);

        //every row wears its accent, graded by state
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

    /** Inside the row and inside the list - a row scrolled out of the window takes no clicks. */
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
