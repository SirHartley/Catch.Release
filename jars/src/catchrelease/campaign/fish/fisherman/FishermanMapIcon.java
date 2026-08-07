package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * A boat's mark on the system map, and nowhere else.
 * <p>
 * Its spec draws on the map and not in the campaign - {@code showInCampaign} false,
 * {@code showIconOnMap} true, which is the pair vanilla's own {@code base_intel_icon} is built
 * from. That is the whole point of it: a mark painted over the hull in space says nothing the hull
 * did not already say, whereas the map is the one screen where a boat somewhere out in the dark is
 * a blip among forty other blips and cannot be told from any of them.
 * <p>
 * It has no sensor profile, so it is never a contact to be found - it is simply drawn, which is
 * what makes the boat locatable on the map while it is out of sight.
 * <p>
 * Rides the fleet rather than orbiting anything, and goes when the fleet does.
 */
public class FishermanMapIcon extends BaseCustomEntityPlugin {

    public static final String ENTITY_ID = "catchrelease_FisherMapIcon";

    /** Hangs a mark on a boat, in the location the boat is currently in. */
    public static SectorEntityToken addTo(CampaignFleetAPI fleet) {
        if (fleet == null) return null;

        LocationAPI where = fleet.getContainingLocation();
        if (where == null) return null;

        SectorEntityToken icon = where.addCustomEntity(Misc.genUID(), null, ENTITY_ID,
                FishermanConstants.FACTION, fleet);

        icon.setLocation(fleet.getLocation().x, fleet.getLocation().y);

        return icon;
    }

    protected CampaignFleetAPI fleet;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        if (pluginParams instanceof CampaignFleetAPI) fleet = (CampaignFleetAPI) pluginParams;
    }

    /**
     * Sits where the boat sits. A mark whose boat has gone - died, left, or jumped out of the
     * system without it - takes itself off rather than standing over empty water.
     */
    @Override
    public void advance(float amount) {
        if (entity == null) return;

        if (fleet == null || fleet.isExpired() || !fleet.isAlive()
                || fleet.getContainingLocation() != entity.getContainingLocation()) {

            remove();
            return;
        }

        entity.setLocation(fleet.getLocation().x, fleet.getLocation().y);
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

    /** Says which boat it is, since the icon itself is the same one on every fishing fleet. */
    @Override
    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        if (fleet == null) return;

        tooltip.addTitle(fleet.getNameWithFaction());
        tooltip.addPara("Fishing. Trades in survey data, buys a catch, and carries an outfitter.",
                Misc.getGrayColor(), 10f);
    }
}
