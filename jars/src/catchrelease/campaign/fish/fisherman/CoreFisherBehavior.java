package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lwjgl.util.vector.Vector2f;

public class CoreFisherBehavior extends FishermanBehavior {
    public CoreFisherBehavior(CampaignFleetAPI fleet) {
        super(fleet);
    }

    @Override
    protected boolean isVisiting() {
        return false;
    }

    @Override
    protected void keepWorking() {
        if (fleet.getCurrentAssignment() != null) return;
        if (!(fleet.getContainingLocation() instanceof StarSystemAPI)) return;

        StarSystemAPI system = (StarSystemAPI) fleet.getContainingLocation();

        Vector2f at = OuterReaches.pick(system, new Vector2f(fleet.getLocation()));
        SectorEntityToken waypoint = system.createToken(at.x, at.y);

        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, waypoint,
                OuterReaches.LEG_DAYS, "working the outer reaches");
    }
}
