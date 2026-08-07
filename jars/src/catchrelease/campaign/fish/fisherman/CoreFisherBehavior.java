package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * A standing boat's working day: the same rig as the visiting one's, on a boat that never leaves.
 * <p>
 * Everything else is inherited - the sweep, the staged motes, the throws, the map mark, the pinned
 * visibility and his name in whatever state the local water leaves it. It is the same trade and the
 * same man; only the schedule and the route are the boat's own.
 * <p>
 * Two things differ, and they are the two hooks overridden here. It is not visiting, so the
 * fortnight clock, the wind-down and the despawn never run. And it does not patrol:
 * {@code PATROL_SYSTEM} wanders the whole system and will cut across an inhabited orbit getting
 * anywhere, which is the one thing these boats are posted not to do. See {@link OuterReaches}.
 */
public class CoreFisherBehavior extends FishermanBehavior {

    public CoreFisherBehavior(CampaignFleetAPI fleet) {
        super(fleet);
    }

    /** It lives here. There is no visit to count down. */
    @Override
    protected boolean isVisiting() {
        return false;
    }

    /**
     * The next leg out in the reaches, issued whenever the last one has run out.
     * <p>
     * One leg at a time rather than a route laid in advance: the destinations are cleared against
     * where the boat actually is, and a queue of them would be a queue of legs planned from a
     * position it has since left.
     */
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
