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
 * Filter pane beside the sector map when the Fish filter is on: a search field, one chip per
 * type, and the known species as rows, with the explaining pushed into tooltips instead.
 * <p>
 * No mode switch - the selection is the mode. Nothing picked shades each enabled type's whole
 * territory (the survey view); picking species narrows the shading to exactly those, up to
 * three at once, which is how a route gets planned. F2 over a row opens its codex page.
 * <p>
 * Also the panel's own plugin - the host mounts it via {@link #mount}. Controls are built once;
 * only the row list rebuilds on a filter change, so the search field keeps the keyboard.
 */
public class FishMapPane extends BaseCustomUIPanelPlugin {

    /** What the pane needs from whoever put it on the screen. */
    public interface Host {
        /** The filter or the selection moved - the waters on the map need re-cutting. */
        void onPresenceChanged();

        /** A row was clicked - point the map at this species. */
        void onSpeciesFocused(FishSpec spec);

        /** The planner button was pressed - float the planner over the map. */
        void onPlannerRequested();
    }

    public static final float WIDTH = 250f;

    public static final float PAD = 10f;
    public static final float PLANNER_HEIGHT = 22f;
    public static final float SEARCH_HEIGHT = 22f;
    public static final float CHIP_HEIGHT = 34f;
    public static final float CHIP_GAP = 4f;
    public static final float DESELECT_HEIGHT = 20f;
    public static final float HEADER_HEIGHT = 20f;
    public static final float CONTROLS_HEIGHT = 154f;
    public static final float ROW_HEIGHT = 24f;

    public static final String SEARCH_GHOST = "Search...";

    /** How many species can be up at once. Three weaves exist, and a fourth would have to pile. */
    public static final int MAX_SELECTED = 3;

    /** The chips' own face: category art does not exist yet, and a stand-in says so honestly. */
    public static final String CHIP_ICON_FONT = "graphics/fonts/victor10.fnt";

    protected static transient LazyFont tinyFont;
    protected static transient boolean tinyChecked = false;

    /** The smallest hand the game writes in, for labels that were shouting at chip size. */
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

    /** No selection means the survey: the selection itself is the mode switch. */
    public boolean isCategoryView() {
        return selectedIds.isEmpty();
    }

    /**
     * Picks a species without a click - how an outside request (the codex's jump) arrives.
     * Existing picks stay, since a selection is a route being planned.
     */
    public void showSpecies(String speciesId) {
        if (speciesId == null || selectedIds.contains(speciesId)) return;

        //the codex asked, so room is made: the oldest pick retires rather than the request failing
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

    /** Pane's own field and border, drawn under the widgets in vanilla's manner - 1px
     *  player-colour border, square corners, since this pane sits among vanilla panels. */
    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        //transparent black, the way the screen's own panels sit on it - no colour wash
        ShopUi.drawQuad(x, y, w, h, Color.BLACK, 0.7f * alphaMult);

        Color border = Misc.getDarkPlayerColor();
        ShopUi.drawQuad(x, y, w, 1f, border, alphaMult);
        ShopUi.drawQuad(x, y + h - 1f, w, 1f, border, alphaMult);
        ShopUi.drawQuad(x, y, 1f, h, border, alphaMult);
        ShopUi.drawQuad(x + w - 1f, y, 1f, h, border, alphaMult);
    }

    /** Text field has no change callback or placeholder, so both are worked by hand off
     *  {@code hasFocus} - the ghost text fills the empty field and never reaches the filter. */
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

