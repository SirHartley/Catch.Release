package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
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
import com.fs.starfarer.api.ui.MapParams;
import com.fs.starfarer.api.ui.MarkerData;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The catch map: the game's own sector map with what lives where drawn on it, in a dialog of its
 * own off an ability press - the same holding pen the outfitter lives in, for the same reason. The
 * intel screen hosted this once and made a mess of it; a dialog is a frame this owns outright.
 * <p>
 * What the map knows is said in its own vocabulary: a marker in the species' colour where a
 * specimen was actually landed, and lit systems where a species with bought location data is said
 * to live, everything else dimmed under them. Which systems a region lights is asked of the region
 * resolver itself, which is what keeps ABYSSAL working - a property of a system was never going to
 * fit in a rectangle.
 * <p>
 * The map takes its parameters at creation and holds them, so a filter change tears the map side
 * down and rebuilds it. Checkboxes rebuild on the click; the search box waits until the typing has
 * stopped, because a rebuild takes the keyboard focus with it.
 */
public class FishMapDialog implements InteractionDialogPlugin {

    public static final float WIDTH = 980f;
    public static final float HEIGHT = 640f;

    public static final float PAD = 12f;
    public static final float SIDEBAR_WIDTH = 240f;

    /** Seconds the search text has to sit still before the map is rebuilt around it. */
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

    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin {

        protected CustomPanelAPI panel;
        protected DialogCallbacks callbacks;

        protected final FishMapFilter filter = new FishMapFilter();

        protected TextFieldAPI searchField;
        protected final Map<FishRarity, ButtonAPI> rarityBoxes = new LinkedHashMap<>();

        /** The map and the species list, torn down together when the filter moves. */
        protected TooltipMakerAPI mapElement;
        protected TooltipMakerAPI sideElement;

        /** What the panels on screen were built from, which is how a change is recognised. */
        protected String builtSearch = "";
        protected Set<FishRarity> builtRarities;

        protected String lastSeenSearch = "";
        protected float searchStill = 0f;

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;

            build();
        }

        protected void build() {
            List<FishSpec> shown = getShown();

            buildMap(shown);
            buildSidebar(shown);

            builtSearch = filter.search == null ? "" : filter.search;
            builtRarities = new LinkedHashSet<>(filter.rarities);
        }

        protected void buildMap(List<FishSpec> shown) {
            if (mapElement != null) panel.removeComponent(mapElement);

            float width = WIDTH - PAD * 3f - SIDEBAR_WIDTH;
            float height = HEIGHT - PAD * 2f;

            mapElement = panel.createUIElement(width, height, false);
            mapElement.createSectorMap(width, height, buildMapParams(shown, width, height),
                    FishConstants.MAP_INTEL_TITLE);

            panel.updateUIElementSizeAndMakeItProcessInput(mapElement);
            panel.addUIElement(mapElement).inTL(PAD, PAD);
        }

        /**
         * A landed species is a fact and gets a marker on the exact spot, in its own rarity's
         * colour; one only known from bought location data lights up every system its declared
         * regions cover. Dev mode shows every region the table declares, caught or not - checking
         * the table against the sector is what dev mode is for.
         */
        protected MapParams buildMapParams(List<FishSpec> shown, float width, float height) {
            MapParams params = new MapParams();

            params.starAlphaMult = 0.4f;
            params.useFullAlphaForShownSystems = true;

            for (FishSpec spec : shown) {
                boolean caught = FishLog.isCaught(spec.id);

                if (caught) addPin(params, spec);
                if (showsRegions(spec, caught)) {
                    for (StarSystemAPI system : getSystemsIn(spec.regions)) params.showSystem(system);
                }
            }

            if (params.markers != null || params.showSystems != null) {
                params.positionToShowAllMarkersAndSystems(true, Math.min(width, height));
            }

            return params;
        }

