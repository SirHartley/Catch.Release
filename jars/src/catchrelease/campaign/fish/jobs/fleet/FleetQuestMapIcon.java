package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.map.FleetMapVisibility;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;

public class FleetQuestMapIcon extends BaseCustomEntityPlugin {

    public static final String ENTITY_ID = "catchrelease_FleetQuestMapIcon";
    private static final String MARKER_KEY = "$catchrelease_fleetQuestMapMarker";
    protected static final float AUTOPILOT_CHECK_SECONDS = 1f;

    protected CampaignFleetAPI fleet;
    protected float autopilotCheckElapsed;

    public static Color getMarkerColor() {
        return Global.getSettings().getCustomEntitySpec(ENTITY_ID).getIconColor();
    }

    public static SectorEntityToken findOrAdd(CampaignFleetAPI fleet) {
        if (!isActive(fleet)) {
            removeFor(fleet);
            return null;
        }

        LocationAPI location = fleet.getContainingLocation();
        Object saved = fleet.getMemoryWithoutUpdate().get(MARKER_KEY);
        if (saved instanceof SectorEntityToken marker) {
            if (!marker.isExpired() && marker.getContainingLocation() == location
                    && marker.getCustomPlugin() instanceof FleetQuestMapIcon icon && icon.isFor(fleet)) {
                FleetMapVisibility.sync(marker, fleet);
                return marker;
            }
            removeFor(fleet);
        }

        SectorEntityToken found = null;
        for (CustomCampaignEntityAPI candidate : new ArrayList<>(location.getCustomEntities())) {
            if (!ENTITY_ID.equals(candidate.getCustomEntityType())) continue;
            if (!(candidate.getCustomPlugin() instanceof FleetQuestMapIcon)) continue;
            if (!((FleetQuestMapIcon) candidate.getCustomPlugin()).isFor(fleet)) continue;

            if (found == null) found = candidate;
            else location.removeEntity(candidate);
        }

        if (found == null) {
            found = location.addCustomEntity(Misc.genUID(), null, ENTITY_ID,
                    fleet.getFaction().getId(), fleet);
        }

        fleet.getMemoryWithoutUpdate().set(MARKER_KEY, found);
        FleetMapVisibility.sync(found, fleet);
        return found;
    }

    public static void removeFor(CampaignFleetAPI fleet) {
        if (fleet == null || Global.getSector() == null) return;

        Object saved = fleet.getMemoryWithoutUpdate().get(MARKER_KEY);
        if (saved instanceof SectorEntityToken marker && marker.getContainingLocation() != null) {
            marker.getContainingLocation().removeEntity(marker);
        }
        fleet.getMemoryWithoutUpdate().unset(MARKER_KEY);

        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (CustomCampaignEntityAPI candidate : new ArrayList<>(location.getCustomEntities())) {
                if (!ENTITY_ID.equals(candidate.getCustomEntityType())) continue;
                if (!(candidate.getCustomPlugin() instanceof FleetQuestMapIcon)) continue;
                if (((FleetQuestMapIcon) candidate.getCustomPlugin()).isFor(fleet)) {
                    location.removeEntity(candidate);
                }
            }
        }
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        if (pluginParams instanceof CampaignFleetAPI) fleet = (CampaignFleetAPI) pluginParams;
    }

    @Override
    public void advance(float amount) {
        if (entity == null) return;

        if (!isActive() || fleet.getContainingLocation() != entity.getContainingLocation()) {
            remove();
            return;
        }

        FleetMapVisibility.sync(entity, fleet);
        redirectAutopilot(amount);
    }

    protected boolean isFor(CampaignFleetAPI other) {
        return fleet == other;
    }

    protected boolean isActive() {
        return isActive(fleet);
    }

    private static boolean isActive(CampaignFleetAPI fleet) {
        return fleet != null && !fleet.isExpired() && fleet.isAlive()
                && fleet.getMemoryWithoutUpdate().getBoolean(FleetQuest.QUEST_FLAG)
                && fleet.getMemoryWithoutUpdate().getBoolean(FleetQuest.TAKEN_FLAG);
    }

    protected void redirectAutopilot(float amount) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != entity.getContainingLocation()) {
            autopilotCheckElapsed = 0f;
            return;
        }

        autopilotCheckElapsed += amount;
        if (autopilotCheckElapsed < AUTOPILOT_CHECK_SECONDS) return;
        autopilotCheckElapsed %= AUTOPILOT_CHECK_SECONDS;

        if (Global.getSector().getCampaignUI().getUltimateCourseTarget() == entity) {
            Global.getSector().getCampaignUI().layInCourseForNextStep(fleet);
        }
    }

    protected void remove() {
        if (entity == null || entity.getContainingLocation() == null) return;
        entity.getContainingLocation().removeEntity(entity);
        if (fleet != null && fleet.getMemoryWithoutUpdate().get(MARKER_KEY) == entity) {
            fleet.getMemoryWithoutUpdate().unset(MARKER_KEY);
        }
    }

    @Override
    public boolean hasCustomMapTooltip() {
        return isActive();
    }

    @Override
    public float getMapTooltipWidth() {
        return 280f;
    }

    @Override
    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        if (!isActive()) return;
        tooltip.addTitle(Misc.ucFirst(fleet.getName()));
    }
}
