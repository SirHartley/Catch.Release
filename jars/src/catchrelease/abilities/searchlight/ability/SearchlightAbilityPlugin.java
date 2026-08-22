package catchrelease.abilities.searchlight.ability;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
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
import com.fs.starfarer.api.campaign.LocationAPI;
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

public class SearchlightAbilityPlugin extends BaseToggleAbility {

    public static final String ABILITY_ID = "catchrelease_searchlights";
    public static float DETECTABILITY_PERCENT = 100f;
    public static float SPOOL_UP_TIME = 1.5f;
    public static float SEARCHLIGHT_ACTIVATION_PAUSE = 1f;
    public static final float SLOW_LINGER = 0.5f;

    private float timePassed = 0f;
    private int lightsToActivate = 0;
    private boolean spoolDone = false;
    private List<Searchlight> activeSearchlights = new ArrayList<>();
    private List<CircularArc> searchlightArcs = new ArrayList<>();
    private transient SearchlightImpressionRenderer impressionRenderer;
    private transient LocationAPI activationLocation;

    @Override
    protected Object readResolve() {
        super.readResolve();
        ensureCollections();

        impressionRenderer = null;
        activationLocation = null;

        return this;
    }

    protected void ensureCollections() {
        if (activeSearchlights == null) activeSearchlights = new ArrayList<>();
        if (searchlightArcs == null) searchlightArcs = new ArrayList<>();
    }

    public static boolean isLit(SectorEntityToken mote) {
        SearchlightImpressionRenderer renderer = getImpressions();

        return renderer != null && mote != null && renderer.getMarkStrength(mote) > 0f;
    }

    public static boolean isDetected(SectorEntityToken mote) {
        SearchlightImpressionRenderer renderer = getImpressions();

        return renderer != null && mote != null && renderer.getDentStrength(mote) > 0f;
    }

    protected static SearchlightImpressionRenderer getImpressions() {
        CampaignFleetAPI fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (fleet == null) return null;

        AbilityPlugin ability = fleet.getAbility(ABILITY_ID);
        if (!(ability instanceof SearchlightAbilityPlugin)) return null;

        return ((SearchlightAbilityPlugin) ability).impressionRenderer;
    }

    @Override
    protected void activateImpl() {
        teardownRuntime(false);

        CampaignFleetAPI fleet = getFleet();
        if (fleet == null || fleet.getContainingLocation() == null) return;

        activationLocation = fleet.getContainingLocation();
        timePassed = 0f;
        lightsToActivate = getSearchlightNum();
        spoolDone = false;

        ensureImpressionRenderer();

        float size = Searchlight.getArea();
        float radius = size * 2f;

        // even share of the full circle per light, in world degrees (arcs don't track fleet heading)
        float areaPerLight = 360f / lightsToActivate;

        for (int i = 0; i < lightsToActivate; i++) {
            float minAngle = areaPerLight * i;

            searchlightArcs.add(new CircularArc(fleet.getLocation(), radius,
                    minAngle, minAngle + areaPerLight));
        }
    }

