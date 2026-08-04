package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The catch map: this mod's own sector viewport with what lives where drawn on it, in a dialog off
 * an ability press - the same holding pen the outfitter lives in.
 * <p>
 * The map itself is {@link FishMapPanel}, built once and kept: filters hand it fresh marks rather
 * than rebuilding it, so the camera stays where the player put it. What the log knows is said in
 * the map's vocabulary - a pin in the species' colour on the exact spot a record came out, lit
 * systems where a species with location data is said to live, the player's own position ringed -
 * and the sidebar names what is shown, each row a control that points the map at its species.
 * <p>
 * Dev mode shows every region the table declares, caught or not - checking the table against the
 * sector is what dev mode is for.
 */
public class FishMapDialog implements InteractionDialogPlugin {

    public static final float WIDTH = 980f;
    public static final float HEIGHT = 640f;

    public static final float PAD = 12f;
    public static final float HEADER_HEIGHT = 38f;
    public static final float SIDEBAR_WIDTH = 250f;
    public static final float ROW_HEIGHT = 24f;

    /** Seconds the search text has to sit still before the marks are rebuilt around it. */
    public static final float SEARCH_SETTLE = 0.75f;

    /** Opens the map, if the UI will have it. */
    public static boolean open() {
        return Global.getSector().getCampaignUI()
                .showInteractionDialog(new FishMapDialog(), Global.getSector().getPlayerFleet());
    }

    protected InteractionDialogAPI dialog;
    protected Delegate delegate;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        dialog.setBackgroundDimAmount(0.6f);

        delegate = new Delegate();

