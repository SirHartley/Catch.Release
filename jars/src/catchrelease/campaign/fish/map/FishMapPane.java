package catchrelease.campaign.fish.map;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLocationSummary;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
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

/**
 * Filter pane shown beside the sector map when the Fish filter is on: a live search field, one
 * chip per type, and the known species as rows, each with a tooltip carrying its own art.
 * <p>
 * The selection is the mode: nothing picked shades each enabled type's whole territory (survey
 * view); picking species narrows shading to just those (up to {@link #MAX_SELECTED}); DESELECT
 * ALL returns to survey. F2 over a row opens that species' codex page.
 * <p>
 * This class is both the pane's builder and its panel plugin - the host creates a custom panel
 * with this as the plugin, then calls {@link #mount}. Controls are built once; only the row list
 * is torn down on a filter change, so the search field keeps the keyboard focus.
 */
public class FishMapPane extends BaseCustomUIPanelPlugin {

    /** What the pane needs from whoever put it on the screen. */
    public interface Host {
        /** Filter or selection changed - waters need re-cutting. */
        void onPresenceChanged();

        /** A row was clicked - point the map at this species. */
        void onSpeciesFocused(FishSpec spec);
    }

    public static final float WIDTH = 250f;

    public static final float PAD = 10f;
    public static final float SEARCH_HEIGHT = 22f;
    public static final float CHIP_HEIGHT = 34f;
    public static final float CHIP_GAP = 4f;
    public static final float DESELECT_HEIGHT = 20f;
    public static final float HEADER_HEIGHT = 20f;
    public static final float CONTROLS_HEIGHT = 124f;
    public static final float ROW_HEIGHT = 24f;

    public static final String SEARCH_GHOST = "Search...";

    /** How many species can be up at once - matches the three available fill weaves. */
    public static final int MAX_SELECTED = 3;

    public static final String CHIP_ICON_FONT = "graphics/fonts/victor10.fnt";

    protected static transient LazyFont tinyFont;
    protected static transient boolean tinyChecked = false;

    protected static LazyFont getTinyFont() {
        if (tinyChecked) return tinyFont;
        tinyChecked = true;

        try {
            tinyFont = LazyFont.loadFont(CHIP_ICON_FONT);
        } catch (Exception e) {
            tinyFont = null;
        }

        return tinyFont;
    }

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

    public FishMapPane(Host host) {
        this.host = host;
    }

    public FishPresence.Filter getFilter() {
        return filter;
    }

    /** The species whose waters are being shown, in the order they were picked. */
    public Set<String> getSelectedIds() {
        return selectedIds;
    }

    /** No selection means the survey view. */
    public boolean isCategoryView() {
        return selectedIds.isEmpty();
    }

    /** Adds a species to the selection (e.g. from the codex's "show on map") without clearing existing picks. */
    public void showSpecies(String speciesId) {
        if (speciesId == null || selectedIds.contains(speciesId)) return;

        //room made by retiring the oldest pick rather than refusing the request
        if (selectedIds.size() >= MAX_SELECTED) {
            selectedIds.remove(selectedIds.iterator().next());
        }

        selectedIds.add(speciesId);
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

    /** Field and border under the widgets, matching vanilla's own style (1px player colour, square corners). */
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
     * {@link TextFieldAPI} has no change callback or placeholder support, so both are polled by
     * hand off {@code hasFocus}: ghost text fills the empty field, clears on focus, returns if left
     * empty, and never reaches the filter itself.
     */
    @Override
    public void advance(float amount) {
        if (searchField == null) return;

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
            rebuildList();
            host.onPresenceChanged();
        }
    }

    /** Builds the part that never rebuilds: search, type chips, deselect, and the list header. */
    protected void buildControls() {
        float innerWidth = width - PAD * 2f;
        TooltipMakerAPI controls = panel.createUIElement(innerWidth, CONTROLS_HEIGHT, false);

        searchField = controls.addTextField(innerWidth, SEARCH_HEIGHT, ShopUi.FONT_SMALL, 4f);
        searchField.setText(filter.search == null || filter.search.isEmpty()
                ? SEARCH_GHOST : filter.search);
        controls.addTooltipToPrevious(createSimpleTooltip(260f,
                "Type to filter the species by name. The list and the waters follow as you type."),
                TooltipMakerAPI.TooltipLocation.BELOW);

        FishType[] types = FishType.values();

        //floored to avoid a soft edge from a fractional-pixel chip width
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

        CustomPanelAPI deselect = panel.createCustomPanel(innerWidth, DESELECT_HEIGHT,
                new DeselectPlugin());
        controls.addCustom(deselect, 8f);
        controls.addTooltipTo(createSimpleTooltip(260f,
                "Clear the picked species and return to shading whole categories."),
                deselect, TooltipMakerAPI.TooltipLocation.BELOW);

        CustomPanelAPI header = panel.createCustomPanel(innerWidth, HEADER_HEIGHT, new ListHeaderPlugin());
        controls.addCustom(header, 8f);
        controls.addTooltipTo(createLegendTooltip(), header, TooltipMakerAPI.TooltipLocation.BELOW);

        panel.addUIElement(controls).inTL(PAD, PAD);
    }

