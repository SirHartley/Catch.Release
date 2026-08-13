package catchrelease.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * The small campaign questions asked from several corners at once, so each corner stops keeping
 * its own slightly different copy of the answer.
 */
public final class CampaignHelper {

    private CampaignHelper() {
    }

    /**
     * Whether the player fleet is in the same location as this entity - what holds a visit
     * clock, keeps a tutorial boat posted, or lets a camp give chase. Null anywhere - no
     * sector, no player, no entity, an entity in no location at all - answers no.
     */
    public static boolean isPlayerHere(SectorEntityToken entity) {
        if (entity == null || entity.getContainingLocation() == null) return false;

        CampaignFleetAPI player = Global.getSector() == null
                ? null : Global.getSector().getPlayerFleet();

        return player != null
                && player.getContainingLocation() == entity.getContainingLocation();
    }
}
