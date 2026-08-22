package catchrelease.campaign.fish.map;

import catchrelease.ui.PaneWidgets;
import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FishMapPane extends BaseCustomUIPanelPlugin {

    public static final float WIDTH = 250f;
    public static final float PAD = 14f;

    public static final float PLANNER_HEIGHT = 22f;
    public static final float SEARCH_HEIGHT = 22f;

    public static final float CHIP_HEIGHT = 34f;
    public static final float CHIP_GAP = 4f;

    public static final float DESELECT_HEIGHT = 20f;
    public static final float HEADER_HEIGHT = 20f;
    public static final float CONTROLS_HEIGHT = 154f;
    public static final float ROW_HEIGHT = 24f;

    public static final String NO_DATA_TEXT = "No data for entry";
    public static final float NO_DATA_NOTE_HEIGHT = 20f;
    public static final float NO_DATA_RESET_WIDTH = 110f;
    public static final float NO_DATA_RESET_HEIGHT = 22f;
    public static final float NO_DATA_GAP = 8f;

    public static final float COHERENCE_HEIGHT = 22f;
    public static final float FOOTER_HEIGHT = COHERENCE_HEIGHT + 8f;
    public static final String SEARCH_GHOST = "Search...";
    public static final int MAX_SELECTED = 3;

    protected static boolean coherenceShown = false;
    protected final Host host;
    protected final FishPresence.Filter filter = new FishPresence.Filter();
    protected CustomPanelAPI panel;
    protected float width, height;

    protected PositionAPI pos;
    protected TextFieldAPI searchField;
    protected TooltipMakerAPI listElement;
    protected UIComponentAPI listRemovable;
    protected PositionAPI listViewport;

    protected final Set<String> selectedIds = new LinkedHashSet<>();
    protected int shownCount = 0;
    protected boolean resetRequested = false;

    public interface Host {

        void onPresenceChanged();

        void onSpeciesFocused(FishSpec spec);

        void onPlannerRequested();

        void onCoherenceToggled(boolean shown);
    }

    protected class RowPlugin extends FishListRow {

        public RowPlugin(FishSpec spec) {
            super(spec);
        }

        @Override
        protected PositionAPI getViewport() {
            return listViewport;
        }

        @Override
        protected boolean isSelected() {
            return selectedIds.contains(spec.id);
        }

        @Override
        protected void onRowClick(float pointX, float pointY) {
            onRowClicked(spec);
        }
    }

    public FishMapPane(Host host) {
        this.host = host;
    }

    public static boolean isCoherenceShown() {
        return coherenceShown;
    }

    public FishPresence.Filter getFilter() {
        return filter;
    }

    public Set<String> getSelectedIds() {
        return selectedIds;
    }

    public boolean hasSelectionWithoutRangeData() {
        for (String id : selectedIds) {
            FishSpec spec = FishPresence.getSpec(id);
            if (spec != null && !FishPresence.hasRangeData(spec)) return true;
        }

        return false;
    }

    public boolean isCategoryView() {
        return selectedIds.isEmpty();
    }

    public void showSpecies(String speciesId) {
        if (speciesId == null) return;

        resetRequested = false;

        boolean wasRestricted = filter.speciesRestricted;
        filter.speciesRestricted = false;
        filter.allowedSpeciesIds.clear();
        if (selectedIds.contains(speciesId)) {
            if (wasRestricted && panel != null) rebuildList();
            return;
        }

        // the codex asked, so room is made: the oldest pick retires rather than the request failing
        if (selectedIds.size() >= MAX_SELECTED) {
            selectedIds.remove(selectedIds.iterator().next());
        }

        selectedIds.add(speciesId);
        if (wasRestricted && panel != null) rebuildList();
    }

    public void showRequirements(List<FishRequirement> asks) {
        resetRequested = false;
        selectedIds.clear();
        filter.search = "";
        filter.types.clear();
        filter.allowedSpeciesIds.clear();
        filter.speciesRestricted = true;

        LinkedHashSet<String> exact = new LinkedHashSet<>();
        boolean exactOnly = asks != null && !asks.isEmpty();

        if (asks != null) {
            for (FishRequirement ask : asks) {
                if (ask == null || ask.speciesId == null || !ask.anyOf.isEmpty()) {
                    exactOnly = false;
                    continue;
                }
                exact.add(ask.speciesId);
            }
        }

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !FishPresence.hasRangeData(spec)) continue;
            if (!couldSupplyAny(asks, spec)) continue;

            filter.allowedSpeciesIds.add(spec.id);
            filter.types.add(FishType.of(spec));
        }

        if (exactOnly && exact.size() <= MAX_SELECTED) {
            for (String id : exact) {
                if (filter.allowedSpeciesIds.contains(id)) selectedIds.add(id);
            }
        }

        if (searchField != null) searchField.setText(SEARCH_GHOST);
        rebuildList();
    }

    public void showOverview() {
        resetRequested = false;
        selectedIds.clear();
        filter.search = "";
        filter.types.clear();
        filter.allowedSpeciesIds.clear();
        filter.speciesRestricted = false;
        for (FishType type : FishType.values()) filter.types.add(type);

        if (searchField != null) searchField.setText(SEARCH_GHOST);
        rebuildList();
    }

    protected boolean couldSupplyAny(List<FishRequirement> asks, FishSpec spec) {
        if (asks == null || asks.isEmpty()) return true;

        for (FishRequirement ask : asks) {
            if (ask != null && ask.couldBeSatisfiedBy(spec)) return true;
        }
        return false;
    }

    public void mount(CustomPanelAPI panel, float width, float height) {
        this.panel = panel;
        this.width = width;
        this.height = height;

        buildControls();
        buildFooter();
        rebuildList();
    }

    protected void buildFooter() {
        float innerWidth = width - PAD * 2f - 6f;
        TooltipMakerAPI footer = panel.createUIElement(innerWidth, COHERENCE_HEIGHT, false);

        CustomPanelAPI toggle = panel.createCustomPanel(innerWidth, COHERENCE_HEIGHT,
                new PaneWidgets.TextButton(
                        () -> coherenceShown ? "HIDE COHERENCE" : "SHOW COHERENCE",
                        () -> true,
                        () -> {
                            coherenceShown = !coherenceShown;
                            host.onCoherenceToggled(coherenceShown);
                        }));
        footer.addCustom(toggle, 0f);
        footer.addTooltipToPrevious(createSimpleTooltip(280f,
                "Paints how well the fabric is holding over the whole sector: clear where it"
                        + " holds, purple where it runs thin, hot where it is barely there."
                        + " Specimens taken where the fabric is thin come up aberrant."),
                TooltipMakerAPI.TooltipLocation.ABOVE);

        panel.addUIElement(footer).inTL(PAD, height - PAD - COHERENCE_HEIGHT);
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        ShopUi.drawPanel(x, y, w, h, 0.7f, alphaMult);
    }

    @Override
    public void advance(float amount) {
        if (searchField == null) return;

        if (resetRequested) {
            resetRequested = false;
            showOverview();
            host.onPresenceChanged();
        }

        String effective = PaneWidgets.tendGhost(searchField, SEARCH_GHOST);
        String current = filter.search == null ? "" : filter.search;

        if (!effective.equals(current)) {
            filter.search = effective;
            rebuildList();
            host.onPresenceChanged();
        }
    }

    protected void buildControls() {
        // the same right edge as the list's rows below, which sit 6px in for their scroller
        float innerWidth = width - PAD * 2f - 6f;
        TooltipMakerAPI controls = panel.createUIElement(innerWidth, CONTROLS_HEIGHT, false);

        CustomPanelAPI planner = panel.createCustomPanel(innerWidth, PLANNER_HEIGHT,
                new PaneWidgets.TextButton(() -> "PLAN A ROUTE", () -> true,
                        host::onPlannerRequested));
        controls.addCustom(planner, 0f);
        controls.addTooltipToPrevious(createSimpleTooltip(260f,
                "Pick the fish you need - open jobs and upgrade asks are suggested - and plot"
                        + " the shortest route through their ranges."),
                TooltipMakerAPI.TooltipLocation.BELOW);

        searchField = controls.addTextField(innerWidth, SEARCH_HEIGHT, ShopUi.FONT_SMALL, 8f);
        searchField.setText(filter.search == null || filter.search.isEmpty()
                ? SEARCH_GHOST : filter.search);
        controls.addTooltipToPrevious(createSimpleTooltip(260f,
                "Type to filter the species by name. The list and the shading follow as you type."),
                TooltipMakerAPI.TooltipLocation.BELOW);

        FishType[] types = FishType.values();

        float chipWidth = (float) Math.floor(
                (innerWidth - CHIP_GAP * (types.length - 1)) / types.length);

        CustomPanelAPI chipRow = panel.createCustomPanel(innerWidth, CHIP_HEIGHT,
                new BaseCustomUIPanelPlugin() {
                });

        for (int i = 0; i < types.length; i++) {
            FishType type = types[i];
            CustomPanelAPI chip = panel.createCustomPanel(chipWidth, CHIP_HEIGHT,
                    new PaneWidgets.Chip(type.label, type.color, type.iconId,
                            () -> filter.types.contains(type), () -> onChipToggled(type)));

            chipRow.addComponent(chip).inTL(i * (chipWidth + CHIP_GAP), 0f);
            controls.addTooltipTo(createChipTooltip(type), chip, TooltipMakerAPI.TooltipLocation.BELOW);
        }

        controls.addCustom(chipRow, 8f);

        CustomPanelAPI deselect = panel.createCustomPanel(innerWidth, DESELECT_HEIGHT,
                new PaneWidgets.TextButton(() -> "DESELECT ALL",
                        () -> !selectedIds.isEmpty() || !filter.types.isEmpty()
                                || filter.speciesRestricted,
                        this::onDeselectAll));
        controls.addCustom(deselect, 8f);
        controls.addTooltipTo(createSimpleTooltip(260f,
                "Clear the picked species and switch off every category filter."),
                deselect, TooltipMakerAPI.TooltipLocation.BELOW);

        CustomPanelAPI header = panel.createCustomPanel(innerWidth, HEADER_HEIGHT,
                new PaneWidgets.ListHeader(() -> shownCount == 0
                        ? "SPECIES - NONE MATCH" : "SPECIES - " + shownCount));
        controls.addCustom(header, 8f);
        controls.addTooltipTo(createLegendTooltip(), header, TooltipMakerAPI.TooltipLocation.BELOW);

        panel.addUIElement(controls).inTL(PAD, PAD);
    }

    protected void rebuildList() {
        if (listRemovable != null) panel.removeComponent(listRemovable);

        List<FishSpec> shown = FishPresence.getShown(filter);
        shownCount = shown.size();

        float listHeight = height - CONTROLS_HEIGHT - FOOTER_HEIGHT - PAD * 2f;
        float listWidth = width - PAD * 2f;
        boolean noDataForEntry = filter.speciesRestricted
                && filter.allowedSpeciesIds.isEmpty();

        // same air on both sides - the list's slot is inset PAD left and right alike
        listElement = panel.createUIElement(listWidth, listHeight, !noDataForEntry);

        if (noDataForEntry) {
            CustomPanelAPI emptyState = panel.createCustomPanel(listWidth, listHeight,
                    new BaseCustomUIPanelPlugin() {
                    });

            float blockHeight = NO_DATA_NOTE_HEIGHT + NO_DATA_GAP + NO_DATA_RESET_HEIGHT;
            float blockTop = Math.max(0f, (listHeight - blockHeight) * 0.5f);

            CustomPanelAPI note = panel.createCustomPanel(listWidth, NO_DATA_NOTE_HEIGHT,
                    new PaneWidgets.Note(NO_DATA_TEXT));
            emptyState.addComponent(note).inTL(0f, blockTop);

            CustomPanelAPI reset = panel.createCustomPanel(
                    NO_DATA_RESET_WIDTH, NO_DATA_RESET_HEIGHT,
                    new PaneWidgets.TextButton(() -> "RESET", () -> true,
                            () -> resetRequested = true));
            emptyState.addComponent(reset).inTL(
                    (listWidth - NO_DATA_RESET_WIDTH) * 0.5f,
                    blockTop + NO_DATA_NOTE_HEIGHT + NO_DATA_GAP);

            listElement.addCustom(emptyState, 0f);
        }

        for (FishSpec spec : shown) {
            CustomPanelAPI row = panel.createCustomPanel(listWidth - 6f, ROW_HEIGHT,
                    new RowPlugin(spec));

            listElement.addCustom(row, 3f);
            listElement.addTooltipTo(createRowTooltip(spec), row, TooltipMakerAPI.TooltipLocation.LEFT);
        }

        listViewport = panel.addUIElement(listElement);
        listViewport.inTL(PAD, PAD + CONTROLS_HEIGHT);

        listRemovable = listElement.getExternalScroller() != null
                ? (UIComponentAPI) listElement.getExternalScroller() : listElement;
    }

    protected void onChipToggled(FishType type) {
        if (!filter.types.remove(type)) filter.types.add(type);

        rebuildList();
        host.onPresenceChanged();
    }

    protected void onRowClicked(FishSpec spec) {
        if (selectedIds.contains(spec.id)) {
            selectedIds.remove(spec.id);
            host.onPresenceChanged();
            return;
        }

        // three weaves, three picks - a fourth is refused rather than repainted over the others
        if (selectedIds.size() >= MAX_SELECTED) return;

        selectedIds.add(spec.id);
        host.onSpeciesFocused(spec);
        host.onPresenceChanged();
    }

    protected void onDeselectAll() {
        boolean wasRestricted = filter.speciesRestricted;
        if (selectedIds.isEmpty() && filter.types.isEmpty() && !wasRestricted) return;

        selectedIds.clear();
        filter.types.clear();

        if (wasRestricted) {
            filter.search = "";
            filter.allowedSpeciesIds.clear();
            filter.speciesRestricted = false;
            if (searchField != null) searchField.setText(SEARCH_GHOST);
        }

        rebuildList();
        host.onPresenceChanged();
    }

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
                        ? "Click to hide this type's species and shading."
                        : "Click to show this type's species and shading.", Misc.getGrayColor(), 6f);
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
                tooltip.addPara("Reading the map", Misc.getBasePlayerColor(), 0f);

                tooltip.addPara("Enable type chips to shade whole territories. Pick species off"
                        + " the list to shade only those - up to three at once for planning a"
                        + " route, the first filled, the second striped one way, the third the"
                        + " other, so overlaps cross instead of piling.", 8f);

                tooltip.addPara("A filled circle by a name is a species somebody aboard has"
                        + " landed. A hollow one is known only from range data: its range"
                        + " shade, but nobody has seen the creature itself.", 8f);

                tooltip.addPara("F2 over a row opens that species' codex page.", Misc.getGrayColor(), 8f);

                if (Global.getSettings().isDevMode()) {
                    tooltip.addPara("Dev mode: everything in the table is shown, caught or not.",
                            Misc.getHighlightColor(), 8f);
                }
            }
        };
    }

    protected TooltipMakerAPI.TooltipCreator createRowTooltip(FishSpec spec) {
        return FishTooltips.create(spec, () ->
                !selectedIds.contains(spec.id) && selectedIds.size() >= MAX_SELECTED
                        ? "Three ranges are already up - deselect one first."
                        : "Click to toggle its range on the map. F2 opens the codex.");
    }
}
