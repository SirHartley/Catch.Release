package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The whole catch map as one mountable piece: the {@link FishMapPanel} viewport on the left and
 * the search pane on the right, wherever a host panel will have them - the ability's dialog today,
 * a tab on the campaign UI tomorrow. The host owes it a panel, a rectangle, and an
 * {@link #advance(float)} call; everything else lives here.
 * <p>
 * The pane keeps itself simple on purpose: a search field, one chip per rarity, and the species
 * as rows. Everything that would have been a paragraph of instructions is a tooltip instead, so
 * the pane holds only things that do something. The controls are built once and never rebuilt -
 * only the row list is torn down on a filter change, which is what lets the search field keep the
 * keyboard and the list follow every keystroke, where rebuilding the field meant waiting for the
 * typing to stop.
 */
public class FishMapView implements FishMapRowPlugin.Host {

    public static final float SIDEBAR_WIDTH = 250f;
    public static final float GAP = 12f;

    public static final float SEARCH_HEIGHT = 22f;
    public static final float CHIP_HEIGHT = 20f;
    public static final float CHIP_GAP = 4f;
    public static final float HEADER_HEIGHT = 20f;
    public static final float CONTROLS_HEIGHT = 92f;
    public static final float ROW_HEIGHT = 24f;

    protected CustomPanelAPI host;
    protected float x, y, width, height;

    protected final Filter filter = new Filter();
    protected final FishMapPanel map = new FishMapPanel();

    protected TextFieldAPI searchField;

    protected TooltipMakerAPI listElement;
    protected UIComponentAPI listRemovable;
    protected PositionAPI listViewport;

    protected String selectedId = null;
    protected int shownCount = 0;

    /**
     * Builds the map and the pane into the host. The view survives its hosts: mounting again into
     * a fresh panel - the campaign UI rebuilds its screens on every open - keeps the camera, the
     * filter and the selection, and only the furniture is made new.
     */
    public void mount(CustomPanelAPI host, float x, float y, float width, float height) {
        this.host = host;
        this.listRemovable = null;
        this.listViewport = null;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        float mapWidth = width - SIDEBAR_WIDTH - GAP;
        host.addComponent(host.createCustomPanel(mapWidth, height, map)).inTL(x, y);

        buildControls();
        rebuildList();
    }

    /**
     * The part of the pane that never rebuilds: the search field, the rarity chips, and the list
     * header. The chips and the header draw their state fresh every frame, so nothing here ever
     * needs finding and rewriting - the shop's argument, applied.
     */
    protected void buildControls() {
        float innerWidth = SIDEBAR_WIDTH - 10f;
        TooltipMakerAPI controls = host.createUIElement(SIDEBAR_WIDTH, CONTROLS_HEIGHT, false);

        searchField = controls.addTextField(innerWidth, SEARCH_HEIGHT, ShopUi.FONT_SMALL, 4f);
        searchField.setText(filter.search == null ? "" : filter.search);
        controls.addTooltipToPrevious(createSimpleTooltip(260f,
                "Type to filter the species by name. The list and the map's lights follow as you type."),
                TooltipMakerAPI.TooltipLocation.BELOW);

        FishRarity[] rarities = FishRarity.values();

        //floored to the pixel: a chip on a fractional edge is a chip with a soft edge
        float chipWidth = (float) Math.floor(
                (innerWidth - CHIP_GAP * (rarities.length - 1)) / rarities.length);

        CustomPanelAPI chipRow = host.createCustomPanel(innerWidth, CHIP_HEIGHT,
                new BaseCustomUIPanelPlugin() {
                });

        for (int i = 0; i < rarities.length; i++) {
            FishRarity rarity = rarities[i];
            CustomPanelAPI chip = host.createCustomPanel(chipWidth, CHIP_HEIGHT, new ChipPlugin(rarity));

            chipRow.addComponent(chip).inTL(i * (chipWidth + CHIP_GAP), 0f);
            controls.addTooltipTo(createChipTooltip(rarity), chip, TooltipMakerAPI.TooltipLocation.BELOW);
        }

        controls.addCustom(chipRow, 8f);

        CustomPanelAPI header = host.createCustomPanel(innerWidth, HEADER_HEIGHT, new ListHeaderPlugin());
        controls.addCustom(header, 8f);
        controls.addTooltipTo(createLegendTooltip(), header, TooltipMakerAPI.TooltipLocation.BELOW);

        host.addUIElement(controls).inTL(x + width - SIDEBAR_WIDTH, y);
    }

    /** Fresh marks for the map and fresh rows for the pane. The controls and the camera stay put. */
    protected void rebuildList() {
        if (listRemovable != null) host.removeComponent(listRemovable);

        List<FishSpec> shown = getShown();
        shownCount = shown.size();

        map.setData(buildSystemMarks(shown), buildCatchMarks(shown));
        map.setAreas(buildAreaMarks(shown));

        float listHeight = height - CONTROLS_HEIGHT;
        listElement = host.createUIElement(SIDEBAR_WIDTH, listHeight, true);

        for (FishSpec spec : shown) {
            CustomPanelAPI row = host.createCustomPanel(SIDEBAR_WIDTH - 14f, ROW_HEIGHT,
                    new FishMapRowPlugin(spec, getStatus(spec), this));

            listElement.addCustom(row, 3f);
            listElement.addTooltipTo(createRowTooltip(spec), row, TooltipMakerAPI.TooltipLocation.LEFT);
        }

        listViewport = host.addUIElement(listElement);
        listViewport.inTL(x + width - SIDEBAR_WIDTH, y + CONTROLS_HEIGHT);

        //a scrollable element goes in wrapped in a scroller, and the wrapper is what comes out
        listRemovable = listElement.getExternalScroller() != null
                ? (UIComponentAPI) listElement.getExternalScroller() : listElement;
    }

    /**
     * A text field has no change callback, so it is polled. Since only the row list rebuilds -
     * never the field - the list can chase the text a keystroke at a time.
     */
    public void advance(float amount) {
        if (searchField == null) return;

        String text = searchField.getText();
        String current = filter.search == null ? "" : filter.search;

        if (text != null && !text.equals(current)) {
            filter.search = text;
            rebuildList();
        }
    }

    protected void onChipToggled(FishRarity rarity) {
        if (!filter.rarities.remove(rarity)) filter.rarities.add(rarity);

        rebuildList();
    }

    /** A row click points the map: the pin if there is one, else the first system it is said to live in. */
    @Override
    public void onRowClicked(FishSpec spec) {
        selectedId = spec.id;
        map.setAreas(buildAreaMarks(getShown()));

        FishLogEntry logged = FishLog.get(spec.id);

        if (logged != null && logged.recordLocationInHyper != null) {
            map.focus(logged.recordLocationInHyper);
            return;
        }

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            SectorRegion at = SectorRegion.of(system);
            if (at != null && spec.regions.contains(at)) {
                map.focus(system.getLocation());
                return;
            }
        }
    }

    @Override
    public boolean isSelectedRow(FishSpec spec) {
        return spec.id != null && spec.id.equals(selectedId);
    }

    @Override
    public PositionAPI getListViewport() {
        return listViewport;
    }

    // --- What is shown. ---

    /** What passes the filters, in table order so the list does not reshuffle as things are caught. */
    protected List<FishSpec> getShown() {
        List<FishSpec> shown = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (!filter.accepts(spec)) continue;
            if (!isKnown(spec)) continue;

            shown.add(spec);
        }

        return shown;
    }

    /** Dev mode knows everything. Otherwise it has to have been caught or paid for. */
    protected boolean isKnown(FishSpec spec) {
        if (Global.getSettings().isDevMode()) return true;

        return FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id);
    }

    protected boolean showsRegions(FishSpec spec, boolean caught) {
        if (Global.getSettings().isDevMode()) return !spec.regions.isEmpty();

        return !caught && FishLog.isLocationDataUnlocked(spec.id);
    }

    protected String getStatus(FishSpec spec) {
        if (FishLog.isCaught(spec.id)) return "landed";

        //a table row with nowhere to be is a data problem, and dev mode is where it gets caught
        if (Global.getSettings().isDevMode() && spec.regions.isEmpty()) return "no data";

        return "region data";
    }

    // --- Marks for the map. ---

    /**
     * Every system as a quiet mark, and the ones a shown species is said to live in lit in the
     * rarest claimant's colour. Which systems a region means is asked of the region resolver
     * itself, which is what keeps ABYSSAL working.
     */
    protected List<FishMapPanel.SystemMark> buildSystemMarks(List<FishSpec> shown) {
        Map<StarSystemAPI, FishMapPanel.SystemMark> marks = new HashMap<>();
        List<FishMapPanel.SystemMark> out = new ArrayList<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getLocation() == null) continue;

            FishMapPanel.SystemMark mark = new FishMapPanel.SystemMark(system.getLocation(),
                    system.getBaseName());

            marks.put(system, mark);
            out.add(mark);
        }

        for (FishSpec spec : shown) {
            if (!showsRegions(spec, FishLog.isCaught(spec.id))) continue;

            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                SectorRegion at = SectorRegion.of(system);
                if (at == null || !spec.regions.contains(at)) continue;

                FishMapPanel.SystemMark mark = marks.get(system);
                if (mark == null) continue;

                mark.species.add(spec.getDisplayName());

                if (!mark.lit || spec.rarity.ordinal() > getRarityOrdinal(mark.litColor)) {
                    mark.lit = true;
                    mark.litColor = spec.rarity.color;
                }
            }
        }

        return out;
    }

    /**
     * The selected species' approximate waters, as shaded patches in its rarity's colour - only
     * for a species whose regions the map is allowed to show, under the same gate that lights the
     * systems. The eight geometric regions become rectangles (the rim quadrants L-shapes, so two
     * apiece); ABYSSAL is not a place on the map and stays with its lit systems.
     */
    protected List<FishMapPanel.AreaMark> buildAreaMarks(List<FishSpec> shown) {
        List<FishMapPanel.AreaMark> out = new ArrayList<>();
        if (selectedId == null) return out;

        FishSpec spec = null;
        for (FishSpec candidate : shown) {
            if (selectedId.equals(candidate.id)) spec = candidate;
        }

        if (spec == null || !showsRegions(spec, FishLog.isCaught(spec.id))) return out;

        //the sector's reach, measured the way the map measures it, with the same breathing room
        float minX = -90000f, minY = -55000f, maxX = 90000f, maxY = 55000f;
        boolean any = false;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getLocation() == null) continue;

            if (!any) {
                any = true;
                minX = maxX = system.getLocation().x;
                minY = maxY = system.getLocation().y;
            }

            minX = Math.min(minX, system.getLocation().x);
            minY = Math.min(minY, system.getLocation().y);
            maxX = Math.max(maxX, system.getLocation().x);
            maxY = Math.max(maxY, system.getLocation().y);
        }

        minX -= 6000f;
        minY -= 6000f;
        maxX += 6000f;
        maxY += 6000f;

        for (SectorRegion region : spec.regions) {
            addRegionArea(region, spec.rarity.color, minX, minY, maxX, maxY, out);
        }

        return out;
    }

    protected void addRegionArea(SectorRegion region, Color color, float minX, float minY,
                                 float maxX, float maxY, List<FishMapPanel.AreaMark> out) {
        float coreW = FishConstants.CORE_BAND_HALF_WIDTH;
        float coreH = FishConstants.CORE_BAND_HALF_HEIGHT;

        switch (region) {
            case CORE_NE -> out.add(new FishMapPanel.AreaMark(0f, 0f, coreW, coreH, color));
            case CORE_NW -> out.add(new FishMapPanel.AreaMark(-coreW, 0f, coreW, coreH, color));
            case CORE_SE -> out.add(new FishMapPanel.AreaMark(0f, -coreH, coreW, coreH, color));
            case CORE_SW -> out.add(new FishMapPanel.AreaMark(-coreW, -coreH, coreW, coreH, color));

            case RIM_NE -> {
                out.add(new FishMapPanel.AreaMark(coreW, 0f, maxX - coreW, maxY, color));
                out.add(new FishMapPanel.AreaMark(0f, coreH, coreW, maxY - coreH, color));
            }
            case RIM_NW -> {
                out.add(new FishMapPanel.AreaMark(minX, 0f, -coreW - minX, maxY, color));
                out.add(new FishMapPanel.AreaMark(-coreW, coreH, coreW, maxY - coreH, color));
            }
            case RIM_SE -> {
                out.add(new FishMapPanel.AreaMark(coreW, minY, maxX - coreW, -minY, color));
                out.add(new FishMapPanel.AreaMark(0f, minY, coreW, -coreH - minY, color));
            }
            case RIM_SW -> {
                out.add(new FishMapPanel.AreaMark(minX, minY, -coreW - minX, -minY, color));
                out.add(new FishMapPanel.AreaMark(-coreW, minY, coreW, -coreH - minY, color));
            }

            case ABYSSAL -> {
                //a property of the system, not a place - its lit systems say it already
            }
        }
    }

    /** The ordinal of the rarity a lit colour came from, so the rarest claimant keeps the light. */
    protected int getRarityOrdinal(Color color) {
        for (FishRarity rarity : FishRarity.values()) {
            if (rarity.color.equals(color)) return rarity.ordinal();
        }

        return -1;
    }

    protected List<FishMapPanel.CatchMark> buildCatchMarks(List<FishSpec> shown) {
        List<FishMapPanel.CatchMark> out = new ArrayList<>();

        for (FishSpec spec : shown) {
            if (!FishLog.isCaught(spec.id)) continue;

            FishLogEntry logged = FishLog.get(spec.id);
            if (logged == null || logged.recordLocationInHyper == null) continue;

            String system = logged.recordSystemName == null
                    ? "an unrecorded system" : logged.recordSystemName;

            out.add(new FishMapPanel.CatchMark(logged.recordLocationInHyper, spec.rarity.color,
                    "Record " + spec.getDisplayName() + "  -  taken in " + system));
        }

        return out;
    }

    // --- Tooltips, which is where all the explaining lives. ---

    protected TooltipMakerAPI.TooltipCreator createSimpleTooltip(float width, String text) {
        return new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return width;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(text, 0f);
            }
        };
    }

    protected TooltipMakerAPI.TooltipCreator createChipTooltip(FishRarity rarity) {
        String name = Misc.ucFirst(rarity.name().toLowerCase());

        return new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 220f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(name, rarity.color, 0f);
                tooltip.addPara(filter.rarities.contains(rarity)
                        ? "Click to hide this rarity."
                        : "Click to show this rarity.", Misc.getGrayColor(), 6f);
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

                tooltip.addPara("A lit system is somewhere a species with location data is said"
                        + " to live, in the rarest claimant's colour. A pin is the exact spot a"
                        + " record specimen came out of the water.", 8f);

                tooltip.addPara("Click a species to centre the map on it and shade the waters it"
                        + " is said to haunt. Drag the map to pan, scroll to zoom.", 8f);

                if (Global.getSettings().isDevMode()) {
                    tooltip.addPara("Dev mode: everything in the table is shown, caught or not.",
                            Misc.getHighlightColor(), 8f);
                }
            }
        };
    }

    protected TooltipMakerAPI.TooltipCreator createRowTooltip(FishSpec spec) {
        return new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 320f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(spec.getDisplayName(), spec.rarity.color, 0f);
                tooltip.addPara(spec.getTypeName(), Misc.getGrayColor(), 2f);

                boolean caught = FishLog.isCaught(spec.id);
                FishLogEntry logged = FishLog.get(spec.id);

                if (caught && logged != null) {
                    String where = logged.recordSystemName == null
                            ? "an unrecorded system" : logged.recordSystemName;

                    tooltip.addPara("Caught " + logged.caught + (logged.caught == 1
                            ? " time." : " times."), 8f);

                    if (logged.recordLocationInHyper != null) {
                        tooltip.addPara("The pin marks where the record specimen came out of the"
                                + " water, in " + where + ".", 4f);
                    }
                }

                if (showsRegions(spec, caught) && !spec.regions.isEmpty()) {
                    tooltip.addPara("Said to live in: " + describeRegions(spec), 8f);
                } else if (Global.getSettings().isDevMode() && spec.regions.isEmpty()) {
                    tooltip.addPara("No region data in the table.", Misc.getHighlightColor(), 8f);
                }

                tooltip.addPara("Click to centre the map.", Misc.getGrayColor(), 8f);
            }
        };
    }

    protected String describeRegions(FishSpec spec) {
        List<String> parts = new ArrayList<>();
        for (SectorRegion region : spec.regions) parts.add(describeRegion(region));

        return String.join(", ", parts);
    }

    protected static String describeRegion(SectorRegion region) {
        return switch (region) {
            case CORE_NE -> "the core, north-east";
            case CORE_NW -> "the core, north-west";
            case CORE_SE -> "the core, south-east";
            case CORE_SW -> "the core, south-west";
            case RIM_NE -> "the rim, north-east";
            case RIM_NW -> "the rim, north-west";
            case RIM_SE -> "the rim, south-east";
            case RIM_SW -> "the rim, south-west";
            case ABYSSAL -> "abyssal systems";
        };
    }

    // --- The drawn controls. ---

    /** One rarity as a chip: its colour when it is being shown, a quiet outline when it is not. */
    protected class ChipPlugin extends BaseCustomUIPanelPlugin {

        protected final FishRarity rarity;
        protected PositionAPI pos;

        public ChipPlugin(FishRarity rarity) {
            this.rarity = rarity;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;

            float px = pos.getX();
            float py = pos.getY();
            float w = pos.getWidth();
            float h = pos.getHeight();

            boolean on = filter.rarities.contains(rarity);
            boolean hovered = ShopUi.contains(px, py, w, h,
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

            if (on) {
                ShopUi.drawQuad(px, py, w, h, rarity.color, (hovered ? 0.65f : 0.5f) * alphaMult);
                ShopUi.drawQuad(px, py, w, 2f, rarity.color, 0.95f * alphaMult);
            } else {
                //off is absence, not another colour: the dark field with only the underline
                //remembering what would come back
                ShopUi.drawQuad(px, py, w, h, Misc.getDarkPlayerColor(),
                        (hovered ? 0.35f : 0.18f) * alphaMult);
                ShopUi.drawQuad(px, py, w, 2f, rarity.color, 0.35f * alphaMult);
            }
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
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
                onChipToggled(rarity);

                return;
            }
        }
    }

    /**
     * The line over the list: what it is, how much of it there is, and the question mark that
     * holds the legend - drawn live, so the count is never stale.
     */
    protected class ListHeaderPlugin extends BaseCustomUIPanelPlugin {

        protected PositionAPI pos;

        protected transient LazyFont.DrawableString text;
        protected transient String written;
        protected transient LazyFont.DrawableString help;

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            float px = pos.getX();
            float py = pos.getY();
            float w = pos.getWidth();
            float h = pos.getHeight();

            String wanted = shownCount == 0 ? "SPECIES - NONE MATCH" : "SPECIES - " + shownCount;

            if (text == null || !wanted.equals(written)) {
                written = wanted;
                text = ShopUi.createText(small, wanted);
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            text.setBaseColor(ShopUi.withAlpha(Misc.getBasePlayerColor(), alphaMult));
            text.draw(Math.round(px), Math.round(py + h * 0.5f + text.getHeight() * 0.5f));

            if (help == null) {
                help = ShopUi.createText(small, "?");
                help.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
            }

            help.setBaseColor(ShopUi.withAlpha(Misc.getGrayColor(), alphaMult));
            help.draw(Math.round(px + w - 2f), Math.round(py + h * 0.5f + help.getHeight() * 0.5f));

            ShopUi.drawQuad(px, py, w, 1f, Misc.getDarkPlayerColor(), 0.8f * alphaMult);
        }
    }

    /** What the map is currently allowed to draw. */
    public static class Filter {

        public String search = "";
        public final Set<FishRarity> rarities = new LinkedHashSet<>();

        public Filter() {
            for (FishRarity rarity : FishRarity.values()) rarities.add(rarity);
        }

        public boolean accepts(FishSpec spec) {
            if (!rarities.contains(spec.rarity)) return false;
            if (search == null || search.trim().isEmpty()) return true;

            String needle = search.trim().toLowerCase();

            return spec.getDisplayName().toLowerCase().contains(needle)
                    || spec.id.toLowerCase().contains(needle);
        }
    }
}