    /** Rebuilds the rows for the current filter; controls are untouched. */
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

        //a scrollable element is wrapped in a scroller - the wrapper is what must be removed later
        listRemovable = listElement.getExternalScroller() != null
                ? (UIComponentAPI) listElement.getExternalScroller() : listElement;
    }

    protected void onChipToggled(FishType type) {
        if (!filter.types.remove(type)) filter.types.add(type);

        rebuildList();
        host.onPresenceChanged();
    }

    /** Toggles a row's waters in/out of the picture; the first pick leaves the survey view. */
    protected void onRowClicked(FishSpec spec) {
        if (selectedIds.contains(spec.id)) {
            selectedIds.remove(spec.id);
            host.onPresenceChanged();
            return;
        }

        if (selectedIds.size() >= MAX_SELECTED) return;

        selectedIds.add(spec.id);
        host.onSpeciesFocused(spec);
        host.onPresenceChanged();
    }

    protected void onDeselectAll() {
        if (selectedIds.isEmpty()) return;

        selectedIds.clear();
        host.onPresenceChanged();
    }

    // --- Tooltips ---

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
                        ? "Click to hide this type's species and waters."
                        : "Click to show this type's species and waters.", Misc.getGrayColor(), 6f);
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

                tooltip.addPara("Enable type chips to shade whole territories. Pick species off"
                        + " the list to shade only those - up to three at once for planning a"
                        + " route, the first filled, the second striped one way, the third the"
                        + " other, so overlaps cross instead of piling.", 8f);

                tooltip.addPara("A filled circle by a name is a species somebody aboard has"
                        + " landed. A hollow one is known only from survey data: its waters"
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
        return new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 320f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                boolean caught = FishLog.isCaught(spec.id);
                FishLogEntry logged = FishLog.get(spec.id);

                //fallback icon for anything not yet landed - a survey knows where a thing lives, not what it looks like
                String icon = caught ? FishCodex.getIcon(spec) : FishConstants.ITEM_ICON_FALLBACK;
                if (icon != null && !icon.isEmpty()) {
                    try {
                        Global.getSettings().loadTexture(icon);
                        tooltip.addImage(icon, 48f, 48f, 0f);
                    } catch (Exception e) {
                        //a tooltip without a portrait is still a tooltip
                    }
                }

                tooltip.addPara(spec.getDisplayName(), spec.rarity.color, 8f);
                tooltip.addPara(spec.getTypeName(), Misc.getGrayColor(), 2f);

                if (caught && logged != null) {
                    tooltip.addPara("Caught " + logged.caught + (logged.caught == 1
                            ? " time." : " times."), 8f);
                } else {
                    tooltip.addPara("Known only from survey data.", Misc.getGrayColor(), 8f);
                }

                tooltip.addPara("%s", 8f, Misc.getGrayColor(), Misc.getHighlightColor(),
                        FishLocationSummary.describe(spec));

                if (Global.getSettings().isDevMode() && spec.regions.isEmpty()) {
                    tooltip.addPara("No region data in the table.", Misc.getHighlightColor(), 8f);
                }

                if (!selectedIds.contains(spec.id) && selectedIds.size() >= MAX_SELECTED) {
                    tooltip.addPara("Three waters are already up - deselect one first.",
                            Misc.getHighlightColor(), 8f);
                } else {
                    tooltip.addPara("Click to toggle its waters on the map. F2 opens the codex.",
                            Misc.getGrayColor(), 8f);
                }
            }
        };
    }

    // --- Drawn controls ---

    /** One type as a chip: the mod's placeholder mark over its name, lit while shown. */
    protected class ChipPlugin extends BaseCustomUIPanelPlugin {

        public static final float ICON_SIZE = 16f;

        protected final FishType type;
        protected PositionAPI chipPos;

        protected transient LazyFont.DrawableString text;
        protected transient SpriteAPI icon;
        protected transient boolean iconChecked;

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
                ShopUi.drawQuad(x, y, w, h, type.color, (hovered ? 0.5f : 0.35f) * alphaMult);
                ShopUi.drawQuad(x, y, w, 2f, type.color, 0.95f * alphaMult);
            } else {
                ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(),
                        (hovered ? 0.35f : 0.18f) * alphaMult);
                ShopUi.drawQuad(x, y, w, 2f, type.color, 0.35f * alphaMult);
            }

            SpriteAPI face = getIcon();
            if (face != null) {
                float scale = Math.min(ICON_SIZE / face.getWidth(), ICON_SIZE / face.getHeight());

                face.setSize(face.getWidth() * scale, face.getHeight() * scale);
                face.setNormalBlend();
                face.setAlphaMult((on ? 1f : 0.55f) * alphaMult);
                face.renderAtCenter(Math.round(x + w * 0.5f),
                        Math.round(y + h - 3f - ICON_SIZE * 0.5f));
            }

            LazyFont tiny = getTinyFont();
            if (tiny == null) return;

            if (text == null) {
                text = ShopUi.createText(tiny, type.label);
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            Color color = on ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();
            text.setBaseColor(ShopUi.withAlpha(color, alphaMult));
            text.draw(Math.round(x + (w - text.getWidth()) * 0.5f),
                    Math.round(y + 2f + text.getHeight()));
        }

        protected SpriteAPI getIcon() {
            if (iconChecked) return icon;
            iconChecked = true;

            try {
                icon = Global.getSettings().getSprite(ModPlugin.MOD_ID, "placeholder");
            } catch (Exception e) {
                icon = null;
            }

            return icon;
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

    /** DESELECT ALL button; lit only while there's a selection to clear. */
    protected class DeselectPlugin extends BaseCustomUIPanelPlugin {

        protected PositionAPI buttonPos;

        protected transient LazyFont.DrawableString text;

        @Override
        public void positionChanged(PositionAPI position) {
            buttonPos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (buttonPos == null || alphaMult <= 0f) return;

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            float x = buttonPos.getX();
            float y = buttonPos.getY();
            float w = buttonPos.getWidth();
            float h = buttonPos.getHeight();

            boolean live = !selectedIds.isEmpty();
            boolean hovered = live && ShopUi.contains(x, y, w, h,
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

            ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(),
                    (live ? (hovered ? 0.45f : 0.32f) : 0.12f) * alphaMult);

            if (text == null) {
                text = ShopUi.createText(small, "DESELECT ALL");
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            Color color = live
                    ? (hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor())
                    : Misc.getGrayColor();

            text.setBaseColor(ShopUi.withAlpha(color, (live ? 1f : 0.6f) * alphaMult));
            text.draw(Math.round(x + (w - text.getWidth()) * 0.5f),
                    Math.round(y + h * 0.5f + text.getHeight() * 0.5f));
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (buttonPos == null || selectedIds.isEmpty()) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed() || !event.isLMBDownEvent()) continue;
                if (!ShopUi.contains(buttonPos.getX(), buttonPos.getY(), buttonPos.getWidth(),
                        buttonPos.getHeight(), event.getX(), event.getY())) {
                    continue;
                }

                event.consume();
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
                onDeselectAll();

                return;
            }
        }
    }

    /** List header: live species count plus a "?" that holds the legend tooltip. */
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
     * One species row: rarity-coloured accent bar (lit while its waters are shown), a caught/known
     * mark, and the name. F2 while hovering opens the codex.
     */
    protected class RowPlugin extends BaseCustomUIPanelPlugin {

        public static final float PAD_SIDE = 8f;
        public static final float ACCENT_WIDTH = 3f;
        public static final float MARK_RADIUS = 3.5f;
        public static final float MARK_GAP = 7f;

        protected final FishSpec spec;
        protected PositionAPI rowPos;

        protected transient LazyFont.DrawableString name;

        public RowPlugin(FishSpec spec) {
            this.spec = spec;
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

            boolean selected = selectedIds.contains(spec.id);
            boolean hovered = !selected && contains(Global.getSettings().getMouseX(),
                    Global.getSettings().getMouseY());

            float field = selected ? 0.4f : hovered ? 0.3f : 0.12f;
            ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(), field * alphaMult);

            float accent = selected ? 0.9f : hovered ? 0.6f : 0.3f;
            ShopUi.drawQuad(x, y, ACCENT_WIDTH, h, spec.rarity.color, accent * alphaMult);

            Color chrome = selected || hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

            //filled = caught, hollow = known from survey only; checked live so it updates on a first catch
            boolean caught = FishLog.isCaught(spec.id);
            float markX = x + ACCENT_WIDTH + PAD_SIDE + MARK_RADIUS;
            float markY = y + h * 0.5f;

            if (caught) {
                Disc.draw(markX, markY, MARK_RADIUS, chrome, 0.9f * alphaMult, 0.9f * alphaMult, false);
            }

            //outline drawn even over the fill; needed to keep a circle this small looking round
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

            ShopUi.endClip();
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (rowPos == null) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;

                if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_F2) {
                    if (!contains(Global.getSettings().getMouseX(), Global.getSettings().getMouseY())) {
                        continue;
                    }

                    event.consume();
                    FishCodex.show(spec.id);
                    return;
                }

                if (!event.isLMBDownEvent()) continue;
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
