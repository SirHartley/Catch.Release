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
     * Whether the player's lights currently mark this mote as found. State lives in the impression
     * renderer, not on the mote itself. False if the rig is off, unfitted, or never swept over it.
     */
    public static boolean isLit(SectorEntityToken mote) {
        SearchlightImpressionRenderer renderer = getImpressions();

        return renderer != null && mote != null && renderer.getMarkStrength(mote) > 0f;
    }

    /**
     * Whether this mote is showing at all - lit by a beam, or merely detected within passive range.
     * Weaker than {@link #isLit}: detected motes are visible but not strikeable by an ordinary
     * harpoon head.
     */
    public static boolean isDetected(SectorEntityToken mote) {
        SearchlightImpressionRenderer renderer = getImpressions();

        return renderer != null && mote != null && renderer.getDentStrength(mote) > 0f;
    }

    /** The live map of what the lights have made of the dark, or null while they are off. */
    protected static SearchlightImpressionRenderer getImpressions() {
        CampaignFleetAPI fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (fleet == null) return null;

        AbilityPlugin ability = fleet.getAbility(ABILITY_ID);
        if (!(ability instanceof SearchlightAbilityPlugin)) return null;

        return ((SearchlightAbilityPlugin) ability).impressionRenderer;
    }

    @Override
    protected void activateImpl() {
        timePassed = 0f;
        lightsToActivate = getSearchlightNum();
        spoolDone = false;
        activeSearchlights.clear();
        searchlightArcs.clear();

        //one shared renderer for all dents, avoiding double-depth dents where beams cross; a renderer
        //still fading from a previous toggle is force-expired first
        if (impressionRenderer != null) impressionRenderer.fadeAndExpire(0f);
        impressionRenderer = new SearchlightImpressionRenderer(activeSearchlights);
        LunaCampaignRenderer.addTransientRenderer(impressionRenderer);

        float size = Searchlight.getArea();
        float radius = size * 2f;

        //even share of the full circle per light, in world degrees (arcs don't track fleet heading)
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

        //raw frame time: the old campaignSpeedupMult division meant to steady the animation
        //under speed-up, but it applied at normal speed too, running every wait at double
        //length - the spool read as taking forever, worst on the fan, which lights nothing
        //until it ends. A spool that runs brisker under speed-up is fine; one that drags at
        //normal speed is not
        timePassed += amount;

        if (!spoolDone && timePassed > SPOOL_UP_TIME){
            timePassed = 0;
            spoolDone = true;
        }

        if (spoolDone & lightsToActivate > 0 && timePassed > SEARCHLIGHT_ACTIVATION_PAUSE){
            addSearchlight();
            lightsToActivate--;
            timePassed = 0f;
        }

        applyBeamSlow(fleet);

        fleet.getStats().getDetectedRangeMod().modifyPercent(getModId(), DETECTABILITY_PERCENT * level, "Breach lamps");

        if (level <= 0) {
            cleanupImpl();
        }
    }

    /** Seconds the drag lingers once a mote has left the light, easing off rather than snapping. */
    public static final float SLOW_LINGER = 0.5f;

    /**
     * Slow upgrade: drags motes swimming through active beams, applied via the mote's blast-knock
     * and refreshed each frame with a short linger on exit. No-op until bought (strength 0).
     */
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

    private void addSearchlight(){
        Searchlight searchlight = new Searchlight();

        //arcs hold a direct reference to the fleet's location vector, so lights track the fleet
        //automatically; taken front-first for a consistent activation order
        searchlight.init(searchlightArcs.get(0));
        searchlightArcs.remove(0);

        getFleet().addScript(searchlight);
        activeSearchlights.add(searchlight);
    }

    /**
     * Whether the lamps can run here: not in hyperspace (nothing to fish through from the far side),
     * and not beside an open pond rupture (that's the R.O.D.'s water).
     */
    public static boolean canRunHere(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getContainingLocation() == null) return true;
        if (fleet.getContainingLocation().isHyperspace()) return false;

        return !isNearActivePond(fleet);
    }

    /** Whether an open rupture is within its own interaction range - the range the rod works at,
     * so the lamps yield exactly where the rod takes over. */
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

    /**
     * Whether any lamp windows are currently open (i.e. the ability is active). Mutually exclusive
     * with an open pond, since {@link #canRunHere(CampaignFleetAPI)} already keeps the lamps off near one.
     */
    public static boolean isBreaching() {
        CampaignFleetAPI fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (fleet == null) return false;

        AbilityPlugin ability = fleet.getAbility(ABILITY_ID);

        return ability instanceof SearchlightAbilityPlugin && ((SearchlightAbilityPlugin) ability).isActive();
    }

    private void expireLights(boolean withFade){
        for (Searchlight searchlight : activeSearchlights) searchlight.expire(withFade);
    }

    /**
     * Number of simultaneous lights; starts at 2, upgradeable. Intended to eventually depend on
     * fleet loadout; currently purely a purchased stat.
     */
    public int getSearchlightNum(){
        return Math.max(1, Math.round(UpgradeManager.getValue(StatIds.SEARCHLIGHT_COUNT, 2f)));
    }

    @Override
    protected void deactivateImpl() {
        timePassed = 0f;
        lightsToActivate = 0;
        spoolDone = false;

        //fade skipped only in hyperspace, where nothing should linger drawing; deactivating near a
        //pond still gets the normal spool-down
        CampaignFleetAPI fleet = getFleet();
        boolean withFade = fleet != null && fleet.getContainingLocation() != null
                && !fleet.getContainingLocation().isHyperspace();

        expireLights(withFade);
        activeSearchlights.clear();

        //dents fade on the same terms as the lights - instantly in hyperspace
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

        tooltip.addPara("The lamps will not run beside an open pond rupture - that water is the"
                + " R.O.D.'s.", Misc.getGrayColor(), pad);

        addUpgradesToTooltip(tooltip, pad);

        addIncompatibleToTooltip(tooltip, expanded);
    }

    /**
     * Lists only upgrades actually purchased; an unupgraded rig shows nothing rather than a list of
     * zeroes.
     */
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