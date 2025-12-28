package catchrelease.abilities.searchlight.ability;

import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.abilities.searchlight.scripts.SearchAreaProfile;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

//todo make sure to manually disable this if the player upgrades it

public class SearchlightAbilityPlugin extends BaseToggleAbility {

    public static float DETECTABILITY_PERCENT = 100f;

    public static float SPOOL_UP_TIME = 1.5f; //seconds
    public static float SEARCHLIGHT_ACTIVATION_PAUSE = 1f;

    private float timePassed = 0f;
    private int lightsToActivate = 0;
    private boolean spoolDone = false;

    private List<Searchlight> activeSearchlights = new ArrayList<>();
    private List<SearchAreaProfile> profiles = new ArrayList<>();

    @Override
    protected void activateImpl() {
        timePassed = 0f;
        lightsToActivate = getSearchlightNum();
        spoolDone = false;
        activeSearchlights.clear();
        profiles.clear();

        float areaPerLight = 360f / lightsToActivate;

        for (int i = 1; i <= lightsToActivate; i++){
            float size = UpgradeManager.getInstance().getCurrentValue(StatIds.SEARCHLIGHT_AREA);

            float minAngle = areaPerLight * (i - 1);
            float maxAngle = areaPerLight * i;

            profiles.add(new SearchAreaProfile(minAngle, maxAngle, size, size * 6));
        }
    }

    @Override
    protected void applyEffect(float amount, float level) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        if (level > 0 && fleet.getContainingLocation() != null && fleet.getContainingLocation().isHyperspace()) {
            deactivate();
            return;
        }

        float mult = Global.getSettings().getFloat("campaignSpeedupMult"); //anim independent of speed up
        timePassed += amount / mult;

        //animation and startup
        if (!spoolDone && timePassed > SPOOL_UP_TIME){
            timePassed = 0;
            spoolDone = true;
        }

        if (spoolDone & lightsToActivate > 0 && timePassed > SEARCHLIGHT_ACTIVATION_PAUSE){
            addSearchlight();
            lightsToActivate--;
            timePassed = 0f;

            Global.getSoundPlayer().playUISound("catchrelease_ui_searchlight_toggle", 0.8f, 0.8f);
        }

        fleet.getStats().getDetectedRangeMod().modifyPercent(getModId(), DETECTABILITY_PERCENT * level, "Searchlights");

        if (level <= 0) {
            cleanupImpl();
        }
    }

    private void addSearchlight(){
        Searchlight searchlight = new Searchlight(getFleet());

        searchlight.init(profiles.get(profiles.size()-1));
        profiles.remove(profiles.size()-1);

        getFleet().addScript(searchlight);
        activeSearchlights.add(searchlight);
    }

    private void expireLights(boolean withFade){
        for (Searchlight searchlight : activeSearchlights) searchlight.expire(withFade);
    }

    public int getSearchlightNum(){
        //Searchlight amount depends on ships in fleet with searchlights mounted
        //could do modular weps

        return 3; //todo adjust later with actual num
    }

    @Override
    protected void deactivateImpl() {
        timePassed = 0f;
        lightsToActivate = 0;
        spoolDone = false;

        CampaignFleetAPI fleet = getFleet();
        expireLights(fleet != null && !fleet.getContainingLocation().isHyperspace());
        activeSearchlights.clear();

        cleanupImpl();
    }

    @Override
    protected void cleanupImpl() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        fleet.getStats().getDetectedRangeMod().unmodify(getModId());
    }

    @Override
    public boolean showProgressIndicator() {
        return super.showProgressIndicator();
        //return false;
    }

    @Override
    public boolean showActiveIndicator() {
        //super.showActiveIndicator()
        return isActive();
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();

        String status = " (off)";
        if (turnedOn) {
            status = " (on)";
        }

        if (!Global.CODEX_TOOLTIP_MODE) {
            LabelAPI title = tooltip.addTitle("Search Lights" + status);
            title.highlightLast(status);
            title.setHighlightColor(gray);
        } else {
            tooltip.addSpacer(-10f);
        }

        float pad = 10f;


        tooltip.addPara("Toggle the search lights installed on fishing trawlers.", pad);


        tooltip.addPara("Hyperspace motes will be drawn to, and made visible by these lights across dimensions. Use %s and harpoon them for a quick catch." +
                        "The severe radiation increases the range at which the fleet can be detected by %s.", pad,
                highlight,
                "dive bombs",
                "" + (int)(DETECTABILITY_PERCENT) + "%"
        );

        tooltip.addPara("TODO: Upgrade information", pad
        );

        addIncompatibleToTooltip(tooltip, expanded);
    }

    public boolean isUsable() {
        if (!super.isUsable()) return false;
        if (getFleet() == null) return false;

        CampaignFleetAPI fleet = getFleet();

        if (!fleet.isAIMode() &&
                fleet.getContainingLocation() != null && fleet.getContainingLocation().isHyperspace()) {
            return false;
        }

        return true;
    }

    public boolean hasTooltip() {
        return true;
    }

    @Override
    public void fleetLeftBattle(BattleAPI battle, boolean engagedInHostilities) {
    }


    @Override
    public void fleetJoinedBattle(BattleAPI battle) {
        if (!battle.isPlayerInvolved()) {
            deactivate();
        }
    }
}