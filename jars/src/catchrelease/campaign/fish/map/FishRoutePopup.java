package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The fishing planner: a card over the map that asks one question - which fish do you need? -
 * and turns the answer into a plotted route.
 * <p>
 * Leads with what's already being asked for (open jobs and the shop's next rungs, each tagged
 * with who wants it), then every other known species below. Up to
 * {@link FishRoutePlanner#MAX_PICKS} picks; PLOT ROUTE hands them to the planner and closes.
 * Drawn by hand, like the pane's chips - the tooltip/button machinery belongs to the map screen
 * underneath - and every event inside the card is consumed so a click here never pans the map.
 */
public class FishRoutePopup extends BaseCustomUIPanelPlugin {

    /** What the popup needs from whoever floated it over the map. */
    public interface Host {
        void onRoutePlotted(FishRoute.Saved route);

        void onPlannerClosed();
    }

    public static final float PAD = 12f;
    public static final float ROW_HEIGHT = 24f;
    public static final float ICON = 18f;
    public static final float ICON_GAP = 8f;
    public static final float HEADER_GAP = 6f;
    public static final float BUTTON_HEIGHT = 26f;
    public static final float SCROLL_STEP = 40f;

    protected final Host host;

    /** Everything shown, wanted species first - built once when the popup opens. */
    protected final List<Row> rows = new ArrayList<>();
    protected final Set<String> selected = new LinkedHashSet<>();

    protected PositionAPI pos;
    protected float scroll = 0f;

    protected float mouseX = -1f;
    protected float mouseY = -1f;

    protected static class Row {
        FishSpec spec;
        String reason;
    }

    public FishRoutePopup(Host host) {
        this.host = host;

        Set<String> suggested = new LinkedHashSet<>();

        for (FishRoutePlanner.Suggestion suggestion : FishRoutePlanner.getSuggestions()) {
            FishSpec spec = FishPresence.getSpec(suggestion.speciesId);
            if (spec == null || !suggested.add(spec.id)) continue;

            Row row = new Row();
            row.spec = spec;
            row.reason = suggestion.reason;
            rows.add(row);
        }

        FishPresence.Filter everything = new FishPresence.Filter();
        for (FishSpec spec : FishPresence.getShown(everything)) {
            if (suggested.contains(spec.id)) continue;

            Row row = new Row();
            row.spec = spec;
            rows.add(row);
        }
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (pos == null) return;

        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (event.isMouseMoveEvent() || event.isMouseEvent()) {
                mouseX = event.getX();
                mouseY = event.getY();
            }

            boolean inside = mouseX >= pos.getX() && mouseX <= pos.getX() + pos.getWidth()
                    && mouseY >= pos.getY() && mouseY <= pos.getY() + pos.getHeight();

            if (!inside) continue;

            if (event.isMouseScrollEvent()) {
                scroll -= Math.signum(event.getEventValue()) * SCROLL_STEP;
                clampScroll();
                event.consume();
                continue;
            }

            if (event.isLMBDownEvent()) {
                handleClick();
                event.consume();
                continue;
            }

            //everything else inside the card stays inside the card - the map must not pan under it
            if (event.isMouseEvent()) event.consume();
        }
    }

    protected void handleClick() {
        //the two buttons along the bottom
        float buttonsY = pos.getY() + PAD;
        float half = (pos.getWidth() - PAD * 3f) / 2f;

        if (mouseY >= buttonsY && mouseY <= buttonsY + BUTTON_HEIGHT) {
            if (mouseX <= pos.getX() + PAD + half) {
                plot();
            } else {
                host.onPlannerClosed();
            }
            return;
        }

        //then the rows
        int index = rowIndexAt(mouseX, mouseY);
        if (index < 0 || index >= rows.size()) return;

        String id = rows.get(index).spec.id;

        if (!selected.remove(id)) {
            if (selected.size() >= FishRoutePlanner.MAX_PICKS) return;
            selected.add(id);
        }
    }

    protected void plot() {
        if (selected.isEmpty()) return;

        FishRoute.Saved route = FishRoutePlanner.plan(new ArrayList<>(selected));
        if (route == null) return;

        FishRoute.set(route);
        host.onRoutePlotted(route);
    }

    /** The list's viewport: below the title, above the buttons. */
    protected float getListTop() {
        return pos.getY() + pos.getHeight() - PAD - getTitleHeight() - HEADER_GAP;
    }

    protected float getListBottom() {
        return pos.getY() + PAD + BUTTON_HEIGHT + PAD;
    }

    protected float getTitleHeight() {
        LazyFont body = ShopUi.getBodyFont();
        LazyFont small = ShopUi.getSmallFont();

        float bodyHeight = body == null ? 16f : body.getBaseHeight();
        float smallHeight = small == null ? 13f : small.getBaseHeight();

        return bodyHeight + 4f + smallHeight + 8f;
    }

    protected int rowIndexAt(float x, float y) {
        if (y > getListTop() || y < getListBottom()) return -1;

        float fromTop = getListTop() - y + scroll;

        return (int) Math.floor(fromTop / ROW_HEIGHT);
    }

    protected void clampScroll() {
        float visible = getListTop() - getListBottom();
        float content = rows.size() * ROW_HEIGHT;

        scroll = MathUtils.clamp(scroll, 0f, Math.max(0f, content - visible));
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        LazyFont body = ShopUi.getBodyFont();
        LazyFont small = ShopUi.getSmallFont();
        if (body == null || small == null) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        //the pane's own dressing: dark field, one-pixel player border, corners square
        ShopUi.drawQuad(x - 1f, y - 1f, w + 2f, h + 2f, Misc.getDarkPlayerColor(), alphaMult);
        ShopUi.drawQuad(x, y, w, h, Color.BLACK, 0.92f * alphaMult);
        ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(), 0.07f * alphaMult);

        LazyFont.DrawableString title = body.createText("FISHING PLANNER",
                Misc.getBasePlayerColor(), body.getBaseHeight());
        title.draw(Math.round(x + PAD), Math.round(y + h - PAD));

        LazyFont.DrawableString hint = small.createText(
                "Pick up to " + FishRoutePlanner.MAX_PICKS + " - wanted fish first",
                Misc.getGrayColor(), small.getBaseHeight());
        hint.draw(Math.round(x + PAD), Math.round(y + h - PAD - title.getHeight() - 4f));

        renderRows(small, alphaMult);
        renderButtons(small, alphaMult);
    }

    protected void renderRows(LazyFont small, float alphaMult) {
        float top = getListTop();
        float bottom = getListBottom();
        float x = pos.getX();
        float w = pos.getWidth();

        float rowTop = top + scroll;

        for (Row row : rows) {
            float rowBottom = rowTop - ROW_HEIGHT;

            if (rowTop < bottom || rowBottom > top) {
                rowTop = rowBottom;
                continue;
            }

            boolean picked = selected.contains(row.spec.id);
            boolean hovered = mouseY <= rowTop && mouseY > rowBottom
                    && mouseX >= x && mouseX <= x + w;

            if (picked) {
                ShopUi.drawQuad(x + PAD * 0.5f, rowBottom, w - PAD, ROW_HEIGHT,
                        Misc.getDarkPlayerColor(), 0.5f * alphaMult);
            } else if (hovered) {
                ShopUi.drawQuad(x + PAD * 0.5f, rowBottom, w - PAD, ROW_HEIGHT,
                        Misc.getDarkPlayerColor(), 0.25f * alphaMult);
            }

            //the art only once one has been landed - a survey knows the name, not the face
            String iconPath = FishLog.isCaught(row.spec.id)
                    ? FishCodex.getIcon(row.spec) : FishConstants.ITEM_ICON_FALLBACK;

            SpriteAPI icon = SpriteLoader.loadSprite(iconPath);
            if (icon != null) {
                icon.setSize(ICON, ICON);
                icon.setColor(Color.WHITE);
                icon.setNormalBlend();
                icon.setAlphaMult(alphaMult);
                icon.renderAtCenter(Math.round(x + PAD + ICON * 0.5f),
                        Math.round(rowBottom + ROW_HEIGHT * 0.5f));
            }

            LazyFont.DrawableString name = small.createText(row.spec.getDisplayName(),
                    row.spec.rarity.color, small.getBaseHeight());
            name.draw(Math.round(x + PAD + ICON + ICON_GAP),
                    Math.round(rowBottom + (ROW_HEIGHT + name.getHeight()) * 0.5f));

            if (row.reason != null) {
                LazyFont.DrawableString reason = small.createText(row.reason,
                        Misc.getHighlightColor(), small.getBaseHeight());
                reason.draw(Math.round(x + pos.getWidth() - PAD - reason.getWidth()),
                        Math.round(rowBottom + (ROW_HEIGHT + reason.getHeight()) * 0.5f));
            }

            rowTop = rowBottom;
        }
    }

    protected void renderButtons(LazyFont small, float alphaMult) {
        float x = pos.getX();
        float y = pos.getY() + PAD;
        float half = (pos.getWidth() - PAD * 3f) / 2f;

        boolean canPlot = !selected.isEmpty();

        drawButton(small, x + PAD, y, half, "PLOT ROUTE (" + selected.size() + ")",
                canPlot ? Misc.getBasePlayerColor() : Misc.getGrayColor(), alphaMult);
        drawButton(small, x + PAD * 2f + half, y, half, "CLOSE",
                Misc.getBasePlayerColor(), alphaMult);
    }

    protected void drawButton(LazyFont small, float x, float y, float width,
                              String label, Color color, float alphaMult) {

        ShopUi.drawQuad(x, y, width, BUTTON_HEIGHT, Misc.getDarkPlayerColor(), 0.4f * alphaMult);
        ShopUi.drawQuad(x, y, width, 1f, color, alphaMult);
        ShopUi.drawQuad(x, y + BUTTON_HEIGHT - 1f, width, 1f, color, alphaMult);
        ShopUi.drawQuad(x, y, 1f, BUTTON_HEIGHT, color, alphaMult);
        ShopUi.drawQuad(x + width - 1f, y, 1f, BUTTON_HEIGHT, color, alphaMult);

        LazyFont.DrawableString text = small.createText(label, color, small.getBaseHeight());
        text.draw(Math.round(x + (width - text.getWidth()) * 0.5f),
                Math.round(y + (BUTTON_HEIGHT + text.getHeight()) * 0.5f));
    }
}
