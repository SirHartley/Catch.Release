package catchrelease.campaign.fish.map;

import catchrelease.ui.PaneWidgets;
import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.ui.ShopUi;
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

        ShopUi.drawPanel(x, y, w, h, 0.7f, alphaMult);
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
                new PaneWidgets.TitleRow("FISHING PLANNER"));
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
                    new PaneWidgets.Chip(type.label, type.color,
                            () -> filter.types.contains(type), () -> onChipToggled(type)));

            chipRow.addComponent(chip).inTL(i * (chipWidth + CHIP_GAP), 0f);
            controls.addTooltipTo(createChipTooltip(type), chip, TooltipMakerAPI.TooltipLocation.BELOW);
        }

        controls.addCustom(chipRow, 8f);

        CustomPanelAPI header = panel.createCustomPanel(innerWidth, HEADER_HEIGHT,
                new PaneWidgets.ListHeader(() -> rows.isEmpty()
                        ? "SPECIES - NONE MATCH" : "SPECIES - " + rows.size()));
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

    /** One species row in the shared dress; the field stays lit while picked. The dot is the
     *  whole of the who-wants-it story here - the job/errand tag that used to sit beside it is
     *  the tooltip's to tell (its Required-by section already names the askers). */
    protected class RowPlugin extends FishListRow {

        public RowPlugin(Row row) {
            super(row.spec);
        }

        @Override
        protected PositionAPI getViewport() {
            return listViewport;
        }

        @Override
        protected boolean isSelected() {
            return selected.contains(spec.id);
        }

        @Override
        protected void onRowClick(float pointX, float pointY) {
            onRowClicked(spec);
        }
    }
}
