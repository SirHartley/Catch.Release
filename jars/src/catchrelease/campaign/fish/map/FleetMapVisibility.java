package catchrelease.campaign.fish.map;

import catchrelease.config.Setting;
import catchrelease.campaign.fish.fisherman.FishermanMapIcon;
import catchrelease.campaign.fish.jobs.fleet.FleetQuestMapIcon;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;

public final class FleetMapVisibility {

    private FleetMapVisibility() {
    }

    public static boolean isDetected(CampaignFleetAPI fleet) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        return fleet != null && player != null
                && fleet.getContainingLocation() != null
                && fleet.getContainingLocation() == player.getContainingLocation()
                && fleet.getVisibilityLevelToPlayerFleet() != VisibilityLevel.NONE;
    }

    public static void sync(SectorEntityToken marker, CampaignFleetAPI fleet) {
        if (marker == null || marker.isExpired()) return;
        if (fleet != null) marker.setLocation(fleet.getLocation().x, fleet.getLocation().y);
        if (!isEnabled(marker) || isDetected(fleet)) {
            redirectCourse(marker, fleet);
            if (marker.isAlive()) marker.getContainingLocation().removeEntity(marker);
        } else {
            restore(marker);
        }
    }

    public static void restore(SectorEntityToken marker) {
        if (marker != null && !isEnabled(marker)) {
            if (marker.isAlive() && marker.getContainingLocation() != null) {
                marker.getContainingLocation().removeEntity(marker);
            }
            return;
        }
        // removeEntity retains the location; isAlive tests actual membership.
        if (marker != null && !marker.isExpired() && !marker.isAlive()
                && marker.getContainingLocation() != null) {
            marker.getContainingLocation().addEntity(marker);
        }
    }

    public static boolean isEnabled(SectorEntityToken marker) {
        if (marker == null) return false;
        if (marker.getCustomPlugin() instanceof FishermanMapIcon) {
            return Setting.FISHERMAN_MARKERS.enabled();
        }
        if (marker.getCustomPlugin() instanceof FleetQuestMapIcon) {
            return Setting.ACCEPTED_MARKERS.enabled();
        }
        return true;
    }

    public static void redirectCourse(SectorEntityToken marker, CampaignFleetAPI fleet) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (marker != null && fleet != null && player != null
                && player.getContainingLocation() == fleet.getContainingLocation()
                && Global.getSector().getCampaignUI().getUltimateCourseTarget() == marker) {
            Global.getSector().getCampaignUI().layInCourseForNextStep(fleet);
        }
    }
}