        dialog.showCustomVisualDialog(WIDTH, HEIGHT, delegate);
    }

    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin,
            FishMapRowPlugin.Host {

        protected CustomPanelAPI panel;
        protected DialogCallbacks callbacks;

        protected final FishMapFilter filter = new FishMapFilter();
        protected final FishMapPanel map = new FishMapPanel();

        protected TextFieldAPI searchField;
        protected final Map<FishRarity, ButtonAPI> rarityBoxes = new LinkedHashMap<>();

        protected TooltipMakerAPI sideElement;
        protected UIComponentAPI sideRemovable;
        protected PositionAPI listViewport;

        /** What the marks on screen were built from, which is how a change is recognised. */
        protected String builtSearch = "";
        protected Set<FishRarity> builtRarities;

        protected String lastSeenSearch = "";
        protected float searchStill = 0f;

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;

            buildHeader();
            buildMapPanel();
            refresh();
        }

        /** The band across the top: the map's name, and the one line of how to hold it. */
        protected void buildHeader() {
            CustomPanelAPI header = panel.createCustomPanel(WIDTH - PAD * 2f, HEADER_HEIGHT,
                    new HeaderPlugin());

            panel.addComponent(header).inTL(PAD, PAD);
        }

        /** The glass itself. Built once - the camera belongs to the player after this. */
        protected void buildMapPanel() {
            float width = WIDTH - PAD * 3f - SIDEBAR_WIDTH;
            float height = HEIGHT - PAD * 2f - HEADER_HEIGHT - 8f;

            CustomPanelAPI holder = panel.createCustomPanel(width, height, map);

            panel.addComponent(holder).inTL(PAD, PAD + HEADER_HEIGHT + 8f);
        }

        /** Fresh marks and a fresh sidebar for the current filter. The map panel itself stays. */
        protected void refresh() {
            List<FishSpec> shown = getShown();

            map.setData(buildSystemMarks(shown), buildCatchMarks(shown));
            buildSidebar(shown);

            builtSearch = filter.search == null ? "" : filter.search;
            builtRarities = new LinkedHashSet<>(filter.rarities);
        }

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

        protected boolean showsRegions(FishSpec spec, boolean caught) {
            if (Global.getSettings().isDevMode()) return !spec.regions.isEmpty();

            return !caught && FishLog.isLocationDataUnlocked(spec.id);
        }

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

        /** The filters, and under them the species as rows that point the map where they live. */
        protected void buildSidebar(List<FishSpec> shown) {
            if (sideRemovable != null) panel.removeComponent(sideRemovable);

            float height = HEIGHT - PAD * 2f - HEADER_HEIGHT - 8f;
            float pad = 10f;

            rarityBoxes.clear();

            sideElement = panel.createUIElement(SIDEBAR_WIDTH, height, true);

            sideElement.addSectionHeading("Filters", Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 0f);

            sideElement.addPara("Search", Misc.getGrayColor(), pad);
            searchField = sideElement.addTextField(SIDEBAR_WIDTH - 14f, 3f);
            searchField.setText(filter.search == null ? "" : filter.search);

            sideElement.addPara("Rarity", Misc.getGrayColor(), pad);

            for (FishRarity rarity : FishRarity.values()) {
                ButtonAPI box = sideElement.addAreaCheckbox(
                        Misc.ucFirst(rarity.name().toLowerCase()), rarity, rarity.color,
                        Misc.getDarkPlayerColor(), rarity.color, SIDEBAR_WIDTH - 14f, 20f, 3f);

                box.setChecked(filter.rarities.contains(rarity));
                rarityBoxes.put(rarity, box);
            }

            sideElement.addSectionHeading("Species", Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, pad);

            if (shown.isEmpty()) {
                sideElement.addPara("Nothing passes the filters.", Misc.getGrayColor(), pad);
            }

            for (FishSpec spec : shown) {
                sideElement.addCustom(panel.createCustomPanel(SIDEBAR_WIDTH - 14f, ROW_HEIGHT,
                        new FishMapRowPlugin(spec, getStatus(spec), this)), 3f);
            }

            sideElement.addSectionHeading("Reading it", Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, pad);

            sideElement.addPara("A pin is a spot where one was actually landed. A lit system is"
                    + " somewhere a species with location data is said to live. Click a species to"
                    + " go there.", Misc.getGrayColor(), pad);

            if (Global.getSettings().isDevMode()) {
                sideElement.addPara("Dev mode: everything in the table is shown, caught or not.",
                        Misc.getHighlightColor(), pad);
            }

            listViewport = panel.addUIElement(sideElement);
            listViewport.inTL(WIDTH - PAD - SIDEBAR_WIDTH, PAD + HEADER_HEIGHT + 8f);

            //a scrollable element goes in wrapped in a scroller, and the wrapper is what comes out
            sideRemovable = sideElement.getExternalScroller() != null
                    ? (UIComponentAPI) sideElement.getExternalScroller() : sideElement;
        }

        protected String getStatus(FishSpec spec) {
            if (FishLog.isCaught(spec.id)) return "landed";

            //a table row with nowhere to be is a data problem, and dev mode is where it gets caught
            if (Global.getSettings().isDevMode() && spec.regions.isEmpty()) return "no data";

            return "region data";
        }

        /** A row click points the map: the pin if there is one, else the first system it is said to live in. */
        @Override
        public void onRowClicked(FishSpec spec) {
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
        public PositionAPI getListViewport() {
            return listViewport;
        }

        /**
         * Both controls are polled rather than listened to - a text field has no change callback
         * at all. A checkbox change refreshes on the spot; the search waits until the text has sat
         * still, since the sidebar rebuild takes the keyboard focus with it.
         */
        @Override
        public void advance(float amount) {
            if (builtRarities == null) return;

            if (searchField != null) {
                String text = searchField.getText();
                if (text != null && !text.equals(filter.search)) filter.search = text;
            }

            for (Map.Entry<FishRarity, ButtonAPI> entry : rarityBoxes.entrySet()) {
                if (entry.getValue() == null) continue;

                if (entry.getValue().isChecked()) filter.rarities.add(entry.getKey());
                else filter.rarities.remove(entry.getKey());
            }

            String search = filter.search == null ? "" : filter.search;

            if (!search.equals(lastSeenSearch)) {
                lastSeenSearch = search;
                searchStill = 0f;
            } else {
                searchStill += amount;
            }

            boolean raritiesChanged = !builtRarities.equals(filter.rarities);
            boolean searchSettled = !search.equals(builtSearch) && searchStill >= SEARCH_SETTLE;

            if (raritiesChanged || searchSettled) refresh();
        }

        @Override
        public CustomUIPanelPlugin getCustomPanelPlugin() {
            return this;
        }

        @Override
        public float getNoiseAlpha() {
            return 0.05f;
        }

        @Override
        public void reportDismissed(int option) {
            if (dialog != null) dialog.dismiss();
        }

        /** Escape closes it. There is nothing to lose by leaving, so nothing to confirm. */
        @Override
        public void processInput(List<InputEventAPI> events) {
            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;
                if (!event.isKeyDownEvent()) continue;
                if (event.getEventValue() != Keyboard.KEY_ESCAPE) continue;

                event.consume();
                if (callbacks != null) callbacks.dismissDialog();
            }
        }

        @Override
        public void buttonPressed(Object buttonId) {
        }

        @Override
        public void positionChanged(PositionAPI position) {
        }

        @Override
        public void renderBelow(float alphaMult) {
        }

        @Override
        public void render(float alphaMult) {
        }
    }

    /** The band across the top: the title in the shop's hand, on the shop's rule. */
    protected static class HeaderPlugin extends BaseCustomUIPanelPlugin {

        protected PositionAPI pos;
        protected transient LazyFont.DrawableString title;

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;

            LazyFont font = ShopUi.getTitleFont();
            if (font != null) {
                if (title == null) {
                    title = ShopUi.createText(font, "CATCH LOCATIONS");
                    title.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
                }

                title.setBaseColor(ShopUi.withAlpha(Misc.getBrightPlayerColor(), alphaMult));
                title.draw(Math.round(pos.getX() + 2f),
                        Math.round(pos.getY() + pos.getHeight() * 0.5f + title.getHeight() * 0.5f));
            }

            ShopUi.drawQuad(pos.getX(), pos.getY(), pos.getWidth(), 1f,
                    Misc.getBrightPlayerColor(), 0.35f * alphaMult);
        }
    }

    /** What the map is currently allowed to draw. */
    public static class FishMapFilter {

        public String search = "";
        public final Set<FishRarity> rarities = new LinkedHashSet<>();

        public FishMapFilter() {
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

    @Override
    public void advance(float amount) {
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return new HashMap<>();
    }
}
