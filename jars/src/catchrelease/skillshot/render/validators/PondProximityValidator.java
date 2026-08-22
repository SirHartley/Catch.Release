package catchrelease.skillshot.render.validators;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.skillshot.render.PositionValidator;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import org.lwjgl.util.vector.Vector2f;

public class PondProximityValidator implements PositionValidator {
    protected float safeRadius;

    public PondProximityValidator(float safeRadius) {
        this.safeRadius = safeRadius;
    }

    @Override
    public boolean isValid(Vector2f worldPos) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        for (CampaignTerrainAPI t : fleet.getContainingLocation().getTerrainCopy()) {
            if (t.getPlugin().containsPoint(worldPos, -safeRadius)) return true;
        }

        return false;
    }
}
