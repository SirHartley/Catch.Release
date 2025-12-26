package catchrelease.campaign.searchlight.ability;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberViewAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class SearchlightAbilityPlugin extends BaseToggleAbility {

    public static float DETECTABILITY_PERCENT = 100f;

    private float timePassed = 0f;
    private int cycle = 0;

    @Override
    protected void activateImpl() {
        
    }

    @Override
    protected void applyEffect(float amount, float level) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        if (level > 0 && fleet.getContainingLocation() != null && fleet.getContainingLocation().isHyperspace()) {
            deactivate();
            return;
        }

        //3 second spool up
        //then activate search lights across 3 seconds, split tme to activate equally between num of lights so each one has a pause
        //search lights search around fleet, never overlapping in search area. If one would overlap in its search path, it instead changes direction smoothly


        timePassed += amount;
        if (cycle == 0 && timePassed > 3) {
            cycle++;
            timePassed = 0f;
        }

        if (cycle >= 1 && cycle <= 3 && timePassed > 2){
            Global.getSoundPlayer().playUISound("catchrelease_ui_searchlight_toggle", 0.8f, 0.8f);
            timePassed = 0;
            cycle++;
        }

        if (level > 0 && level < 1 && amount > 0) {
            //for wind up
            return;
        }

        fleet.getStats().getDetectedRangeMod().modifyPercent(getModId(), DETECTABILITY_PERCENT * level, "Searchlights");

        if (level <= 0) {
            cleanupImpl();
        }
    }

    public int getSearchlightNum(){
        return 6; //todo adjust later with actual num
    }

    @Override
    protected void deactivateImpl() {
        cleanupImpl();
    }

    @Override
    protected void cleanupImpl() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        timePassed = 0;
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