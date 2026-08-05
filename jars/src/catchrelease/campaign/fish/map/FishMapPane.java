package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopUi;
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

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The filter pane that appears beside the sector map when the Fish filter is on: a search field
 * the list chases a keystroke at a time, one chip per type - fish, crab, mollusc, other - and
 * the known species as rows. Everything that would have been a paragraph of instructions is a
 * tooltip.
 * <p>
 * The waters have two modes, on a pair of tabs. CATEGORY shades each enabled type's whole
 * territory as one merged shape - the survey view. SPECIES shades only the species the player
 * has picked off the list, and lets them pick several at once - the route-planning view, where
 * clicking rows toggles their waters in and out. In both, overlapping waters interleave rather
 * than stack.
 * <p>
 * This class is the pane's own panel plugin as well as its builder - the host makes a custom
 * panel with this as the plugin, then calls {@link #mount}. The controls are built once and never
 * rebuilt; only the row list is torn down on a filter change, which is what lets the search field
 * keep the keyboard.
 */
public class FishMapPane extends BaseCustomUIPanelPlugin {

    /** Which waters the map is shading: whole categories, or hand-picked species. */
    public enum Mode {
        CATEGORY("CATEGORY"),
        SPECIES("SPECIES");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /** What the pane needs from whoever put it on the screen. */
    public interface Host {
        /** The filter, the mode or the selection moved - the waters on the map need re-cutting. */
        void onPresenceChanged();

        /** A row was clicked - point the map at this species. */
        void onSpeciesFocused(FishSpec spec);
    }

    public static final float WIDTH = 250f;

    public static final float PAD = 10f;
    public static final float SEARCH_HEIGHT = 22f;
    public static final float CHIP_HEIGHT = 20f;
    public static final float CHIP_GAP = 4f;
    public static final float MODE_HEIGHT = 20f;
    public static final float HEADER_HEIGHT = 20f;
    public static final float CONTROLS_HEIGHT = 124f;
    public static final float ROW_HEIGHT = 24f;

    protected final Host host;
    protected final FishPresence.Filter filter = new FishPresence.Filter();

    protected CustomPanelAPI panel;
    protected float width, height;
    protected PositionAPI pos;

    protected TextFieldAPI searchField;
    protected TooltipMakerAPI listElement;
    protected UIComponentAPI listRemovable;
    protected PositionAPI listViewport;

    protected Mode mode = Mode.CATEGORY;
    protected final Set<String> selectedIds = new LinkedHashSet<>();
    protected int shownCount = 0;

    public FishMapPane(Host host) {
        this.host = host;
    }

    public FishPresence.Filter getFilter() {
        return filter;
    }

    public Mode getMode() {
        return mode;
    }

    /** The species whose waters the SPECIES mode is showing, in the order they were picked. */
    public Set<String> getSelectedIds() {
        return selectedIds;
    }

    /** Builds the controls and the first list into the pane's own panel. Call once. */
    public void mount(CustomPanelAPI panel, float width, float height) {
        this.panel = panel;
        this.width = width;
        this.height = height;

        buildControls();
        rebuildList();
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    /**
     * The pane's own field and border, under the widgets. The border is the game's own manner -
     * one pixel of the player colour, corners square - because this pane sits among vanilla
     * panels, and a guest dresses like the house.
     */
    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        ShopUi.drawQuad(x, y, w, h, Color.BLACK, 0.8f * alphaMult);
        ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(), 0.07f * alphaMult);

        Color border = Misc.getDarkPlayerColor();
        ShopUi.drawQuad(x, y, w, 1f, border, alphaMult);
        ShopUi.drawQuad(x, y + h - 1f, w, 1f, border, alphaMult);
        ShopUi.drawQuad(x, y, 1f, h, border, alphaMult);
        ShopUi.drawQuad(x + w - 1f, y, 1f, h, border, alphaMult);
    }

    /**
     * A text field has no change callback, so it is polled. Only the row list rebuilds - never
     * the field - so the list can chase the text a keystroke at a time.
     */
    @Override
    public void advance(float amount) {
        if (searchField == null) return;

        String text = searchField.getText();
        String current = filter.search == null ? "" : filter.search;

        if (text != null && !text.equals(current)) {
            filter.search = text;
            rebuildList();
            host.onPresenceChanged();
        }
    }

    /** The part that never rebuilds: search, chips, and the live list header. */
    protected void buildControls() {
        float innerWidth = width - PAD * 2f;
        TooltipMakerAPI controls = panel.createUIElement(innerWidth, CONTROLS_HEIGHT, false);

        searchField = controls.addTextField(innerWidth, SEARCH_HEIGHT, ShopUi.FONT_SMALL, 4f);
        searchField.setText(filter.search == null ? "" : filter.search);
        controls.addTooltipToPrevious(createSimpleTooltip(260f,
                "Type to filter the species by name. The list and the waters follow as you type."),
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
            CustomPanelAPI chip = panel.createCustomPanel(chipWidth, CHIP_HEIGHT, new ChipPlugin(type));

            chipRow.addComponent(chip).inTL(i * (chipWidth + CHIP_GAP), 0f);
            controls.addTooltipTo(createChipTooltip(type), chip, TooltipMakerAPI.TooltipLocation.BELOW);
        }

        controls.addCustom(chipRow, 8f);

        //the two ways of shading the map, as a pair of tabs
        float tabWidth = (float) Math.floor((innerWidth - CHIP_GAP) / 2f);

        CustomPanelAPI modeRow = panel.createCustomPanel(innerWidth, MODE_HEIGHT,
                new BaseCustomUIPanelPlugin() {
                });

        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length; i++) {
            CustomPanelAPI tab = panel.createCustomPanel(tabWidth, MODE_HEIGHT, new ModeTabPlugin(modes[i]));

            modeRow.addComponent(tab).inTL(i * (tabWidth + CHIP_GAP), 0f);
            controls.addTooltipTo(createModeTooltip(modes[i]), tab, TooltipMakerAPI.TooltipLocation.BELOW);
        }

        controls.addCustom(modeRow, 8f);

        CustomPanelAPI header = panel.createCustomPanel(innerWidth, HEADER_HEIGHT, new ListHeaderPlugin());
        controls.addCustom(header, 8f);
        controls.addTooltipTo(createLegendTooltip(), header, TooltipMakerAPI.TooltipLocation.BELOW);

        panel.addUIElement(controls).inTL(PAD, PAD);
    }

    /** Fresh rows for the current filter. The controls stay put, and so does the keyboard. */
    protected void rebuildList() {
        if (listRemovable != null) panel.removeComponent(listRemovable);

        List<FishSpec> shown = FishPresence.getShown(filter);
        shownCount = shown.size();

        float listHeight = height - CONTROLS_HEIGHT - PAD * 2f;
        listElement = panel.createUIElement(width - PAD, listHeight, true);

        for (FishSpec spec : shown) {
            CustomPanelAPI row = panel.createCustomPanel(width - PAD * 2f - 6f, ROW_HEIGHT,
                    new RowPlugin(spec));

            listElement.addCustom(row, 3f);
            listElement.addTooltipTo(createRowTooltip(spec), row, TooltipMakerAPI.TooltipLocation.LEFT);
        }

        listViewport = panel.addUIElement(listElement);
        listViewport.inTL(PAD, PAD + CONTROLS_HEIGHT);

        //a scrollable element goes in wrapped in a scroller, and the wrapper is what comes out
        listRemovable = listElement.getExternalScroller() != null
                ? (UIComponentAPI) listElement.getExternalScroller() : listElement;
    }

    protected void onChipToggled(FishType type) {
        if (!filter.types.remove(type)) filter.types.add(type);

        rebuildList();
        host.onPresenceChanged();
    }

    protected void onModeClicked(Mode picked) {
        if (mode == picked) return;

        mode = picked;
        host.onPresenceChanged();
    }

    /**
     * In SPECIES mode a click toggles the row's waters in and out of the picture - several at
     * once is the point, that is how a route gets planned. In CATEGORY mode the shapes belong to
     * the types, so a click only points the map.
     */
    protected void onRowClicked(FishSpec spec) {
        if (mode == Mode.SPECIES) {
            boolean added = selectedIds.add(spec.id);
            if (!added) selectedIds.remove(spec.id);

            if (added) host.onSpeciesFocused(spec);
            host.onPresenceChanged();
            return;
        }

        host.onSpeciesFocused(spec);
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
                return 220f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(type.label, type.color, 0f);
                tooltip.addPara(filter.types.contains(type)
                        ? "Click to hide this type."
                        : "Click to show this type.", Misc.getGrayColor(), 6f);
            }
        };
    }

    protected TooltipMakerAPI.TooltipCreator createModeTooltip(Mode tabMode) {
        return new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 280f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                if (tabMode == Mode.CATEGORY) {
                    tooltip.addPara("Category", Misc.getBasePlayerColor(), 0f);
                    tooltip.addPara("Shade each enabled type's whole territory as one shape.", 6f);
                } else {
                    tooltip.addPara("Species", Misc.getBasePlayerColor(), 0f);
                    tooltip.addPara("Shade only the species you pick off the list - several at"
                            + " once, for planning a route.", 6f);
                }
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
                tooltip.addPara("Reading the waters", Misc.getBasePlayerColor(), 0f);

                tooltip.addPara("A shaded shape is water something with location data is said to"
                        + " haunt. In CATEGORY it is a type's whole territory; in SPECIES it is"
                        + " the species you have picked, and overlapping waters take turns at the"
                        + " pixels rather than piling on them.", 8f);

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
                    tooltip.addPara("Caught " + logged.caught + (logged.caught == 1
                            ? " time." : " times."), 8f);
                }

                if (FishPresence.showsRegions(spec)) {
                    tooltip.addPara("Its waters are shaded on the map.", 8f);
                } else if (caught) {
                    tooltip.addPara("Already landed - its waters are no longer shaded.", 8f);
                } else if (Global.getSettings().isDevMode() && spec.regions.isEmpty()) {
                    tooltip.addPara("No region data in the table.", Misc.getHighlightColor(), 8f);
                }

                tooltip.addPara(mode == Mode.SPECIES
                        ? "Click to toggle its waters on the map."
                        : "Click to centre the map.", Misc.getGrayColor(), 8f);
            }
        };
    }

    // --- The drawn controls. ---

    /** One type as a chip: its colour when it is being shown, a quiet outline when it is not. */
    protected class ChipPlugin extends BaseCustomUIPanelPlugin {

        protected final FishType type;
        protected PositionAPI chipPos;

        public ChipPlugin(FishType type) {
            this.type = type;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            chipPos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (chipPos == null || alphaMult <= 0f) return;

            float x = chipPos.getX();
            float y = chipPos.getY();
            float w = chipPos.getWidth();
            float h = chipPos.getHeight();

            boolean on = filter.types.contains(type);
            boolean hovered = ShopUi.contains(x, y, w, h,
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

            if (on) {
                ShopUi.drawQuad(x, y, w, h, type.color, (hovered ? 0.65f : 0.5f) * alphaMult);
                ShopUi.drawQuad(x, y, w, 2f, type.color, 0.95f * alphaMult);
            } else {
                //off is absence, not another colour: the dark field with only the underline
                //remembering what would come back
                ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(),
                        (hovered ? 0.35f : 0.18f) * alphaMult);
                ShopUi.drawQuad(x, y, w, 2f, type.color, 0.35f * alphaMult);
            }
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (chipPos == null) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed() || !event.isLMBDownEvent()) continue;
                if (!ShopUi.contains(chipPos.getX(), chipPos.getY(), chipPos.getWidth(),
                        chipPos.getHeight(), event.getX(), event.getY())) {
                    continue;
                }

                event.consume();
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
                onChipToggled(type);

                return;
            }
        }
    }

    /** One of the two shading modes as a tab: bright and underlined while it is the open one. */
    protected class ModeTabPlugin extends BaseCustomUIPanelPlugin {

        protected final Mode tabMode;
        protected PositionAPI tabPos;

        protected transient LazyFont.DrawableString text;

        public ModeTabPlugin(Mode tabMode) {
            this.tabMode = tabMode;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            tabPos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (tabPos == null || alphaMult <= 0f) return;

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            float x = tabPos.getX();
            float y = tabPos.getY();
            float w = tabPos.getWidth();
            float h = tabPos.getHeight();

            boolean active = mode == tabMode;
            boolean hovered = !active && ShopUi.contains(x, y, w, h,
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

            float field = active ? 0.5f : hovered ? 0.3f : 0.12f;
            ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(), field * alphaMult);

            if (active) {
                ShopUi.drawQuad(x, y, w, 2f, Misc.getBrightPlayerColor(), 0.9f * alphaMult);
            }

            if (text == null) {
                text = ShopUi.createText(small, tabMode.label);
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            Color color = active ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();
            text.setBaseColor(ShopUi.withAlpha(color, alphaMult));
            text.draw(Math.round(x + (w - text.getWidth()) * 0.5f),
                    Math.round(y + h * 0.5f + text.getHeight() * 0.5f));
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (tabPos == null) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed() || !event.isLMBDownEvent()) continue;
                if (!ShopUi.contains(tabPos.getX(), tabPos.getY(), tabPos.getWidth(),
                        tabPos.getHeight(), event.getX(), event.getY())) {
                    continue;
                }

                event.consume();

                //an already-open tab takes the click and does nothing with it
                if (mode != tabMode) {
                    Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
                    onModeClicked(tabMode);
                }

                return;
            }
        }
    }

    /**
     * The line over the list: what it is, how much of it there is, and the question mark that
     * holds the legend - drawn live, so the count is never stale.
     */
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

            String wanted = shownCount == 0 ? "SPECIES - NONE MATCH" : "SPECIES - " + shownCount;

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

    /**
     * One species in the list: an accent bar in the rarity's colour, the name, and one quiet word
     * at the right end for what the log has on it. The bar stays lit while the map is pointed at
     * this species - a list beside a map that cannot point at the map is a legend, not a control.
     */
    protected class RowPlugin extends BaseCustomUIPanelPlugin {

        public static final float PAD_SIDE = 8f;
        public static final float ACCENT_WIDTH = 3f;

        protected final FishSpec spec;
        protected final String status;
        protected PositionAPI rowPos;

        protected transient LazyFont.DrawableString name;
        protected transient LazyFont.DrawableString mark;

        public RowPlugin(FishSpec spec) {
            this.spec = spec;
            this.status = FishPresence.getStatus(spec);
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

            boolean selected = mode == Mode.SPECIES && selectedIds.contains(spec.id);
            boolean hovered = !selected && contains(Global.getSettings().getMouseX(),
                    Global.getSettings().getMouseY());

            float field = selected ? 0.4f : hovered ? 0.3f : 0.12f;
            ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(), field * alphaMult);

            float accent = selected ? 0.9f : hovered ? 0.6f : 0.3f;
            ShopUi.drawQuad(x, y, ACCENT_WIDTH, h, spec.rarity.color, accent * alphaMult);

            LazyFont body = ShopUi.getBodyFont();
            if (body != null) {
                if (name == null) {
                    name = ShopUi.createText(body, spec.getDisplayName());
                    name.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
                }

                Color color = selected || hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();
                name.setBaseColor(ShopUi.withAlpha(color, alphaMult));
                name.draw(Math.round(x + ACCENT_WIDTH + PAD_SIDE),
                        Math.round(y + h * 0.5f + name.getHeight() * 0.5f));
            }

            LazyFont small = ShopUi.getSmallFont();
            if (small != null && status != null) {
                if (mark == null) {
                    mark = ShopUi.createText(small, status);
                    mark.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
                }

                mark.setBaseColor(ShopUi.withAlpha(Misc.getGrayColor(), alphaMult));
                mark.draw(Math.round(x + w - PAD_SIDE),
                        Math.round(y + h * 0.5f + mark.getHeight() * 0.5f));
            }

            ShopUi.endClip();
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (rowPos == null) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed() || !event.isLMBDownEvent()) continue;
                if (!contains(event.getX(), event.getY())) continue;

                event.consume();
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
                onRowClicked(spec);

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
