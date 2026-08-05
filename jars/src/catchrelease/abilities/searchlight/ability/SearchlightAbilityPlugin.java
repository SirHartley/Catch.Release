package catchrelease.abilities.searchlight.ability;

import catchrelease.helper.math.CircularArc;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.abilities.searchlight.rendering.SearchlightImpressionRenderer;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

//todo make sure to manually disable this if the player upgrades it

public class SearchlightAbilityPlugin extends BaseToggleAbility {

    /** As the ability is keyed in data/campaign/abilities.csv, for looking the rig up off a fleet. */
    public static final String ABILITY_ID = "catchrelease_searchlights";

    public static float DETECTABILITY_PERCENT = 100f;

    public static float SPOOL_UP_TIME = 1.5f; //seconds
    public static float SEARCHLIGHT_ACTIVATION_PAUSE = 1f;

    private float timePassed = 0f;
    private int lightsToActivate = 0;
    private boolean spoolDone = false;

    private List<Searchlight> activeSearchlights = new ArrayList<>();
    private List<CircularArc> searchlightArcs = new ArrayList<>();

    private SearchlightImpressionRenderer impressionRenderer;

    /**
     * Whether the player's lights have this one, and have not yet forgotten it.
     * <p>
     * The only way to ask. What the lights have found is held in the impression renderer's own map
     * rather than on the motes, because being found is a property of having looked - so anything
     * that wants to act on it has to come through the ability that owns the renderer. False with
     * the rig off, not fitted, or simply never having swept over the thing being asked about.
     */
    public static boolean isLit(SectorEntityToken mote) {
        CampaignFleetAPI fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (fleet == null || mote == null) return false;

        AbilityPlugin ability = fleet.getAbility(ABILITY_ID);
        if (!(ability instanceof SearchlightAbilityPlugin)) return false;

        SearchlightImpressionRenderer renderer = ((SearchlightAbilityPlugin) ability).impressionRenderer;

        return renderer != null && renderer.getMarkStrength(mote) > 0f;
    }

    @Override
    protected void activateImpl() {
        timePassed = 0f;
        lightsToActivate = getSearchlightNum();
        spoolDone = false;
        activeSearchlights.clear();
        searchlightArcs.clear();

        //one renderer for all the dents, made alongside the lights rather than inside one of them:
        //drawn per light, a mote under two crossing beams was dented twice, at double the depth.
        //It holds the live list, so lights arriving on the activation stagger are its problem.
        //An old one still fading from the last toggle goes now - it would draw these same dents
        //under the new one until its second ran out
        if (impressionRenderer != null) impressionRenderer.fadeAndExpire(0f);
        impressionRenderer = new SearchlightImpressionRenderer(activeSearchlights);
        LunaCampaignRenderer.addTransientRenderer(impressionRenderer);

        float size = Searchlight.getArea();
        float radius = size * 2f;

        //an even share of the circle each, in the world's own degrees. A wedge reserved for straight
        //ahead went with the heading it was measured from: with the arcs no longer turning with the
        //hull there is no ahead to keep clear, and the light that used to hold it just swept a
        //narrow strip of due east forever
        float areaPerLight = 360f / lightsToActivate;

        for (int i = 0; i < lightsToActivate; i++) {
            float minAngle = areaPerLight * i;

            searchlightArcs.add(new CircularArc(getFleet().getLocation(), radius,
                    minAngle, minAngle + areaPerLight));
        }
    }

    @Override
    protected void applyEffect(float amount, float level) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        if (level > 0 && !canRunHere(fleet)) {
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
        }

        fleet.getStats().getDetectedRangeMod().modifyPercent(getModId(), DETECTABILITY_PERCENT * level, "Searchlights");

