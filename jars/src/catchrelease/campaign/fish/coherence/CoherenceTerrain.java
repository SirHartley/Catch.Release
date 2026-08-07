package catchrelease.campaign.fish.coherence;

import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.items.FishItemPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.terrain.FlareManager;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * The coherence reading, as an entry in the terrain bar.
 * <p>
 * There is nowhere else to put it. The bar is built from the terrain the fleet is standing in, and
 * thin reality is not a place - it is a property of the whole system, and the rigs can be run
 * anywhere. So this is terrain that covers everything and is never seen: it draws nothing, blocks
 * nothing, and does nothing to a fleet.
 * <p>
 * The trick is IndEvo's, from the radiation field its meteor swarms leave behind. Rather than
 * following the fleet about, the terrain answers {@link #containsEntity} with a question instead of
 * a distance - you are inside it exactly when the overlay is up. Nothing has to be moved, and
 * nothing can drift out of step with what is on screen.
 * <p>
 * Extends the corona because that is the terrain vanilla writes its live readouts on, and every
 * corona behaviour that would otherwise come with it - the CR loss, the flares, the loops, the
 * blocked approach - is switched off below.
 */
public class CoherenceTerrain extends StarCoronaTerrainPlugin {

    /** As registered in data/campaign/terrain.json. */
    public static final String TERRAIN_ID = "catchrelease_coherence_field";

    /** Wide enough to cover any system, since what it covers is "wherever the fleet is". */
    public static final float FIELD_RADIUS = 50000f;

    /** Seconds of nothing to show before it takes itself away again. */
    public static final float IDLE_EXPIRE = 5f;

    protected float idle = 0f;

    /**
     * Tags the entity with the terrain id, which nothing else does.
     * <p>
     * {@code BaseTerrain.init} keeps the id in a field and never puts it on the entity, so a
     * lookup by tag finds nothing - and this one is looked up before every add. Untagged, the
     * field would be added again on every frame the overlay is up.
     */
    @Override
    public void init(String terrainId, SectorEntityToken entity, Object param) {
        super.init(terrainId, entity, param);

        entity.addTag(TERRAIN_ID);
    }

    /**
     * Puts the field in a location, if it is not already there.
     * <p>
     * Added on demand rather than to every system at generation: the reading only exists while
     * somebody is fishing, and a terrain in all four hundred systems is four hundred things to
     * advance for one that is ever looked at.
     */
    public static void ensureIn(LocationAPI location) {
        if (location == null) return;

        for (SectorEntityToken existing : location.getEntitiesWithTag(TERRAIN_ID)) {
            if (!existing.isExpired()) return;
        }

        CoronaParams params = new CoronaParams(FIELD_RADIUS, 0f, null, 0f, 0f, 0f);
        params.name = "Thin Fabric";

        location.addTerrain(TERRAIN_ID, params);
    }

    /**
     * Inside it exactly when the overlay is up, and only for the player - nobody else's readout is
     * being drawn, and a corona that claimed every hull in the system would be applying itself to
     * traffic that has no idea what it is.
     */
    @Override
    public boolean containsEntity(SectorEntityToken other) {
        return other != null && other == Global.getSector().getPlayerFleet() && getLevel() > 0f;
    }

    protected float getLevel() {
        return CoherenceOverlayScript.getLevel();
    }

    /** Takes itself away once nothing has needed it for a while, rather than sitting in the save. */
    @Override
    public void advance(float amount) {
        super.advance(amount);

        idle = getLevel() > 0f ? 0f : idle + amount;

        if (idle > IDLE_EXPIRE) Misc.fadeAndExpire(entity, 0f);
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    public String getNameForTooltip() {
        return "Thin Fabric";
    }

    /** The reading itself, since the bar shows this line whether or not anybody opens the tooltip. */
    @Override
    public String getTerrainName() {
        return "Thin Fabric - " + FishItemPlugin.getAberrationLabel(getAberration());
    }

    @Override
    public Color getNameColor() {
        return FishItemPlugin.getAberrationColor(getAberration());
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float pad = 10f;

        tooltip.addTitle(getNameForTooltip());

        float aberration = getAberration();

        tooltip.addPara("Local coherence: %s.", pad, getNameColor(),
                FishItemPlugin.getAberrationLabel(aberration));

        String source = getSource();
        if (source != null) tooltip.addPara("Thinned by %s.", pad, Misc.getHighlightColor(), source);

        tooltip.addPara("Specimens taken here come up further from what they should be, which is"
                + " worth more to some buyers and less to others.", pad);
    }

    protected float getAberration() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return 0f;

        return Aberration.baseAt(fleet.getLocationInHyperspace(), fleet.getContainingLocation());
    }

    protected String getSource() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return null;

        return Aberration.dominantSourceAt(fleet.getLocationInHyperspace(),
                fleet.getContainingLocation());
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    @Override
    public float getTooltipWidth() {
        return 350f;
    }

    //--- everything a corona does that this must not

    /** A readout, not a hazard. The corona's CR loss would come with it otherwise. */
    @Override
    public void applyEffect(SectorEntityToken entity, float days) {
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
    }

    @Override
    public float getRenderRange() {
        return 0f;
    }

    @Override
    public boolean containsPoint(Vector2f point, float radius) {
        return false;
    }

    @Override
    protected boolean shouldPlayLoopOne() {
        return false;
    }

    @Override
    protected boolean shouldPlayLoopTwo() {
        return false;
    }

    @Override
    protected float getExtraSoundRadius() {
        return 0f;
    }

    @Override
    public FlareManager getFlareManager() {
        return null;
    }

    @Override
    public boolean hasAIFlag(Object flag) {
        return false;
    }

    /** Zero, so nothing routes around a field that is not there to be avoided. */
    @Override
    public float getMaxEffectRadius(Vector2f locFrom) {
        return 0f;
    }

    @Override
    public float getMinEffectRadius(Vector2f locFrom) {
        return 0f;
    }

    @Override
    public float getOptimalEffectRadius(Vector2f locFrom) {
        return 0f;
    }

    @Override
    public boolean canPlayerHoldStationIn() {
        return false;
    }
}
