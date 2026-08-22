package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;

public class FishermanMapIcon extends BaseCustomEntityPlugin {
    public static final String ENTITY_ID = "catchrelease_FisherMapIcon";
    protected static final float AUTOPILOT_CHECK_SECONDS = 1f;
    private static final String SERVICE_LINE =
            "Fishing. Trades in range data, buys a catch, and carries an outfitter.";

    protected CampaignFleetAPI fleet;
    protected float autopilotCheckElapsed = 0f;

    public static SectorEntityToken findOrAdd(CampaignFleetAPI fleet) {
        if (fleet == null) return null;

        LocationAPI where = fleet.getContainingLocation();
        if (where == null) return null;

        SectorEntityToken found = null;
        for (CustomCampaignEntityAPI candidate : new ArrayList<>(where.getCustomEntities())) {
            if (!ENTITY_ID.equals(candidate.getCustomEntityType())) continue;
            if (!(candidate.getCustomPlugin() instanceof FishermanMapIcon)) continue;
            if (!((FishermanMapIcon) candidate.getCustomPlugin()).isFor(fleet)) continue;

            if (found == null) {
                found = candidate;
            } else {
                where.removeEntity(candidate);
            }
        }

        if (found != null) {
            found.setLocation(fleet.getLocation().x, fleet.getLocation().y);
            return found;
        }

        SectorEntityToken icon = where.addCustomEntity(Misc.genUID(), null, ENTITY_ID,
                FishermanConstants.FACTION, fleet);

        icon.setLocation(fleet.getLocation().x, fleet.getLocation().y);

        return icon;
    }

    public static void removeFor(CampaignFleetAPI fleet) {
        if (fleet == null) return;

        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (CustomCampaignEntityAPI candidate : new ArrayList<>(location.getCustomEntities())) {
                if (!ENTITY_ID.equals(candidate.getCustomEntityType())) continue;
                if (!(candidate.getCustomPlugin() instanceof FishermanMapIcon)) continue;
                if (((FishermanMapIcon) candidate.getCustomPlugin()).isFor(fleet)) {
                    location.removeEntity(candidate);
                }
            }
        }
    }

    public static void removeOutside(LocationAPI playerLocation) {
        for (LocationAPI location : Global.getSector().getAllLocations()) {
            if (location == playerLocation) continue;

            for (CustomCampaignEntityAPI candidate : new ArrayList<>(location.getCustomEntities())) {
                if (ENTITY_ID.equals(candidate.getCustomEntityType())
                        && candidate.getCustomPlugin() instanceof FishermanMapIcon) {
                    location.removeEntity(candidate);
                }
            }
        }
    }

    protected boolean isFor(CampaignFleetAPI other) {
        return fleet == other;
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        if (pluginParams instanceof CampaignFleetAPI) fleet = (CampaignFleetAPI) pluginParams;
    }

    @Override
    public void advance(float amount) {
        if (entity == null) return;

        if (fleet == null || fleet.isExpired() || !fleet.isAlive()
                || fleet.getContainingLocation() != entity.getContainingLocation()) {
            remove();
            return;
        }

        entity.setLocation(fleet.getLocation().x, fleet.getLocation().y);
        redirectAutopilot(amount);
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

    public void remove() {
        if (entity == null || entity.getContainingLocation() == null) return;

        entity.getContainingLocation().removeEntity(entity);
    }

    @Override
    public boolean hasCustomMapTooltip() {
        return fleet != null;
    }

    @Override
    public float getMapTooltipWidth() {
        return 280f;
    }

    @Override
    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        if (fleet == null) return;

        tooltip.addTitle(Misc.ucFirst(fleet.getName()));
        int band = FishermanIdentity.getDialogueBand(FishermanIdentity.getDrift(fleet));
        tooltip.addPara(FishermanIdentity.corrupt(SERVICE_LINE, band), Misc.getGrayColor(), 10f);
    }
}
