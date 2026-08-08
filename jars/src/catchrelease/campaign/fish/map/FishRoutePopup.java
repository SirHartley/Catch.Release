package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fishing planner in the sidebar's slot, asking one question - which fish do you need? -
 * and turning the answer into a plotted route.
 * <p>
 * Built the way the sidebar itself is built, from the same parts: a title row with the way out
 * in its corner, the search field, {@link PaneWidgets.Chip} per type, a counted header wearing
 * the explanation as a hover on its {@code ?}, and the species as component rows in a scrolling
 * list - wanted ones (open jobs and the shop's next rungs) pinned first. Up to
 * {@link FishRoutePlanner#MAX_PICKS} picks; the PLOT ROUTE button along the bottom hands them
 * to the planner, with a notice line above it for the refusals that need a reason.
 * <p>
 * Also the panel's own plugin - the host mounts it via {@link #mount}. Controls are built once;
 * only the row list rebuilds on a filter change, so the search field keeps the keyboard.
 */
public class FishRoutePopup extends BaseCustomUIPanelPlugin {

    /** What the popup needs from whoever put it in the sidebar's slot. */
    public interface Host {
        void onRoutePlotted(FishRoute.Saved route);

        void onPlannerClosed();
    }

    public static final float PAD = 14f;
    public static final float TITLE_HEIGHT = 20f;
    public static final float CLOSE_WIDTH = 20f;
    public static final float SEARCH_HEIGHT = 22f;
    public static final float CHIP_HEIGHT = 34f;
    public static final float CHIP_GAP = 4f;
    public static final float HEADER_HEIGHT = 20f;
    public static final float CONTROLS_HEIGHT = 124f;
    public static final float ROW_HEIGHT = 24f;

    /** The floor of the card: the answer-back line and the one button under it. */
    public static final float NOTICE_HEIGHT = 18f;
    public static final float BUTTON_HEIGHT = 26f;
    public static final float FOOTER_HEIGHT = NOTICE_HEIGHT + 4f + BUTTON_HEIGHT;

    public static final String SEARCH_GHOST = "Search...";

    protected final Host host;

    /** Who is asking for a species, for the tag on its row - the pin order is the ask order. */
    protected final Map<String, String> reasons = new LinkedHashMap<>();

    /** What the current filter lets through, wanted species first. */
    protected final List<Row> rows = new ArrayList<>();
    protected final Set<String> selected = new LinkedHashSet<>();

    protected final FishPresence.Filter filter = new FishPresence.Filter();

    protected CustomPanelAPI panel;
    protected float width, height;
    protected PositionAPI pos;

    protected TextFieldAPI searchField;
    protected TooltipMakerAPI listElement;
    protected UIComponentAPI listRemovable;
    protected PositionAPI listViewport;

    /** The card talking back: why a click did nothing, or why a plot refused. */
    protected String notice;
    protected Color noticeColor;

    protected static class Row {
        FishSpec spec;
        String reason;
    }

    public FishRoutePopup(Host host) {
        this.host = host;

        for (FishRoutePlanner.Suggestion suggestion : FishRoutePlanner.getSuggestions()) {
            reasons.putIfAbsent(suggestion.speciesId, suggestion.reason);
        }
    }

    /** Builds the controls and the first list into the popup's own panel. Call once. */
    public void mount(CustomPanelAPI panel, float width, float height) {
        this.panel = panel;
        this.width = width;
        this.height = height;

        buildControls();
        buildFooter();
        rebuildList();
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    /** The sidebar's own dressing, verbatim - this card stands in the sidebar's slot. */
    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        ShopUi.drawQuad(x, y, w, h, Color.BLACK, 0.7f * alphaMult);

        Color border = Misc.getBasePlayerColor();
        ShopUi.drawQuad(x, y, w, 1f, border, 0.55f * alphaMult);
        ShopUi.drawQuad(x, y + h - 1f, w, 1f, border, 0.55f * alphaMult);
        ShopUi.drawQuad(x, y, 1f, h, border, 0.55f * alphaMult);
        ShopUi.drawQuad(x + w - 1f, y, 1f, h, border, 0.55f * alphaMult);
    }

    /** Same hand-worked ghost text as the sidebar's field - there is no change callback. */
    @Override
    public void advance(float amount) {
        if (searchField == null) return;

        String effective = PaneWidgets.tendGhost(searchField, SEARCH_GHOST);
        String current = filter.search == null ? "" : filter.search;

        if (!effective.equals(current)) {
            filter.search = effective;
            rebuildList();
        }
    }

    /** The part that never rebuilds: the title row, search, the type chips, and the header. */
    protected void buildControls() {
        //the same right edge as the list's rows below, which sit 6px in for their scroller
        float innerWidth = width - PAD * 2f - 6f;
        TooltipMakerAPI controls = panel.createUIElement(innerWidth, CONTROLS_HEIGHT, false);

        //the title carries the way out in its right corner
        CustomPanelAPI titleRow = panel.createCustomPanel(innerWidth, TITLE_HEIGHT,
                new TitlePlugin());
        CustomPanelAPI close = panel.createCustomPanel(CLOSE_WIDTH, TITLE_HEIGHT,
                new PaneWidgets.TextButton(() -> "X", () -> true, host::onPlannerClosed));
        titleRow.addComponent(close).inTR(0f, 0f);

        controls.addCustom(titleRow, 0f);
        controls.addTooltipTo(createSimpleTooltip(220f,
                "Close the planner and put the sidebar back."),
                close, TooltipMakerAPI.TooltipLocation.BELOW);

        searchField = controls.addTextField(innerWidth, SEARCH_HEIGHT, ShopUi.FONT_SMALL, 8f);
        searchField.setText(SEARCH_GHOST);
        controls.addTooltipToPrevious(createSimpleTooltip(260f,
                "Type to filter the species by name. The list follows as you type."),
                TooltipMakerAPI.TooltipLocation.BELOW);

        FishType[] types = FishType.values();

        //floored to the pixel: a chip on a fractional edge is a chip with a soft edge
        float chipWidth = (float) Math.floor(
                (innerWidth - CHIP_GAP * (types.length - 1)) / types.length);

        CustomPanelAPI chipRow = panel.createCustomPanel(innerWidth, CHIP_HEIGHT,
                new BaseCustomUIPanelPlugin() {
                });

        for (int i = 0; i < types.length; i++) {
            FishType type = types[i];
            CustomPanelAPI chip = panel.createCustomPanel(chipWidth, CHIP_HEIGHT,
                    new PaneWidgets.Chip(type, filter, this::onChipToggled));

            chipRow.addComponent(chip).inTL(i * (chipWidth + CHIP_GAP), 0f);
            controls.addTooltipTo(createChipTooltip(type), chip, TooltipMakerAPI.TooltipLocation.BELOW);
        }

        controls.addCustom(chipRow, 8f);

        CustomPanelAPI header = panel.createCustomPanel(innerWidth, HEADER_HEIGHT, new ListHeaderPlugin());
        controls.addCustom(header, 8f);
        controls.addTooltipTo(createLegendTooltip(), header, TooltipMakerAPI.TooltipLocation.BELOW);

        panel.addUIElement(controls).inTL(PAD, PAD);
    }

    /** The floor, built once and anchored from the bottom: the notice line, then PLOT ROUTE. */
    protected void buildFooter() {
        float innerWidth = width - PAD * 2f - 6f;
        TooltipMakerAPI footer = panel.createUIElement(innerWidth, FOOTER_HEIGHT, false);

        CustomPanelAPI noticeLine = panel.createCustomPanel(innerWidth, NOTICE_HEIGHT,
                new NoticePlugin());
        footer.addCustom(noticeLine, 0f);

        CustomPanelAPI plot = panel.createCustomPanel(innerWidth, BUTTON_HEIGHT,
                new PaneWidgets.TextButton(() -> "PLOT ROUTE (" + selected.size() + ")",
                        () -> !selected.isEmpty(), this::plot));
        footer.addCustom(plot, 4f);
        footer.addTooltipToPrevious(createSimpleTooltip(260f,
                "Plot the shortest route through the picked species' ranges and draw it on"
                        + " the hyperspace map."),
                TooltipMakerAPI.TooltipLocation.ABOVE);

        panel.addUIElement(footer).inTL(PAD, height - PAD - FOOTER_HEIGHT);
    }

    /** Fresh rows for the current filter: pinned asks first, then the rest of what is known.
     *  The controls stay put, and so does the keyboard. */
    protected void rebuildList() {
        if (listRemovable != null) panel.removeComponent(listRemovable);

        rebuildRows();

        float listHeight = height - CONTROLS_HEIGHT - FOOTER_HEIGHT - PAD * 2f - 8f;
        //same air on both sides - the list's slot is inset PAD left and right alike
        listElement = panel.createUIElement(width - PAD * 2f, listHeight, true);

        for (Row row : rows) {
            CustomPanelAPI rowPanel = panel.createCustomPanel(width - PAD * 2f - 6f, ROW_HEIGHT,
                    new RowPlugin(row));

            listElement.addCustom(rowPanel, 3f);
            listElement.addTooltipTo(createRowTooltip(row.spec), rowPanel,
                    TooltipMakerAPI.TooltipLocation.LEFT);
        }

        listViewport = panel.addUIElement(listElement);
        listViewport.inTL(PAD, PAD + CONTROLS_HEIGHT);

        //a scrollable element comes back wrapped in a scroller - that's what's removed/stored
        listRemovable = listElement.getExternalScroller() != null
                ? (UIComponentAPI) listElement.getExternalScroller() : listElement;
    }

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
    }

    protected void onChipToggled(FishType type) {
        if (!filter.types.remove(type)) filter.types.add(type);

        rebuildList();
    }

    /** A click toggles the pick; the cap refuses with a reason rather than silently. */
    protected void onRowClicked(FishSpec spec) {
        if (selected.remove(spec.id)) {
            notice = null;
            return;
        }

        if (selected.size() >= FishRoutePlanner.MAX_PICKS) {
            say("All " + FishRoutePlanner.MAX_PICKS + " picks used.", Misc.getHighlightColor());
            return;
        }

        selected.add(spec.id);
        notice = null;
    }

    protected void say(String what, Color color) {
        notice = what;
        noticeColor = color;
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

            say("No charted range for " + listed + ".", Misc.getNegativeHighlightColor());
            return;
        }

        FishRoute.Saved route = FishRoutePlanner.plan(new ArrayList<>(selected));
        if (route == null) {
            say("No route - nothing picked has a charted range.", Misc.getNegativeHighlightColor());
            return;
        }

        FishRoute.set(route);
        host.onRoutePlotted(route);
    }

    // --- Tooltips, which is where all the explaining lives. ---

    protected TooltipMakerAPI.TooltipCreator createSimpleTooltip(float tooltipWidth, String text) {
        return new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return tooltipWidth;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(text, 0f);
            }
        };
    }

    protected TooltipMakerAPI.TooltipCreator createChipTooltip(FishType type) {
        return new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 240f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(type.label, type.color, 0f);
                tooltip.addPara(filter.types.contains(type)
                        ? "Click to hide this type's species."
                        : "Click to show this type's species.", Misc.getGrayColor(), 6f);
            }
        };
    }

    protected TooltipMakerAPI.TooltipCreator createLegendTooltip() {
        return new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 320f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Planning a route", Misc.getBasePlayerColor(), 0f);

                tooltip.addPara("Pick up to " + FishRoutePlanner.MAX_PICKS + " fish - wanted"
                        + " ones (open jobs, marked gear) are pinned first, tagged with who is"
                        + " asking.", 8f);

                tooltip.addPara("The search field and the type chips narrow the list.", 8f);

                tooltip.addPara("PLOT ROUTE plots the shortest route through the picked"
                        + " species' ranges and draws it on the hyperspace map.", 8f);

                tooltip.addPara("F2 over a row opens that species' codex page.",
                        Misc.getGrayColor(), 8f);
            }
        };
    }

    protected TooltipMakerAPI.TooltipCreator createRowTooltip(FishSpec spec) {
        //the shared species card, with this card's own action line read live at hover time
        return FishTooltips.create(spec, () -> {
            if (selected.contains(spec.id)) return "Click to drop it from the route plan.";
            if (selected.size() >= FishRoutePlanner.MAX_PICKS) {
                return "All " + FishRoutePlanner.MAX_PICKS + " picks are used - drop one first.";
            }
            return "Click to pick it for the route. F2 opens the codex.";
        });
    }

    // --- The drawn controls. Chips and buttons are PaneWidgets', shared with the sidebar. ---

    /** The card's name, in the header hand the sidebar's sections write in. */
    protected class TitlePlugin extends BaseCustomUIPanelPlugin {

        protected PositionAPI titlePos;

        protected transient LazyFont.DrawableString text;

        @Override
        public void positionChanged(PositionAPI position) {
            titlePos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (titlePos == null || alphaMult <= 0f) return;

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            float x = titlePos.getX();
            float y = titlePos.getY();
            float h = titlePos.getHeight();

            if (text == null) {
                text = ShopUi.createText(small, "FISHING PLANNER");
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            text.setBaseColor(ShopUi.withAlpha(Misc.getBasePlayerColor(), alphaMult));
            text.draw(Math.round(x), Math.round(y + h * 0.5f + text.getHeight() * 0.5f));

            ShopUi.drawQuad(x, y, titlePos.getWidth(), 1f, Misc.getDarkPlayerColor(),
                    0.8f * alphaMult);
        }
    }

    /** Line over the list: what it is, how many match, and the help mark - drawn live so the
     *  count is never stale. */
    protected class ListHeaderPlugin extends BaseCustomUIPanelPlugin {

        protected PositionAPI headerPos;

        protected transient LazyFont.DrawableString text;
        protected transient String written;
        protected transient LazyFont.DrawableString help;

        @Override
        public void positionChanged(PositionAPI position) {
            headerPos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (headerPos == null || alphaMult <= 0f) return;

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            float x = headerPos.getX();
            float y = headerPos.getY();
            float w = headerPos.getWidth();
            float h = headerPos.getHeight();

            String wanted = rows.isEmpty() ? "SPECIES - NONE MATCH" : "SPECIES - " + rows.size();

            if (text == null || !wanted.equals(written)) {
                written = wanted;
                text = ShopUi.createText(small, wanted);
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            text.setBaseColor(ShopUi.withAlpha(Misc.getBasePlayerColor(), alphaMult));
            text.draw(Math.round(x), Math.round(y + h * 0.5f + text.getHeight() * 0.5f));

            if (help == null) {
                help = ShopUi.createText(small, "?");
                help.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
            }

            help.setBaseColor(ShopUi.withAlpha(Misc.getGrayColor(), alphaMult));
            help.draw(Math.round(x + w - 2f), Math.round(y + h * 0.5f + help.getHeight() * 0.5f));

            ShopUi.drawQuad(x, y, w, 1f, Misc.getDarkPlayerColor(), 0.8f * alphaMult);
        }
    }

    /** The answer-back line above the button, gone the moment there is nothing to answer. */
    protected class NoticePlugin extends BaseCustomUIPanelPlugin {

        protected PositionAPI noticePos;

        @Override
        public void positionChanged(PositionAPI position) {
            noticePos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (noticePos == null || alphaMult <= 0f || notice == null) return;

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            //grows upward from its floor - a wrapped second line eats into the list rather
            //than lying across the button it explains
            LazyFont.DrawableString line = small.createText(notice,
                    ShopUi.withAlpha(noticeColor == null ? Misc.getGrayColor() : noticeColor,
                            alphaMult), small.getBaseHeight(), noticePos.getWidth());

            line.draw(Math.round(noticePos.getX()),
                    Math.round(noticePos.getY() + line.getHeight()));
        }
    }

    /** One species row in the sidebar's own dress - rarity accent, caught-mark circle, name,
     *  the shopping-list dot - plus a tag for who is asking. Field stays lit while picked;
     *  F2 opens the codex. */
    protected class RowPlugin extends BaseCustomUIPanelPlugin {

        public static final float PAD_SIDE = 8f;
        public static final float ACCENT_WIDTH = 3f;
        public static final float MARK_RADIUS = 3.5f;
        public static final float MARK_GAP = 7f;

        protected final Row row;
        protected PositionAPI rowPos;

        protected transient LazyFont.DrawableString name;
        protected transient LazyFont.DrawableString reason;

        public RowPlugin(Row row) {
            this.row = row;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            rowPos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (rowPos == null || alphaMult <= 0f || listViewport == null) return;

            float x = rowPos.getX();
            float y = rowPos.getY();
            float w = rowPos.getWidth();
            float h = rowPos.getHeight();

            if (y + h < listViewport.getY() || y > listViewport.getY() + listViewport.getHeight()) return;

            ShopUi.startClip(listViewport.getX(), listViewport.getY(),
                    listViewport.getWidth(), listViewport.getHeight());

            FishSpec spec = row.spec;

            boolean picked = selected.contains(spec.id);
            boolean hovered = !picked && contains(Global.getSettings().getMouseX(),
                    Global.getSettings().getMouseY());

            float field = picked ? 0.4f : hovered ? 0.3f : 0.12f;
            ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(), field * alphaMult);

            float accent = picked ? 0.9f : hovered ? 0.6f : 0.3f;
            ShopUi.drawQuad(x, y, ACCENT_WIDTH, h, spec.rarity.color, accent * alphaMult);

            Color chrome = picked || hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

            //filled = caught, hollow = survey-only; a shape rather than a shade, since every
            //shade here already means selection or rarity
            boolean caught = FishLog.isCaught(spec.id);
            float markX = x + ACCENT_WIDTH + PAD_SIDE + MARK_RADIUS;
            float markY = y + h * 0.5f;

            if (caught) {
                Disc.draw(markX, markY, MARK_RADIUS, chrome, 0.9f * alphaMult, 0.9f * alphaMult, false);
            }

            //drawn over the fill too - the outline is what keeps a circle this small round
            Disc.drawOutline(markX, markY, MARK_RADIUS, chrome, 0.9f * alphaMult, 1.5f);

            LazyFont body = ShopUi.getBodyFont();
            if (body != null) {
                if (name == null) {
                    name = ShopUi.createText(body, spec.getDisplayName());
                    name.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
                }

                name.setBaseColor(ShopUi.withAlpha(chrome, alphaMult));
                name.draw(Math.round(x + ACCENT_WIDTH + PAD_SIDE + MARK_RADIUS * 2f + MARK_GAP),
                        Math.round(y + h * 0.5f + name.getHeight() * 0.5f));
            }

            boolean marked = ShopMarks.isWanted(spec);

            //who is asking, right-aligned - the marked tag retired, the dot already says it
            if (row.reason != null && !"marked".equals(row.reason)) {
                LazyFont small = ShopUi.getSmallFont();
                if (small != null) {
                    if (reason == null) {
                        reason = ShopUi.createText(small, row.reason);
                        reason.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
                    }

                    reason.setBaseColor(ShopUi.withAlpha(Misc.getHighlightColor(), alphaMult));
                    reason.draw(Math.round(x + w - PAD_SIDE - reason.getWidth()
                                    - (marked ? 12f : 0f)),
                            Math.round(y + h * 0.5f + reason.getHeight() * 0.5f));
                }
            }

            //the wanted dot at the row's right end, centred on the row's own midline
            if (marked) {
                ShopMarks.drawDot(x + w - 8f, y + h * 0.5f,
                        ShopMarks.DOT_RADIUS - 0.5f, alphaMult);
            }

            ShopUi.endClip();
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (rowPos == null) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;

                //the codex hotlink, the way the rest of the game's UI wears it
                if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_F2) {
                    if (!contains(Global.getSettings().getMouseX(), Global.getSettings().getMouseY())) {
                        continue;
                    }

                    event.consume();
                    FishCodex.show(row.spec.id);
                    return;
                }

                if (!event.isLMBDownEvent()) continue;
                if (!contains(event.getX(), event.getY())) continue;

                event.consume();
                Global.getSoundPlayer().playUISound(PaneWidgets.CLICK_SOUND, 1f, 1f);
                onRowClicked(row.spec);

                return;
            }
        }

        protected boolean contains(float pointX, float pointY) {
            if (listViewport != null && !ShopUi.contains(listViewport.getX(), listViewport.getY(),
                    listViewport.getWidth(), listViewport.getHeight(), pointX, pointY)) {
                return false;
            }

            return ShopUi.contains(rowPos.getX(), rowPos.getY(), rowPos.getWidth(),
                    rowPos.getHeight(), pointX, pointY);
        }
    }
}
