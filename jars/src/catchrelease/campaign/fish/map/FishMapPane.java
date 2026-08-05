package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopUi;
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
 * The filter pane that appears beside the sector map when the Fish filter is on: a search field
 * the list chases a keystroke at a time, one chip per type - each wearing a face borrowed from
 * its own species - and the known species as rows. Everything that would have been a paragraph
 * of instructions is a tooltip, and each row's tooltip carries the species' own art.
 * <p>
 * There is no mode switch; the selection is the mode. With nothing picked, the map shades each
 * enabled type's whole territory - the survey view. Picking species off the list narrows the
 * shading to exactly those, several at once, which is how a route gets planned; DESELECT ALL
 * hands the map back to the survey. F2 over a row opens that species' codex page.
 * <p>
 * This class is the pane's own panel plugin as well as its builder - the host makes a custom
 * panel with this as the plugin, then calls {@link #mount}. The controls are built once and never
 * rebuilt; only the row list is torn down on a filter change, which is what lets the search field
 * keep the keyboard.
 */
public class FishMapPane extends BaseCustomUIPanelPlugin {

    /** What the pane needs from whoever put it on the screen. */
    public interface Host {
        /** The filter or the selection moved - the waters on the map need re-cutting. */
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
     * Picks a species without a click on anything - how a request from outside the map, the codex's
     * jump, arrives. Anything already picked stays picked: a selection is a route being planned, and
     * being sent here to look at one more fish is no reason to lose the route.
     * <p>
     * Selecting is the whole of it, since a selection is what the species view is.
     */
    public void showSpecies(String speciesId) {
        if (speciesId == null) return;

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
     * A text field has no change callback, so it is polled - and it has no placeholder either,
     * so one is worked by hand off {@code hasFocus}: the ghost text sits in the empty field,
     * clears itself the moment the field takes the keyboard, and comes back if the field is
     * left empty. The ghost never reaches the filter.
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

    /** The part that never rebuilds: search, the type chips, deselect, and the live list header. */
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

    /**
     * A click toggles the row's waters in and out of the picture - several at once is the point,
     * that is how a route gets planned. The first pick leaves the survey; emptying the picks by
     * hand or by DESELECT ALL returns to it.
     */
    protected void onRowClicked(FishSpec spec) {
        boolean added = selectedIds.add(spec.id);
        if (!added) selectedIds.remove(spec.id);

        if (added) host.onSpeciesFocused(spec);
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

                tooltip.addPara("With nothing picked, the map shades each enabled type's whole"
                        + " territory. Pick species off the list to shade only those - several at"
                        + " once for planning a route - and overlapping waters take turns at the"
                        + " pixels rather than piling on them.", 8f);

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
                String icon = FishCodex.getIcon(spec);
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

                boolean caught = FishLog.isCaught(spec.id);
                FishLogEntry logged = FishLog.get(spec.id);

                if (caught && logged != null) {
                    tooltip.addPara("Caught " + logged.caught + (logged.caught == 1
                            ? " time." : " times."), 8f);
                }

                //where the knowledge comes from, and what it buys on the map
                if (FishPresence.showsRegions(spec)) {
                    tooltip.addPara("Known from purchased location data - its waters can be"
                            + " shaded on the map.", 8f);
                } else if (caught) {
                    tooltip.addPara("Already landed - its waters are no longer shaded.", 8f);
                } else if (Global.getSettings().isDevMode() && spec.regions.isEmpty()) {
                    tooltip.addPara("No region data in the table.", Misc.getHighlightColor(), 8f);
                }

                tooltip.addPara("Click to toggle its waters on the map. F2 opens the codex.",
                        Misc.getGrayColor(), 8f);
            }
        };
    }

    // --- The drawn controls. ---

    /**
     * One type as a chip: its face over its name, lit in its colour while it is being shown.
     * The face is borrowed from the first of its species that owns art - the chip introduces
     * the category with the category's own creature.
     */
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
                //off is absence, not another colour: the dark field with only the underline
                //remembering what would come back
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

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            if (text == null) {
                text = ShopUi.createText(small, type.label.toUpperCase());
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

            String path = type.getIconPath();
            if (path == null) return null;

            try {
                Global.getSettings().loadTexture(path);
                icon = Global.getSettings().getSprite(path);
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

    /** The way back to the survey: lit while there is anything to deselect, quiet otherwise. */
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
     * One species in the list: an accent bar in the rarity's colour and the name - everything
     * else the row used to say moved into its tooltip. The bar stays lit while its waters are
     * on the map. F2 while hovering opens the codex on it.
     */
    protected class RowPlugin extends BaseCustomUIPanelPlugin {

        public static final float PAD_SIDE = 8f;
        public static final float ACCENT_WIDTH = 3f;

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
