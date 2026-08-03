package catchrelease.campaign.fish.intel;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.MapParams;
import com.fs.starfarer.api.ui.MarkerData;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where things live, as an intel entry.
 * <p>
 * A holding pen rather than a home - this belongs in a screen of its own eventually, and is an intel
 * entry now because that is the cheapest place to put a large panel that the player can actually
 * reach.
 * <p>
 * The map is the game's own sector map, through {@link TooltipMakerAPI#createSectorMap}, because a
 * hand-drawn one is a diagram of the sector and the real one is the sector - names, starscape,
 * constellations, the player's own position, all for free. What this knows is said in the map's own
 * vocabulary: a marker where a specimen was actually landed, and lit systems where a species is only
 * said to live, with everything else dimmed under them.
 * <p>
 * The map takes its parameters at creation and holds them, so a filter change rebuilds the panel
 * through {@link IntelUIAPI#updateUIForItem} rather than being read live. Checkboxes rebuild on the
 * click; the search box waits until the typing has stopped, because a rebuild takes the keyboard
 * focus with it.
 */
public class FishMapIntel extends BaseIntelPlugin {

    public static final String TAG = "catchrelease_fish_map";

    /** Seconds the search text has to sit still before the map is rebuilt around it. */
    public static final float SEARCH_SETTLE = 0.75f;

    protected transient FishMapFilter filter = new FishMapFilter();
    protected transient TextFieldAPI searchField;
    protected transient Map<FishRarity, ButtonAPI> rarityBoxes = new LinkedHashMap<>();
    protected transient IntelUIAPI intelUI;

    /** What the map on screen was built from, which is how a change is recognised. */
    protected transient String builtSearch;
    protected transient Set<FishRarity> builtRarities;

    protected transient String lastSeenSearch;
    protected transient float searchStill = 0f;

    /** Installed once per campaign. Idempotent, so calling it on every load is safe. */
    public static void register() {
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(FishMapIntel.class)) {
            if (intel != null) return;
        }

        FishMapIntel added = new FishMapIntel();
        Global.getSector().getIntelManager().addIntel(added, true);
        added.setImportant(false);
    }

    protected FishMapFilter getFilter() {
        if (filter == null) filter = new FishMapFilter();

        return filter;
    }

    @Override
    public String getName() {
        return FishConstants.MAP_INTEL_TITLE;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(TAG);

        return tags;
    }

    @Override
    public String getIcon() {
        return FishConstants.CODEX_CATEGORY_ICON;
    }

    /** Never goes away: it is a reference, not an event. */
    @Override
    public boolean shouldRemoveIntel() {
        return false;
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara("Where things have been found, and where they are said to be.",
                Misc.getGrayColor(), 0f);
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public boolean hasSmallDescription() {
        return false;
    }

    /** The map on the left and the filters on the right. */
    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        intelUI = panel.getIntelUI();

        float pad = 10f;
        float sidebar = FishConstants.MAP_SIDEBAR_WIDTH;
        float mapWidth = width - sidebar - pad;

        List<FishSpec> shown = getShown();

        TooltipMakerAPI holder = panel.createUIElement(mapWidth, height, false);
        holder.createSectorMap(mapWidth, height, buildMapParams(shown, mapWidth, height),
                FishConstants.MAP_INTEL_TITLE);
        panel.addUIElement(holder).inTL(0f, 0f);

        TooltipMakerAPI side = panel.createUIElement(sidebar, height, true);
        buildSidebar(side, sidebar, shown);
        panel.addUIElement(side).inTL(mapWidth + pad, 0f);

        builtSearch = getFilter().search == null ? "" : getFilter().search;
        builtRarities = new LinkedHashSet<>(getFilter().rarities);
    }

    /**
     * What this map knows, said in the vanilla map's vocabulary. A landed species is a fact and
     * gets a marker on the exact spot, in its own rarity's colour; a species only known from bought
     * location data lights up every system its declared regions cover - a region is all a hint is,
     * and the region resolver itself decides which systems that means, so the map lights exactly
     * the systems that would qualify. Everything else dims under the shown ones.
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

    /**
     * Known about but never landed: the location is a region, not a point. Dev mode shows every
     * region the table declares, caught or not - a catch marker on top of one is not a reason to
     * hide where the species actually spawns, and checking the table is what dev mode is for.
     */
    protected boolean showsRegions(FishSpec spec, boolean caught) {
        if (Global.getSettings().isDevMode()) return !spec.regions.isEmpty();

        return !caught && FishLog.isLocationDataUnlocked(spec.id);
    }

    /**
     * The systems a set of regions covers, asked of the region resolver itself rather than of the
     * regions' geometry - which is what keeps ABYSSAL working, since that one is a property of a
     * system rather than a place on the map.
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
            if (!getFilter().accepts(spec)) continue;
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
     * The filters, and under them the species the map is currently saying something about - the
     * markers do not name themselves, so the sidebar does it for them.
     */
    protected void buildSidebar(TooltipMakerAPI side, float width, List<FishSpec> shown) {
        float pad = 10f;

        //rebuilt every time the panel is opened, so the old handles are worth nothing
        rarityBoxes = new LinkedHashMap<>();

        side.addSectionHeading("Filters", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, 0f);

        side.addPara("Search", Misc.getGrayColor(), pad);
        searchField = side.addTextField(width - 10f, 3f);
        searchField.setText(getFilter().search == null ? "" : getFilter().search);

        side.addPara("Rarity", Misc.getGrayColor(), pad);

        for (FishRarity rarity : FishRarity.values()) {
            ButtonAPI box = side.addAreaCheckbox(Misc.ucFirst(rarity.name().toLowerCase()), rarity,
                    rarity.color, Misc.getDarkPlayerColor(), rarity.color, width - 10f, 20f, 3f);

            box.setChecked(getFilter().rarities.contains(rarity));
            rarityBoxes.put(rarity, box);
        }

        side.addSectionHeading("Showing", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, pad);

        if (shown.isEmpty()) {
            side.addPara("Nothing passes the filters.", Misc.getGrayColor(), pad);
        }

        for (FishSpec spec : shown) {
            side.addPara("%s - " + getStatus(spec), 3f, spec.rarity.color, spec.getDisplayName());
        }

        side.addSectionHeading("Reading it", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, pad);

        side.addPara("A marker is a spot where one was actually landed.", Misc.getGrayColor(), pad);
        side.addPara("A lit system is somewhere a species you have location data on is said to"
                + " live - the data names the region, not the system.", Misc.getGrayColor(), 3f);

        if (Global.getSettings().isDevMode()) {
            side.addPara("Dev mode: everything in the table is shown, caught or not.",
                    Misc.getHighlightColor(), pad);
        }
    }

    protected String getStatus(FishSpec spec) {
        if (FishLog.isCaught(spec.id)) return "landed";

        //a table row with nowhere to be is a data problem, and dev mode is where it gets caught
        if (Global.getSettings().isDevMode() && spec.regions.isEmpty()) return "no location data";

        return "region data";
    }

    /**
     * Both controls are polled rather than listened to - an intel plugin has no button callback of
     * its own, and a text field has no change callback at all. A checkbox change rebuilds the panel
     * on the spot; the search waits until the text has sat still, since the rebuild takes the
     * keyboard focus with it and pulling it out from under someone mid-word is worse than a moment's
     * lag.
     */
    @Override
    protected void advanceImpl(float amount) {
        if (searchField != null) {
            String text = searchField.getText();
            if (text != null && !text.equals(getFilter().search)) getFilter().search = text;
        }

        for (Map.Entry<FishRarity, ButtonAPI> entry : rarityBoxes.entrySet()) {
            if (entry.getValue() == null) continue;

            if (entry.getValue().isChecked()) getFilter().rarities.add(entry.getKey());
            else getFilter().rarities.remove(entry.getKey());
        }

        if (builtRarities == null) return;

        String search = getFilter().search == null ? "" : getFilter().search;

        if (!search.equals(lastSeenSearch)) {
            lastSeenSearch = search;
            searchStill = 0f;
        } else {
            searchStill += amount;
        }

        boolean raritiesChanged = !builtRarities.equals(getFilter().rarities);
        boolean searchSettled = !search.equals(builtSearch) && searchStill >= SEARCH_SETTLE;

        if (raritiesChanged || searchSettled) refresh();
    }

    /**
     * Rebuilds the large description around the current filter. The UI handle can be stale - the
     * screen may have been closed since it was handed over - so a refusal costs the refresh and
     * nothing else.
     */
    protected void refresh() {
        if (intelUI == null) return;

        try {
            intelUI.updateUIForItem(this);
        } catch (Exception e) {
            Global.getLogger(FishMapIntel.class).warn("Could not rebuild the fish map", e);
            intelUI = null;
        }
    }

    @Override
    public Color getTitleColor(ListInfoMode mode) {
        return Misc.getBasePlayerColor();
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
}
