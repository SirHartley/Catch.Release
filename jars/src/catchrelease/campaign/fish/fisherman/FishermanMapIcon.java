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
 * Rides the fleet rather than orbiting anything, and appears only while the player shares its
 * location. The marker is deliberately reconciled rather than blindly added: old saves can carry
 * more than one from before that lifetime was tied to the player's location.
 */
public class FishermanMapIcon extends BaseCustomEntityPlugin {

    public static final String ENTITY_ID = "catchrelease_FisherMapIcon";

    private static final String SERVICE_LINE =
            "Fishing. Trades in range data, buys a catch, and carries an outfitter.";

    /**
     * Finds this boat's one current-location mark, removing duplicate survivors from older saves,
     * or creates it when the player has come alongside a boat with no mark yet.
     */
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

    /** Removes every surviving mark bound to this exact boat, wherever an old save left it. */
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

    /**
     * The player can only read a boat from the map of the place they are in. Remove every old
     * marker from somewhere else immediately after a load or transition; the watched boat's own
     * behaviour restores exactly one in the newly current location.
     */
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

    protected CampaignFleetAPI fleet;

    /** Object identity is the fleet identity here: plugin parameters serialize that exact hull. */
    protected boolean isFor(CampaignFleetAPI other) {
        return fleet == other;
    }

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
        int band = FishermanIdentity.getDialogueBand(FishermanIdentity.getDrift(fleet));
        tooltip.addPara(FishermanIdentity.corrupt(SERVICE_LINE, band), Misc.getGrayColor(), 10f);
    }
}