        protected void addPin(MapParams params, FishSpec spec) {
            FishLogEntry logged = FishLog.get(spec.id);
            if (logged == null || logged.recordLocationInHyper == null) return;

            if (params.markers == null) params.markers = new ArrayList<>();

            params.markers.add(new MarkerData(new Vector2f(logged.recordLocationInHyper), null,
                    spec.rarity.color));
        }

        protected boolean showsRegions(FishSpec spec, boolean caught) {
            if (Global.getSettings().isDevMode()) return !spec.regions.isEmpty();

            return !caught && FishLog.isLocationDataUnlocked(spec.id);
        }

        /**
         * The systems a set of regions covers, asked of the region resolver itself rather than of
         * the regions' geometry.
         */
        protected List<StarSystemAPI> getSystemsIn(Set<SectorRegion> regions) {
            List<StarSystemAPI> out = new ArrayList<>();

            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                SectorRegion at = SectorRegion.of(system);
                if (at != null && regions.contains(at)) out.add(system);
            }

            return out;
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

        /**
         * The filters, and under them the species the map is currently saying something about -
         * the markers do not name themselves, so the sidebar does it for them.
         */
        protected void buildSidebar(List<FishSpec> shown) {
            if (sideElement != null) panel.removeComponent(sideElement);

            float height = HEIGHT - PAD * 2f;
            float pad = 10f;

            rarityBoxes.clear();

            sideElement = panel.createUIElement(SIDEBAR_WIDTH, height, true);

            sideElement.addSectionHeading("Filters", Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 0f);

            sideElement.addPara("Search", Misc.getGrayColor(), pad);
            searchField = sideElement.addTextField(SIDEBAR_WIDTH - 10f, 3f);
            searchField.setText(filter.search == null ? "" : filter.search);

            sideElement.addPara("Rarity", Misc.getGrayColor(), pad);

            for (FishRarity rarity : FishRarity.values()) {
                ButtonAPI box = sideElement.addAreaCheckbox(
                        Misc.ucFirst(rarity.name().toLowerCase()), rarity, rarity.color,
                        Misc.getDarkPlayerColor(), rarity.color, SIDEBAR_WIDTH - 10f, 20f, 3f);

                box.setChecked(filter.rarities.contains(rarity));
                rarityBoxes.put(rarity, box);
            }

            sideElement.addSectionHeading("Showing", Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, pad);

            if (shown.isEmpty()) {
                sideElement.addPara("Nothing passes the filters.", Misc.getGrayColor(), pad);
            }

            for (FishSpec spec : shown) {
                sideElement.addPara("%s - " + getStatus(spec), 3f, spec.rarity.color,
                        spec.getDisplayName());
            }

            sideElement.addSectionHeading("Reading it", Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, pad);

            sideElement.addPara("A marker is a spot where one was actually landed.",
                    Misc.getGrayColor(), pad);
            sideElement.addPara("A lit system is somewhere a species you have location data on is"
                    + " said to live - the data names the region, not the system.",
                    Misc.getGrayColor(), 3f);

            if (Global.getSettings().isDevMode()) {
                sideElement.addPara("Dev mode: everything in the table is shown, caught or not.",
                        Misc.getHighlightColor(), pad);
            }

            panel.addUIElement(sideElement).inTL(WIDTH - PAD - SIDEBAR_WIDTH, PAD);
        }

        protected String getStatus(FishSpec spec) {
            if (FishLog.isCaught(spec.id)) return "landed";

            //a table row with nowhere to be is a data problem, and dev mode is where it gets caught
            if (Global.getSettings().isDevMode() && spec.regions.isEmpty()) return "no location data";

            return "region data";
        }

        /**
         * Both controls are polled rather than listened to - a text field has no change callback
         * at all. A checkbox change rebuilds on the spot; the search waits until the text has sat
         * still, since the rebuild takes the keyboard focus with it.
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

            if (raritiesChanged || searchSettled) build();
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
