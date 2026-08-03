package catchrelease.campaign.fish.intel;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishRarity;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Set;

/**
 * Where things live, as an intel entry.
 * <p>
 * A holding pen rather than a home - this belongs in a screen of its own eventually, and is an intel
 * entry now because that is the cheapest place to put a large custom panel that the player can
 * actually reach.
 * <p>
 * The map is {@link FishMapPanel}; this is the frame around it and the filters down the side.
 */
public class FishMapIntel extends BaseIntelPlugin {

    public static final String TAG = "catchrelease_fish_map";

    protected transient FishMapPanel.FishMapFilter filter = new FishMapPanel.FishMapFilter();
    protected transient TextFieldAPI searchField;
    protected transient java.util.Map<FishRarity, ButtonAPI> rarityBoxes = new java.util.LinkedHashMap<>();

    /** Installed once per campaign. Idempotent, so calling it on every load is safe. */
    public static void register() {
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(FishMapIntel.class)) {
            if (intel != null) return;
        }

        FishMapIntel added = new FishMapIntel();
        Global.getSector().getIntelManager().addIntel(added, true);
        added.setImportant(false);
    }

    protected FishMapPanel.FishMapFilter getFilter() {
        if (filter == null) filter = new FishMapPanel.FishMapFilter();

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

    /**
     * The map on the left and the filters on the right.
     * <p>
     * The filters are real UI elements rather than drawn ones, because a search box has to take the
     * keyboard and a checkbox has to take a click, and neither is worth reimplementing.
     */
    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float pad = 10f;
        float sidebar = FishConstants.MAP_SIDEBAR_WIDTH;

        float mapWidth = width - sidebar - pad;

        CustomPanelAPI map = Global.getSettings().createCustom(
                mapWidth, height, new FishMapPanel(getFilter()));

        panel.addComponent(map).inTL(0f, 0f);

        TooltipMakerAPI side = panel.createUIElement(sidebar, height, true);
        buildSidebar(side, sidebar);

        panel.addUIElement(side).inTL(mapWidth + pad, 0f);
    }

    protected void buildSidebar(TooltipMakerAPI side, float width) {
        float pad = 10f;

        //rebuilt every time the panel is opened, so the old handles are worth nothing
        rarityBoxes = new java.util.LinkedHashMap<>();

        side.addSectionHeading("Filters", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                com.fs.starfarer.api.ui.Alignment.MID, 0f);

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

        side.addSectionHeading("Reading it", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                com.fs.starfarer.api.ui.Alignment.MID, pad);

        side.addPara("An icon on a system is somewhere one was actually landed.", Misc.getGrayColor(), pad);
        side.addPara("A shaded area is a species you have location data on but have never caught -"
                + " the data says the region, not the system.", Misc.getGrayColor(), 3f);

        if (Global.getSettings().isDevMode()) {
            side.addPara("Dev mode: everything in the table is shown, caught or not.",
                    Misc.getHighlightColor(), pad);
        }
    }

    /**
     * Both controls are polled rather than listened to.
     * <p>
     * An intel plugin has no button callback of its own - buttons inside one are routed to the
     * panel, not to the plugin - and a text field has no change callback at all. Reading both once a
     * frame is the whole of what is needed, and it keeps the filter as the single place the state
     * lives: the map reads it every frame, so there is nothing to refresh.
     */
    @Override
    protected void advanceImpl(float amount) {
        if (searchField != null) {
            String text = searchField.getText();
            if (text != null && !text.equals(getFilter().search)) getFilter().search = text;
        }

        for (java.util.Map.Entry<FishRarity, ButtonAPI> entry : rarityBoxes.entrySet()) {
            if (entry.getValue() == null) continue;

            if (entry.getValue().isChecked()) getFilter().rarities.add(entry.getKey());
            else getFilter().rarities.remove(entry.getKey());
        }
    }

    @Override
    public Color getTitleColor(ListInfoMode mode) {
        return Misc.getBasePlayerColor();
    }
}
