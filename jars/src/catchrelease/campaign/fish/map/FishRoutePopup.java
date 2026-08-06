package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fishing planner: a card in the sidebar's slot that asks one question - which fish do you
 * need? - and turns the answer into a plotted route.
 * <p>
 * Finding the fish works the way the sidebar itself works: a search field, one chip per type,
 * and the species as rows - wanted ones (open jobs and the shop's next rungs, each tagged with
 * who wants it) pinned first. Up to {@link FishRoutePlanner#MAX_PICKS} picks; the full-width
 * PLOT ROUTE along the bottom hands them to the planner, and the X in the top corner is the
 * way out. The list clips to its own viewport, so a half-scrolled row never sits on the button.
 * <p>
 * Drawn by hand like the pane's chips, except the search field, which is the game's own -
 * mounted through {@link #mount} so the keyboard works.
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
    public static final float BUTTON_HEIGHT = 26f;
    public static final float SCROLL_STEP = 40f;

    public static final float CLOSE_SIZE = 20f;

    public static final float SEARCH_HEIGHT = 22f;
    public static final float CHIP_HEIGHT = 22f;
    public static final float CHIP_GAP = 4f;

    /** The line above the button where the card answers back. */
    public static final float NOTICE_HEIGHT = 18f;

    /** Top of the search field, measured down from the card's top edge. */
    public static final float SEARCH_TOP = 48f;

    public static final String SEARCH_GHOST = "Search...";

    protected final Host host;

    /** Who is asking for a species, for the tag on its row - the pin order is the ask order. */
    protected final Map<String, String> reasons = new LinkedHashMap<>();

    /** What the current filter lets through, wanted species first. */
    protected final List<Row> rows = new ArrayList<>();
    protected final Set<String> selected = new LinkedHashSet<>();

    protected final FishPresence.Filter filter = new FishPresence.Filter();

    protected TextFieldAPI searchField;

    /** The card talking back: why a click did nothing, or why a plot refused. */
    protected String notice;
    protected Color noticeColor;

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

        for (FishRoutePlanner.Suggestion suggestion : FishRoutePlanner.getSuggestions()) {
            reasons.putIfAbsent(suggestion.speciesId, suggestion.reason);
        }

        rebuildRows();
    }

    /** The one native control: the search field, added to the card's own panel. Call once. */
    public void mount(CustomPanelAPI panel, float width) {
        TooltipMakerAPI element = panel.createUIElement(width - PAD * 2f, SEARCH_HEIGHT, false);

        searchField = element.addTextField(width - PAD * 2f, SEARCH_HEIGHT, ShopUi.FONT_SMALL, 0f);
        searchField.setText(SEARCH_GHOST);

        //invisible on the card's black otherwise - the field's own face, not a painted backing
        searchField.setBgColor(ShopUi.withAlpha(Misc.getDarkPlayerColor(), 0.55f));

        searchSlot = panel.addUIElement(element).inTL(PAD, SEARCH_TOP);
    }

    /** Where the search element was told to stand, kept so a layout drift can be walked back. */
    protected PositionAPI searchSlot;
    protected boolean searchAligned = false;

    /**
     * The element machinery stands the field a few pixels off where it was told to; measured
     * once against the card's own edge and walked back, so the field lines up with the chips
     * and rows drawn by hand below it.
     */
    protected void alignSearch() {
        if (searchAligned || searchSlot == null || pos == null || searchField == null) return;

        PositionAPI field = searchField.getPosition();
        if (field == null || field.getWidth() <= 0f) return;

        float drift = field.getX() - (pos.getX() + PAD);
        if (Math.abs(drift) > 0.5f) searchSlot.inTL(PAD - drift, SEARCH_TOP);

        searchAligned = true;
    }

    /** Same hand-worked ghost text as the sidebar's field - there is no change callback. */
    @Override
    public void advance(float amount) {
        if (searchField == null) return;

        alignSearch();

        String text = searchField.getText();
        boolean focused = searchField.hasFocus();

        if (focused && SEARCH_GHOST.equals(text)) {
            searchField.deleteAll(false);
            text = "";
        } else if (!focused && (text == null || text.isEmpty())) {
            searchField.setText(SEARCH_GHOST);
            text = SEARCH_GHOST;
        }

        String effective = text == null || SEARCH_GHOST.equals(text) ? "" : text;
        String current = filter.search == null ? "" : filter.search;

        if (!effective.equals(current)) {
            filter.search = effective;
            rebuildRows();
        }
    }

    /** Fresh rows for the current filter: pinned asks first, then the rest of what is known. */
    protected void rebuildRows() {
        rows.clear();

        Set<String> pinned = new LinkedHashSet<>();

        for (Map.Entry<String, String> ask : reasons.entrySet()) {
            FishSpec spec = FishPresence.getSpec(ask.getKey());
            if (spec == null || !FishPresence.isKnown(spec)) continue;
            if (!filter.accepts(spec)) continue;
            if (!pinned.add(spec.id)) continue;

            Row row = new Row();
            row.spec = spec;
            row.reason = ask.getValue();
            rows.add(row);
        }

        for (FishSpec spec : FishPresence.getShown(filter)) {
            if (pinned.contains(spec.id)) continue;

            Row row = new Row();
            row.spec = spec;
            rows.add(row);
        }

        clampScroll();
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

            //the search field is the game's own widget - its clicks have to reach it
            if (isInSearch(mouseX, mouseY)) continue;

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

            //everything else inside the card stays inside the card
            if (event.isMouseEvent()) event.consume();
        }
    }

    protected boolean isInSearch(float x, float y) {
        //the field's real rectangle when it has one - the assumed slot only as a fallback
        if (searchField != null) {
            PositionAPI field = searchField.getPosition();

            if (field != null && field.getWidth() > 0f) {
                return ShopUi.contains(field.getX(), field.getY(),
                        field.getWidth(), field.getHeight(), x, y);
            }
        }

        float top = pos.getY() + pos.getHeight() - SEARCH_TOP;

        return x >= pos.getX() + PAD && x <= pos.getX() + pos.getWidth() - PAD
                && y <= top && y >= top - SEARCH_HEIGHT;
    }

    protected void handleClick() {
        //the way out, top right corner
        if (isInClose(mouseX, mouseY)) {
            host.onPlannerClosed();
            return;
        }

        //the type chips
        int chip = chipIndexAt(mouseX, mouseY);
        if (chip >= 0) {
            FishType type = FishType.values()[chip];
            if (!filter.types.remove(type)) filter.types.add(type);
            rebuildRows();
            return;
        }

        //the one button along the bottom
        float buttonsY = pos.getY() + PAD;
        if (mouseY >= buttonsY && mouseY <= buttonsY + BUTTON_HEIGHT
                && mouseX >= pos.getX() + PAD
                && mouseX <= pos.getX() + pos.getWidth() - PAD) {
            plot();
            return;
        }

        //then the rows
        int index = rowIndexAt(mouseX, mouseY);
        if (index < 0 || index >= rows.size()) return;

        String id = rows.get(index).spec.id;

        if (!selected.remove(id)) {
            if (selected.size() >= FishRoutePlanner.MAX_PICKS) {
                //refusal with a reason, short enough to stay one line
                say("All " + FishRoutePlanner.MAX_PICKS + " picks used.",
                        Misc.getHighlightColor());
                return;
            }
            selected.add(id);
        }

        notice = null;
    }

    protected void say(String what, Color color) {
        notice = what;
        noticeColor = color;
    }

    protected boolean isInClose(float x, float y) {
        //top pad matches the side pad, so the X sits square in its corner
        float left = pos.getX() + pos.getWidth() - PAD - CLOSE_SIZE;
        float top = pos.getY() + pos.getHeight() - PAD;

        return x >= left && x <= left + CLOSE_SIZE + PAD
                && y <= top + PAD && y >= top - CLOSE_SIZE;
    }

    protected int chipIndexAt(float x, float y) {
        float top = getChipTop();
        if (y > top || y < top - CHIP_HEIGHT) return -1;

        FishType[] types = FishType.values();
        float innerWidth = pos.getWidth() - PAD * 2f;
        float chipWidth = (innerWidth - CHIP_GAP * (types.length - 1)) / types.length;

        float fromLeft = x - (pos.getX() + PAD);
        if (fromLeft < 0f) return -1;

        int index = (int) (fromLeft / (chipWidth + CHIP_GAP));
        if (index >= types.length) return -1;

        //the gap between chips belongs to nobody
        if (fromLeft - index * (chipWidth + CHIP_GAP) > chipWidth) return -1;

        return index;
    }

    protected void plot() {
        if (selected.isEmpty()) {
            say("Pick a fish first.", Misc.getGrayColor());
            return;
        }

        //refuse rather than quietly go without: a route that silently dropped a pick would
        //read as the fish being on it
        List<String> stranded = FishRoutePlanner.getUnplaceable(new ArrayList<>(selected));
        if (!stranded.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String id : stranded) {
                FishSpec spec = FishPresence.getSpec(id);
                names.add(spec == null ? id : spec.getDisplayName());
            }

            //the notice is one line; a long roll call gets counted instead of read out
            String listed = names.size() > 2
                    ? names.get(0) + ", " + names.get(1) + " +" + (names.size() - 2) + " more"
                    : String.join(", ", names);

            say("No charted waters for " + listed + ".", Misc.getNegativeHighlightColor());
            return;
        }

        FishRoute.Saved route = FishRoutePlanner.plan(new ArrayList<>(selected));
        if (route == null) {
            say("No route - nothing picked has charted waters.", Misc.getNegativeHighlightColor());
            return;
        }

        FishRoute.set(route);
        host.onRoutePlotted(route);
    }

    protected float getChipTop() {
        return pos.getY() + pos.getHeight() - SEARCH_TOP - SEARCH_HEIGHT - 6f;
    }

    /** The list's viewport: below the chips, above the button. */
    protected float getListTop() {
        return getChipTop() - CHIP_HEIGHT - 8f;
    }

    protected float getListBottom() {
        return pos.getY() + PAD + BUTTON_HEIGHT + NOTICE_HEIGHT + PAD;
    }

    protected int rowIndexAt(float x, float y) {
        if (y > getListTop() || y < getListBottom()) return -1;

        float fromTop = getListTop() - y + scroll;

        return (int) Math.floor(fromTop / ROW_HEIGHT);
    }

    protected void clampScroll() {
        if (pos == null) return;

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
        ShopUi.drawQuad(x, y, w, h, Color.BLACK, 0.7f * alphaMult);

        LazyFont.DrawableString title = body.createText("FISHING PLANNER",
                Misc.getBasePlayerColor(), body.getBaseHeight());
        title.draw(Math.round(x + PAD), Math.round(y + h - PAD));

        renderClose(small, alphaMult);

        LazyFont.DrawableString hint = small.createText(
                "Pick up to " + FishRoutePlanner.MAX_PICKS + " - wanted fish first",
                Misc.getGrayColor(), small.getBaseHeight());
        hint.draw(Math.round(x + PAD), Math.round(y + h - PAD - title.getHeight() - 4f));

        renderChips(small, alphaMult);
        renderRows(small, alphaMult);
        renderNotice(small, alphaMult);
        renderPlotButton(small, alphaMult);
    }

    /** The answer-back line, anchored just above the button and growing upward - a wrapped
     *  second line eats into the list rather than lying across the button it explains. */
    protected void renderNotice(LazyFont small, float alphaMult) {
        if (notice == null) return;

        float width = pos.getWidth() - PAD * 2f;

        LazyFont.DrawableString line = small.createText(notice,
                ShopUi.withAlpha(noticeColor == null ? Misc.getGrayColor() : noticeColor,
                        alphaMult), small.getBaseHeight(), width);

        float bottom = pos.getY() + PAD + BUTTON_HEIGHT + 4f;
        line.draw(Math.round(pos.getX() + PAD), Math.round(bottom + line.getHeight()));
    }

    /** The X, top right, its top pad matching its side pad. */
    protected void renderClose(LazyFont small, float alphaMult) {
        float left = pos.getX() + pos.getWidth() - PAD - CLOSE_SIZE;
        float bottom = pos.getY() + pos.getHeight() - PAD - CLOSE_SIZE;

        boolean hovered = isInClose(mouseX, mouseY);
        Color color = hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

        ShopUi.drawQuad(left, bottom, CLOSE_SIZE, CLOSE_SIZE, Misc.getDarkPlayerColor(),
                (hovered ? 0.5f : 0.3f) * alphaMult);

        LazyFont.DrawableString mark = small.createText("X", color, small.getBaseHeight());
        mark.draw(Math.round(left + (CLOSE_SIZE - mark.getWidth()) * 0.5f),
                Math.round(bottom + (CLOSE_SIZE + mark.getHeight()) * 0.5f));
    }

    /** The type chips, the sidebar's idea at row scale: lit while shown, dark while hidden. */
    protected void renderChips(LazyFont small, float alphaMult) {
        FishType[] types = FishType.values();

        float innerWidth = pos.getWidth() - PAD * 2f;
        float chipWidth = (innerWidth - CHIP_GAP * (types.length - 1)) / types.length;
        float top = getChipTop();

        for (int i = 0; i < types.length; i++) {
            FishType type = types[i];

            float left = pos.getX() + PAD + i * (chipWidth + CHIP_GAP);
            float bottom = top - CHIP_HEIGHT;

            //an empty set means everything shows - the chips only narrow once any are on
            boolean on = filter.types.isEmpty() || filter.types.contains(type);
            boolean lit = filter.types.contains(type);
            boolean hovered = mouseX >= left && mouseX <= left + chipWidth
                    && mouseY >= bottom && mouseY <= top;

            if (lit) {
                ShopUi.drawQuad(left, bottom, chipWidth, CHIP_HEIGHT, type.color,
                        (hovered ? 0.5f : 0.35f) * alphaMult);
            } else {
                ShopUi.drawQuad(left, bottom, chipWidth, CHIP_HEIGHT, Misc.getDarkPlayerColor(),
                        (hovered ? 0.35f : 0.18f) * alphaMult);
            }
            ShopUi.drawQuad(left, bottom, chipWidth, 2f, type.color,
                    (lit ? 0.95f : 0.35f) * alphaMult);

            LazyFont.DrawableString label = small.createText(type.label,
                    ShopUi.withAlpha(on ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor(),
                            alphaMult), small.getBaseHeight());
            label.draw(Math.round(left + (chipWidth - label.getWidth()) * 0.5f),
                    Math.round(bottom + (CHIP_HEIGHT + label.getHeight()) * 0.5f));
        }
    }

    protected void renderRows(LazyFont small, float alphaMult) {
        float top = getListTop();
        float bottom = getListBottom();
        float x = pos.getX();
        float w = pos.getWidth();

        //the viewport is a hard edge: a half-scrolled row ends at it instead of lying on
        //whatever is beyond it
        ShopUi.startClip(x, bottom, w, top - bottom);

        float rowTop = top + scroll;

        for (Row row : rows) {
            float rowBottom = rowTop - ROW_HEIGHT;

            if (rowBottom > top || rowTop < bottom) {
                rowTop = rowBottom;
                continue;
            }

            boolean picked = selected.contains(row.spec.id);
            boolean hovered = mouseY <= rowTop && mouseY > rowBottom
                    && mouseX >= x && mouseX <= x + w
                    && mouseY <= top && mouseY >= bottom;

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

            //the shopping-list dot, bottom right of the icon, same corner as everywhere
            if (ShopMarks.isMarked(row.spec)) {
                ShopMarks.drawDot(x + PAD + ICON, rowBottom + (ROW_HEIGHT - ICON) * 0.5f + 1f,
                        ShopMarks.DOT_RADIUS - 0.5f, alphaMult);
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

        ShopUi.endClip();
    }

    /** One full-width button: plotting is the card's whole job, so it gets the whole row. */
    protected void renderPlotButton(LazyFont small, float alphaMult) {
        float x = pos.getX() + PAD;
        float y = pos.getY() + PAD;
        float width = pos.getWidth() - PAD * 2f;

        boolean canPlot = !selected.isEmpty();
        Color color = canPlot ? Misc.getBasePlayerColor() : Misc.getGrayColor();

        ShopUi.drawQuad(x, y, width, BUTTON_HEIGHT, Misc.getDarkPlayerColor(), 0.4f * alphaMult);
        ShopUi.drawQuad(x, y, width, 1f, color, alphaMult);
        ShopUi.drawQuad(x, y + BUTTON_HEIGHT - 1f, width, 1f, color, alphaMult);
        ShopUi.drawQuad(x, y, 1f, BUTTON_HEIGHT, color, alphaMult);
        ShopUi.drawQuad(x + width - 1f, y, 1f, BUTTON_HEIGHT, color, alphaMult);

        LazyFont.DrawableString text = small.createText(
                "PLOT ROUTE (" + selected.size() + ")", color, small.getBaseHeight());
        text.draw(Math.round(x + (width - text.getWidth()) * 0.5f),
                Math.round(y + (BUTTON_HEIGHT + text.getHeight()) * 0.5f));
    }
}
