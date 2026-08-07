package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * A standing trawler's working day: the same rig as the wanderer's, on a boat that never leaves.
 * <p>
 * Everything the lamps do is inherited - the sweep, the staged motes, the throws, the map mark and
 * the pinned visibility are one rig and there is no reason for the core to have a second copy of it.
 * Three things differ, and they are the three hooks overridden here.
 * <p>
 * It is not visiting, so the fortnight clock, the wind-down and the despawn never run. It does not
 * wear his name, because it is not him - the drift is his plot point and a trawler picking it up
 * would give it away as an effect on the water rather than a fact about the man. And it does not
 * patrol: {@code PATROL_SYSTEM} wanders the whole system and will cut across an inhabited orbit
 * getting anywhere, which is the one thing these boats are posted not to do. See
 * {@link OuterReaches}.
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

    /** Not him, so not his name - and nothing to rename, since a trawler is only ever a trawler. */
    @Override
    protected void keepNamed() {
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
