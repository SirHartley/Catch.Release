package catchrelease.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;


public final class CampaignHelper {

    private CampaignHelper() {
    }


    public static boolean isPlayerHere(SectorEntityToken entity) {
        if (entity == null || entity.getContainingLocation() == null) return false;

        CampaignFleetAPI player = Global.getSector() == null
                ? null : Global.getSector().getPlayerFleet();

        return player != null
                && player.getContainingLocation() == entity.getContainingLocation();
    }
}