        if (level <= 0) {
            cleanupImpl();
        }
    }

    private void addSearchlight(){
        Searchlight searchlight = new Searchlight();

        //the arcs keep a direct reference to the fleet's own location vector, which is how a light
        //travels with the fleet without being told to. Taken from the front so they come on in the
        //order they were laid out, going round rather than lighting up at random
        searchlight.init(searchlightArcs.get(0));
        searchlightArcs.remove(0);

        getFleet().addScript(searchlight);
        activeSearchlights.add(searchlight);
    }

    /**
     * Whether the lights will run where the fleet is standing.
     * <p>
     * They will not go into hyperspace on their own - there is nothing out there for an ordinary
     * beam to find, and lighting up the deep for no reason is only a way to be seen. Bought, the
     * rig burns through instead of shining across, which is what makes the trip worth making.
     */
    public static boolean canRunHere(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getContainingLocation() == null) return true;
        if (!fleet.getContainingLocation().isHyperspace()) return true;

        return burnsIntoHyperspace();
    }

    /** Whether the burn-through has been fitted. */
    public static boolean burnsIntoHyperspace() {
        return TackleManager.get(Tackle.Fit.SEARCHLIGHT).burnsHyperspace;
    }

    private void expireLights(boolean withFade){
        for (Searchlight searchlight : activeSearchlights) searchlight.expire(withFade);
    }

    /**
     * How many lights sweep at once.
     * <p>
     * Two to start with, and bought upward from there. One was too little to read as a sweep at all
     * - a single beam crawling round a whole circle spends most of its time somewhere the player is
     * not looking - and two smaller ones working opposite halves cover the same ground while always
     * having something in view. Eventually this is meant to depend on what the fleet has mounted;
     * until then it is bought, and the fallback matches the sheet.
     */
    public int getSearchlightNum(){
        return Math.max(1, Math.round(UpgradeManager.getValue(StatIds.SEARCHLIGHT_COUNT, 2f)));
    }

    @Override
    protected void deactivateImpl() {
        timePassed = 0f;
        lightsToActivate = 0;
        spoolDone = false;

        CampaignFleetAPI fleet = getFleet();
        boolean withFade = fleet != null && canRunHere(fleet);

        expireLights(withFade);
        activeSearchlights.clear();

        //the dents go on the lights' own terms - and like them, immediately when the fleet is in
        //hyperspace, so nothing of this is left drawing out there
        if (impressionRenderer != null) {
            impressionRenderer.fadeAndExpire(withFade ? 1f : 0f);
            impressionRenderer = null;
        }

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

        addUpgradesToTooltip(tooltip, pad);

        addIncompatibleToTooltip(tooltip, expanded);
    }

    /**
     * What has been fitted, and only what has been fitted.
     * <p>
     * A rig with nothing bought says nothing here rather than listing four zeroes - the shop is
     * where you go to find out what is for sale, and a tooltip that reads like a price list every
     * time you hover the button is a tooltip nobody finishes reading.
     */
    protected void addUpgradesToTooltip(TooltipMakerAPI tooltip, float pad) {
        Color highlight = Misc.getHighlightColor();

        int lights = getSearchlightNum();
        tooltip.addPara("Sweeping with %s, each reaching %s.", pad, highlight,
                lights == 1 ? "one light" : lights + " lights",
                (int) Searchlight.getArea() + " units");

        float track = UpgradeManager.getValue(StatIds.SEARCHLIGHT_TRACK_TIME, 0f);
        if (track > 0f) {
            tooltip.addPara("Whatever the light passes over stays marked for %s afterwards.",
                    3f, highlight, Misc.getRoundedValue(track) + " seconds");
        }

        float lock = TackleManager.get(Tackle.Fit.SEARCHLIGHT).lockTime;
        if (lock > 0f) {
            tooltip.addPara("A light that finds something breaks off its sweep and follows it for %s.",
                    3f, highlight, Misc.getRoundedValue(lock) + " seconds");
        }

        int identify = Math.round(UpgradeManager.getValue(StatIds.SEARCHLIGHT_IDENTIFY, 0f));
        if (identify == 1) {
            tooltip.addPara("A mark carries some hint of how rare the thing under it is.",
                    Misc.getGrayColor(), 3f);
        } else if (identify > 1) {
            tooltip.addPara("A mark is coloured by exactly how rare the thing under it is.",
                    Misc.getGrayColor(), 3f);
        }

        float rare = UpgradeManager.getValue(StatIds.SEARCHLIGHT_RARE_CHANCE, 0f);
        if (rare > 0f) {
            tooltip.addPara("Rarer species are more likely to be down there to begin with.",
                    Misc.getGrayColor(), 3f);
        }
    }

    public boolean isUsable() {
        if (!super.isUsable()) return false;
        if (getFleet() == null) return false;

        CampaignFleetAPI fleet = getFleet();

        if (!fleet.isAIMode() && !canRunHere(fleet)) return false;

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