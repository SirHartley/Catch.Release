package catchrelease.campaign.fish.coherence;

import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.items.FishItemPlugin;
import catchrelease.helper.cache.TimedValue;
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

public class CoherenceTerrain extends StarCoronaTerrainPlugin {

    public static final String TERRAIN_ID = "catchrelease_coherence_field";
    public static final float FIELD_RADIUS = 50000f;
    public static final float IDLE_EXPIRE = 5f;
    public static final float READING_REFRESH = 1f;

    protected float idle = 0f;
    protected transient float readingClock = 0f;
    protected transient TimedValue<Reading> reading;

    protected record Reading(float aberration, String source) {

    }

    @Override
    public void init(String terrainId, SectorEntityToken entity, Object param) {
        super.init(terrainId, entity, param);

        entity.addTag(TERRAIN_ID);
    }

    public static void ensureIn(LocationAPI location) {
        if (location == null) return;

        for (SectorEntityToken existing : location.getEntitiesWithTag(TERRAIN_ID)) {
            if (!existing.isExpired()) return;
        }

        CoronaParams params = new CoronaParams(FIELD_RADIUS, 0f, null, 0f, 0f, 0f);
        params.name = "Thin Fabric";

        location.addTerrain(TERRAIN_ID, params);
    }

    @Override
    public boolean containsEntity(SectorEntityToken other) {
        return other != null && other == Global.getSector().getPlayerFleet() && getLevel() > 0f;
    }

    protected float getLevel() {
        return CoherenceOverlayScript.getLevel();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        readingClock += amount;

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

    protected Reading getReading() {
        // lazily built - the field is transient, so a loaded save starts without one
        if (reading == null) reading = new TimedValue<>(READING_REFRESH);

        return reading.get(readingClock, () -> {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

            if (fleet == null) return new Reading(0f, null);

            return new Reading(
                    Aberration.baseAt(
                            fleet.getLocationInHyperspace(), fleet.getContainingLocation()),
                    Aberration.dominantSourceAt(
                            fleet.getLocationInHyperspace(), fleet.getContainingLocation()));
        });
    }

    protected float getAberration() {
        return getReading().aberration;
    }

    protected String getSource() {
        return getReading().source;
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    @Override
    public float getTooltipWidth() {
        return 350f;
    }

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