    /** The part that never rebuilds: planner, search, the type chips, deselect, and the header. */
    protected void buildControls() {
        float innerWidth = width - PAD * 2f;
        TooltipMakerAPI controls = panel.createUIElement(innerWidth, CONTROLS_HEIGHT, false);

        //planner sits above search - planning is the point, search is just how species get found
        CustomPanelAPI planner = panel.createCustomPanel(innerWidth, PLANNER_HEIGHT,
                new PlannerButtonPlugin());
        controls.addCustom(planner, 0f);
        controls.addTooltipToPrevious(createSimpleTooltip(260f,
                "Pick the fish you need - open jobs and upgrade asks are suggested - and plot"
                        + " the shortest route through their waters."),
                TooltipMakerAPI.TooltipLocation.BELOW);

        searchField = controls.addTextField(innerWidth, SEARCH_HEIGHT, ShopUi.FONT_SMALL, 8f);
        searchField.setText(filter.search == null || filter.search.isEmpty()
                ? SEARCH_GHOST : filter.search);
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

    /** Fresh rows for the current filter. The controls stay put, and so does the keyboard. */
    protected void rebuildList() {
        if (listRemovable != null) panel.removeComponent(listRemovable);

        List<FishSpec> shown = FishPresence.getShown(filter);
        shownCount = shown.size();

        float listHeight = height - CONTROLS_HEIGHT - PAD * 2f;
        //same air on both sides - the list's slot is inset PAD left and right alike
        listElement = panel.createUIElement(width - PAD * 2f, listHeight, true);

        for (FishSpec spec : shown) {
            CustomPanelAPI row = panel.createCustomPanel(width - PAD * 2f - 6f, ROW_HEIGHT,
                    new RowPlugin(spec));

            listElement.addCustom(row, 3f);
            listElement.addTooltipTo(createRowTooltip(spec), row, TooltipMakerAPI.TooltipLocation.LEFT);
        }

        listViewport = panel.addUIElement(listElement);
        listViewport.inTL(PAD, PAD + CONTROLS_HEIGHT);

        //a scrollable element comes back wrapped in a scroller - that's what's removed/stored
        listRemovable = listElement.getExternalScroller() != null
                ? (UIComponentAPI) listElement.getExternalScroller() : listElement;
    }

    protected void onChipToggled(FishType type) {
        if (!filter.types.remove(type)) filter.types.add(type);

        rebuildList();
        host.onPresenceChanged();
    }

    /** A click toggles the row's waters in/out - several at once is the point, that's how a
     *  route gets planned. */
    protected void onRowClicked(FishSpec spec) {
        if (selectedIds.contains(spec.id)) {
            selectedIds.remove(spec.id);
            host.onPresenceChanged();
            return;
        }

        //three weaves, three picks - a fourth is refused rather than repainted over the others
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
        //the shared species card, with this pane's own action line read live at hover time
        return FishTooltips.create(spec, () ->
                !selectedIds.contains(spec.id) && selectedIds.size() >= MAX_SELECTED
                        ? "Three waters are already up - deselect one first."
                        : "Click to toggle its waters on the map. F2 opens the codex.");
    }

    // --- The drawn controls. ---

    /** One type as a chip: a placeholder mark (category art doesn't exist yet) over the name,
     *  lit in the type's colour while shown. */
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
                //off is absence, not another colour - dark field with just the underline remembering
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

            //the smallest native size there is: a chip is a label, not a heading
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

    /** Always-live button that opens the planner - a plan can be made from nothing. */
    protected class PlannerButtonPlugin extends BaseCustomUIPanelPlugin {

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

            boolean hovered = ShopUi.contains(x, y, w, h,
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

            ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(),
                    (hovered ? 0.45f : 0.32f) * alphaMult);

            if (text == null) {
                text = ShopUi.createText(small, "PLAN A ROUTE");
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            Color color = hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

            text.setBaseColor(ShopUi.withAlpha(color, alphaMult));
            text.draw(Math.round(x + (w - text.getWidth()) * 0.5f),
                    Math.round(y + h * 0.5f + text.getHeight() * 0.5f));
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (buttonPos == null) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed() || !event.isLMBDownEvent()) continue;
                if (!ShopUi.contains(buttonPos.getX(), buttonPos.getY(), buttonPos.getWidth(),
                        buttonPos.getHeight(), event.getX(), event.getY())) {
                    continue;
                }

                event.consume();
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
                host.onPlannerRequested();

                return;
            }
        }
    }

    /** Way back to the survey: lit while there is anything to deselect, quiet otherwise. */
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

    /** One species row: rarity-coloured accent bar, a circle marking caught (filled) vs
     *  survey-only (hollow), and the name. Bar stays lit while its waters are on the map;
     *  F2 opens the codex. */
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
