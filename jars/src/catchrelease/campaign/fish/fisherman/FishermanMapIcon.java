package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;

public class FishermanMapIcon extends BaseCustomEntityPlugin {

    public static final String ENTITY_ID = "catchrelease_FisherMapIcon";
    protected static final float AUTOPILOT_CHECK_SECONDS = 1f;
    private static final String SERVICE_LINE =
            "Fishing. Trades in range data, buys a catch, and carries an outfitter.";

    protected CampaignFleetAPI fleet;
    protected boolean standing;
    protected float autopilotCheckElapsed = 0f;

    public static SectorEntityToken findOrAdd(CampaignFleetAPI fleet) {
        if (fleet == null) return null;

        LocationAPI where = fleet.getContainingLocation();
        if (where == null) return null;

        if (CoreFisherSpawner.isStanding(fleet)) {
            return findOrAddStanding((StarSystemAPI) where, fleet);
        }

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

    public static SectorEntityToken findStanding(StarSystemAPI system) {
        if (system == null) return null;

        for (CustomCampaignEntityAPI candidate : system.getCustomEntities()) {
            if (candidate.getCustomPlugin() instanceof FishermanMapIcon icon && icon.standing) {
                return candidate;
            }
        }

        return null;
    }

    public static SectorEntityToken findOrAddStanding(StarSystemAPI system, CampaignFleetAPI fleet) {
        SectorEntityToken found = findStanding(system);
        for (CustomCampaignEntityAPI candidate : new ArrayList<>(system.getCustomEntities())) {
            if (!(candidate.getCustomPlugin() instanceof FishermanMapIcon icon)) continue;
            if (!icon.standing && (fleet == null || !icon.isFor(fleet))) continue;

            if (found == null) found = candidate;
            else if (found != candidate) system.removeEntity(candidate);
        }

        if (found == null) {
            found = system.addCustomEntity(Misc.genUID(), null, ENTITY_ID,
                    FishermanConstants.FACTION);
            Vector2f at = CoreFisherSpawner.pickLocation(system);
            found.setLocation(at.x, at.y);
        }

        FishermanMapIcon icon = (FishermanMapIcon) found.getCustomPlugin();
        icon.standing = true;
        if (fleet != null) icon.attach(fleet);
        found.setDiscoverable(false);
        found.setSensorProfile(null);

        return found;
    }

    public void attach(CampaignFleetAPI fleet) {
        this.fleet = fleet;
        entity.setLocation(fleet.getLocation().x, fleet.getLocation().y);
    }

    public void detach() {
        if (fleet != null) entity.setLocation(fleet.getLocation().x, fleet.getLocation().y);
        fleet = null;
        autopilotCheckElapsed = 0f;
    }

    public static void detachStanding(CampaignFleetAPI fleet) {
        if (fleet == null || !(fleet.getContainingLocation() instanceof StarSystemAPI system)) return;

        SectorEntityToken marker = findStanding(system);
        if (marker != null && ((FishermanMapIcon) marker.getCustomPlugin()).isFor(fleet)) {
            ((FishermanMapIcon) marker.getCustomPlugin()).detach();
        }
    }

    public static void removeFor(CampaignFleetAPI fleet) {
        if (fleet == null) return;

        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (CustomCampaignEntityAPI candidate : new ArrayList<>(location.getCustomEntities())) {
                if (!ENTITY_ID.equals(candidate.getCustomEntityType())) continue;
                if (!(candidate.getCustomPlugin() instanceof FishermanMapIcon)) continue;
                FishermanMapIcon icon = (FishermanMapIcon) candidate.getCustomPlugin();
                if (icon.isFor(fleet)) {
                    if (icon.standing) icon.detach();
                    else location.removeEntity(candidate);
                }
            }
        }
    }

    public static void removeOutside(LocationAPI playerLocation) {
        for (LocationAPI location : Global.getSector().getAllLocations()) {
            if (location == playerLocation) continue;

            for (CustomCampaignEntityAPI candidate : new ArrayList<>(location.getCustomEntities())) {
                if (ENTITY_ID.equals(candidate.getCustomEntityType())
                        && candidate.getCustomPlugin() instanceof FishermanMapIcon icon
                        && !icon.standing) {
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

        if (standing && fleet == null && entity.isInCurrentLocation()
                && entity.getContainingLocation() instanceof StarSystemAPI system) {
            CampaignFleetAPI local = CoreFisherSpawner.getAnyBoat(system);
            if (local != null && local != fleet) attach(local);
        }

        if (fleet == null || fleet.isExpired() || !fleet.isAlive()
                || fleet.getContainingLocation() != entity.getContainingLocation()) {
            if (standing) fleet = null;
            else remove();
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
        return standing || fleet != null;
    }

    @Override
    public float getMapTooltipWidth() {
        return 280f;
    }

    @Override
    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        if (!hasCustomMapTooltip()) return;

        float drift = FishermanIdentity.getDrift(entity.getContainingLocation());
        tooltip.addTitle(Misc.ucFirst(fleet != null ? fleet.getName()
                : FishermanIdentity.getDisplayName(drift)));
        int band = FishermanIdentity.getDialogueBand(drift);
        tooltip.addPara(FishermanIdentity.corrupt(SERVICE_LINE, band), Misc.getGrayColor(), 10f);
    }
}