    @Override
    protected void applyEffect(float amount, float level) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) {
            if (level <= 0f) teardownRuntime(false);
            return;
        }

        ensureCollections();

        if (level <= 0f || !isActive()) {
            unapplyFleetEffect(fleet);
            return;
        }

        if (activationLocation == null) activationLocation = fleet.getContainingLocation();
        bindActiveLights(fleet);
        ensureImpressionRenderer();

        if (!isRuntimeCurrent() || !canRunHere(fleet)) {
            deactivate();
            return;
        }

        timePassed += amount;

        if (!spoolDone && timePassed > SPOOL_UP_TIME){
            timePassed = 0;
            spoolDone = true;
        }

        if (spoolDone && lightsToActivate > 0 && timePassed > SEARCHLIGHT_ACTIVATION_PAUSE){
            if (addSearchlight()) {
                lightsToActivate--;
            } else {
                // A partial/old save can disagree about pending count and arcs. Stop cleanly rather than throwing every frame while already-created renderers remain alive.
                lightsToActivate = 0;
            }
            timePassed = 0f;
        }

        applyBeamSlow(fleet);

        fleet.getStats().getDetectedRangeMod().modifyPercent(getModId(), DETECTABILITY_PERCENT * level, "Breach lamps");
    }

    protected void ensureImpressionRenderer() {
        if (activationLocation == null) return;

        if (impressionRenderer == null || impressionRenderer.isExpired()) {
            impressionRenderer = new SearchlightImpressionRenderer(activeSearchlights,
                    this, activationLocation);
        }

        if (!LunaCampaignRenderer.hasRenderer(impressionRenderer)) {
            LunaCampaignRenderer.addTransientRenderer(impressionRenderer);
        }
    }

    protected void bindActiveLights(CampaignFleetAPI fleet) {
        activeSearchlights.removeIf(light -> light == null || light.isDone());
        for (Searchlight light : activeSearchlights) {
            light.bindOwner(this, fleet, activationLocation);
        }
    }

    protected void applyBeamSlow(CampaignFleetAPI fleet) {
        float slow = UpgradeManager.getValue(StatIds.SEARCHLIGHT_SLOW, 0f);
        if (slow <= 0f || activeSearchlights.isEmpty()) return;
        if (fleet.getContainingLocation() == null) return;

        for (SectorEntityToken mote : fleet.getContainingLocation()
                .getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (mote.isExpired()) continue;
            if (!(mote.getCustomPlugin() instanceof FishEntityPlugin fish)) continue;

            boolean lit = false;
            for (Searchlight light : activeSearchlights) {
                if (light.isDone()) continue;

                if (light.getLitStrength(mote.getLocation()) > 0f) {
                    lit = true;
                    break;
                }
            }

            if (lit) fish.applyBlast(0f, slow, SLOW_LINGER);
        }
    }

    private boolean addSearchlight(){
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null || activationLocation == null || searchlightArcs.isEmpty()) return false;

        Searchlight searchlight = new Searchlight();
        CircularArc arc = searchlightArcs.remove(0);

        activeSearchlights.add(searchlight);
        searchlight.init(arc, this, fleet, activationLocation);
        fleet.addScript(searchlight);

        return true;
    }

    public static boolean canRunHere(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getContainingLocation() == null) return false;
        if (fleet.getContainingLocation().isHyperspace()) return false;

        return !isNearActivePond(fleet);
    }

    public static boolean isNearActivePond(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getContainingLocation() == null) return false;

        for (SectorEntityToken pond : fleet.getContainingLocation()
                .getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID)) {
            MaskedFishingPondTerrainPlugin plugin = MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
            if (plugin == null || !plugin.isActive()) continue;

            if (Misc.getDistance(pond, fleet)
                    < pond.getRadius() * PondConstants.POND_INTERACT_RANGE_MULT) {
                return true;
            }
        }

        return false;
    }

    public static boolean isBreaching() {
        CampaignFleetAPI fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (fleet == null) return false;

        AbilityPlugin ability = fleet.getAbility(ABILITY_ID);

        return ability instanceof SearchlightAbilityPlugin && ((SearchlightAbilityPlugin) ability).isActive();
    }

    private void expireLights(boolean withFade){
        for (Searchlight searchlight : activeSearchlights) {
            if (searchlight != null) searchlight.expire(withFade);
        }
    }

    public boolean owns(Searchlight light) {
        return light != null && activeSearchlights != null && activeSearchlights.contains(light);
    }

    public void recoverRuntimeLocation(LocationAPI location) {
        if (activationLocation == null) activationLocation = location;
    }

    public boolean isRuntimeCurrent() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null || activationLocation == null || Global.getSector() == null) return false;

        return isActive()
                && fleet.getAbility(ABILITY_ID) == this
                && fleet.getContainingLocation() == activationLocation
                && Global.getSector().getCurrentLocation() == activationLocation
                && canRunHere(fleet);
    }

    public int getSearchlightNum(){
        return Math.max(1, Math.round(UpgradeManager.getValue(StatIds.SEARCHLIGHT_COUNT, 2f)));
    }

    @Override
    protected void deactivateImpl() {
        // Fade only if the activation is still in the place that owns its coordinates. A location handoff must retire the global renderers immediately.
        CampaignFleetAPI fleet = getFleet();
        boolean withFade = fleet != null && activationLocation != null
                && fleet.getContainingLocation() == activationLocation
                && !fleet.getContainingLocation().isHyperspace();

        teardownRuntime(withFade);
        unapplyFleetEffect(fleet);
    }

    @Override
    protected void cleanupImpl() {
        CampaignFleetAPI fleet = getFleet();
        teardownRuntime(false);
        unapplyFleetEffect(fleet);
    }

    protected void teardownRuntime(boolean withFade) {
        ensureCollections();

        timePassed = 0f;
        lightsToActivate = 0;
        spoolDone = false;

        expireLights(withFade);
        activeSearchlights.clear();
        searchlightArcs.clear();

        if (impressionRenderer != null) {
            impressionRenderer.fadeAndExpire(withFade ? 1f : 0f);
            impressionRenderer = null;
        }

        activationLocation = null;
    }

    protected void unapplyFleetEffect(CampaignFleetAPI fleet) {
        if (fleet == null) return;

        fleet.getStats().getDetectedRangeMod().unmodify(getModId());
    }

    @Override
    public boolean showProgressIndicator() {
        return super.showProgressIndicator();
    }

    @Override
    public boolean showActiveIndicator() {
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
            LabelAPI title = tooltip.addTitle("Breach Lamps" + status);
            title.highlightLast(status);
            title.setHighlightColor(gray);
        } else {
            tooltip.addSpacer(-10f);
        }

        float pad = 10f;

        tooltip.addPara("Toggle the breach lamps installed on fishing trawlers.", pad);

        tooltip.addPara("Each lamp burns a window through the fabric as it sweeps. Whatever swims"
                        + " under one is %s and can be harpooned before the mark fades."
                        + " The severe radiation increases the range at which the fleet can be"
                        + " detected by %s.", pad,
                highlight,
                "exposed",
                "" + (int)(DETECTABILITY_PERCENT) + "%"
        );

        tooltip.addPara("The lamps will not run beside an open pond rupture - that one is the"
                + " R.O.D.'s.", Misc.getGrayColor(), pad);

        addUpgradesToTooltip(tooltip, pad);

        addIncompatibleToTooltip(tooltip, expanded);
    }

    protected void addUpgradesToTooltip(TooltipMakerAPI tooltip, float pad) {
        Color highlight = Misc.getHighlightColor();

        int lights = getSearchlightNum();
        tooltip.addPara("Sweeping with %s, each reaching %s.", pad, highlight,
                lights == 1 ? "one lamp" : lights + " lamps",
                (int) Searchlight.getArea() + " units");

        float detect = UpgradeManager.getValue(StatIds.SEARCHLIGHT_DETECT_RADIUS,
                FishConstants.IMPRESSION_DETECT_FALLBACK);
        if (detect > 0f) {
            tooltip.addPara("The fabric bruises within %s of a beam, betraying anything under it"
                    + " as a dent.", 3f, highlight, (int) detect + " units");
        }

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

        float rare = UpgradeManager.getValue(StatIds.SEARCHLIGHT_RARE_CHANCE, 0f);
        if (rare > 0f) {
            tooltip.addPara("Rarer species are more likely to be down there to begin with.",
                    Misc.getGrayColor(), 3f);
        }

        float slow = UpgradeManager.getValue(StatIds.SEARCHLIGHT_SLOW, 0f);
        if (slow > 0f) {
            tooltip.addPara("The light itself drags: anything swimming through a beam is slowed"
                    + " by %s.", 3f, highlight, Math.round(slow * 100f) + "%");
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
