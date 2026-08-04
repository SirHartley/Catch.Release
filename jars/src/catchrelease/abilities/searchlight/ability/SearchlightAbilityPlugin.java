package catchrelease.abilities.searchlight.ability;

import catchrelease.helper.math.CircularArc;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.abilities.searchlight.rendering.SearchlightImpressionRenderer;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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

    public static float DETECTABILITY_PERCENT = 100f;

    public static float SPOOL_UP_TIME = 1.5f; //seconds
    public static float SEARCHLIGHT_ACTIVATION_PAUSE = 1f;

    /**
     * Degrees of the forward light's wedge, measured across the fleet's nose.
     * <p>
     * Narrow on purpose. It is there so the water the fleet is about to cross is always lit, and a
     * wide one wanders off the bow for most of its sweep, which is the thing it exists not to do.
     */
    public static float FORWARD_ARC = 50f;

    private float timePassed = 0f;
    private int lightsToActivate = 0;
    private boolean spoolDone = false;

    private List<Searchlight> activeSearchlights = new ArrayList<>();
    private List<CircularArc> searchlightArcs = new ArrayList<>();

    private SearchlightImpressionRenderer impressionRenderer;

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

        //every angle here is measured from the fleet's nose rather than from due east - see
        //Searchlight.getHeading(), which is what turns these into places

        //one light keeps the water ahead, on a narrow wedge, because ahead is where the fleet is
        //about to be and an even carve-up left the front covered only when a sweep happened to
        //swing past it
        searchlightArcs.add(new CircularArc(getFleet().getLocation(), radius,
                -FORWARD_ARC * 0.5f, FORWARD_ARC * 0.5f));

        //the rest share what is left of the circle behind it
        int remaining = lightsToActivate - 1;
        if (remaining <= 0) return;

        float areaPerLight = (360f - FORWARD_ARC) / remaining;

        for (int i = 0; i < remaining; i++) {
            float minAngle = FORWARD_ARC * 0.5f + areaPerLight * i;

            searchlightArcs.add(new CircularArc(getFleet().getLocation(), radius,
                    minAngle, minAngle + areaPerLight));
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
        }

        fleet.getStats().getDetectedRangeMod().modifyPercent(getModId(), DETECTABILITY_PERCENT * level, "Searchlights");

        if (level <= 0) {
            cleanupImpl();
        }
    }

    private void addSearchlight(){
        Searchlight searchlight = new Searchlight();

        //the arcs keep a direct reference to the fleets movement vector, and the fleet itself for
        //the heading they are measured from. Taken from the front of the list so they light up in
        //the order they were laid out - the forward one first, since it is the one worth having
        searchlight.init(searchlightArcs.get(0), getFleet());
        searchlightArcs.remove(0);

        getFleet().addScript(searchlight);
        activeSearchlights.add(searchlight);
    }

    private void expireLights(boolean withFade){
        for (Searchlight searchlight : activeSearchlights) searchlight.expire(withFade);
    }

    /**
     * How many lights sweep at once.
     * <p>
     * The upgrade rather than a fixed three. Eventually this is meant to depend on what the fleet
     * has mounted; until then it is bought, and the fallback is what it always was.
     */
    public int getSearchlightNum(){
        return Math.max(1, Math.round(UpgradeManager.getValue(StatIds.SEARCHLIGHT_COUNT, 3f)));
    }

    @Override
    protected void deactivateImpl() {
        timePassed = 0f;
        lightsToActivate = 0;
        spoolDone = false;

        CampaignFleetAPI fleet = getFleet();
        boolean withFade = fleet != null && !fleet.getContainingLocation().isHyperspace();

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