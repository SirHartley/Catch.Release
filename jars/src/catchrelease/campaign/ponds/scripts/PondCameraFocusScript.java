package catchrelease.campaign.ponds.scripts;

import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.entities.MaskedFishingPondEntityPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/**
 * Holds the campaign camera on an open pond so it can be aimed at comfortably, and closes the pond
 * once the player has left it behind.
 * <p>
 * Three phases, in order:
 * <ol>
 * <li>Fleet within {@link PondConstants#POND_INTERACT_RANGE_MULT} of the pond - the camera eases onto
 * the pond and stays centred on it. That range is the same one the rod ability uses, so the camera
 * holds exactly while the skillshot is available.</li>
 * <li>Fleet moves out of that range - the camera eases back onto the fleet and control goes back to
 * the game, leaving the player where they expect to be.</li>
 * <li>Pond drifts off screen, or the player leaves the system - the pond closes.</li>
 * </ol>
 * Has to be a sector-level script rather than one on the pond entity: entity scripts only advance
 * as part of the location advance, which the campaign engine skips entirely while paused - and
 * aiming a skillshot is something the player does paused.
 */
public class PondCameraFocusScript implements EveryFrameScript {

    protected SectorEntityToken pond;

    protected boolean focusing = false;
    protected boolean done = false;

    /** Not saved - the viewport is rebuilt on load, so nobody is holding it at that point. */
    transient protected boolean holdingCamera = false;

    public PondCameraFocusScript(SectorEntityToken pond) {
        this.pond = pond;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        MaskedFishingPondEntityPlugin plugin = getPondPlugin();

        //something else already closed it
        if (plugin == null || !plugin.isActive()) {
            stop();
            return;
        }

        //left the system: as out of sight as it gets
        if (!pond.isInCurrentLocation()) {
            plugin.deactivate();
            stop();
            return;
        }

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) {
            releaseCamera();
            return;
        }

        if (shouldFocus(fleet)) {
            focusing = true;
            moveCameraTowards(pond.getLocation(), amount);
            return;
        }

        //out of range: give the camera back where the player left it rather than cutting
        if (focusing) {
            if (moveCameraTowards(fleet.getLocation(), amount)) {
                focusing = false;
                releaseCamera();
            }
            return;
        }

        if (isOutOfSight()) {
            plugin.deactivate();
            stop();
        }
    }

    /**
     * Whether the camera should be sitting on the pond right now. False while a dialog or a core UI
     * tab is up - the camera belongs to the game there.
     */
    protected boolean shouldFocus(CampaignFleetAPI fleet) {
        if (fleet.getContainingLocation() != pond.getContainingLocation()) return false;

        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.getCurrentInteractionDialog() != null || ui.getCurrentCoreTab() != null) return false;

        return Misc.getDistance(fleet.getLocation(), pond.getLocation())
                <= pond.getRadius() * PondConstants.POND_INTERACT_RANGE_MULT;
    }

    /** True once the pond has left the visible area entirely. */
    protected boolean isOutOfSight() {
        return !Global.getSector().getViewport().isNearViewport(pond.getLocation(), pond.getRadius());
    }

    /**
     * Eases the camera a frame's worth of the way towards a point. Exponential, so it covers the same
     * fraction of what is left every second no matter the frame rate - fast at first, gentle as it
     * arrives.
     * <p>
     * Sector-level scripts are handed the real frame time even while the campaign is paused, so this
     * needs no special case for it.
     *
     * @return true once the camera is close enough to call it arrived
     */
    protected boolean moveCameraTowards(Vector2f target, float delta) {
        ViewportAPI viewport = Global.getSector().getViewport();

        //stops the game from writing the viewport itself every frame, leaving the centre to us
        viewport.setExternalControl(true);
        holdingCamera = true;

        Vector2f center = viewport.getCenter();
        float travelled = 1f - (float) Math.exp(-delta / PondConstants.POND_FOCUS_TIME_CONSTANT);

        //a fresh vector: getCenter() may well hand out the viewport's own
        Vector2f next = new Vector2f(
                center.x + (target.x - center.x) * travelled,
                center.y + (target.y - center.y) * travelled);

        viewport.setCenter(next);

        return Misc.getDistance(next, target) <= PondConstants.POND_FOCUS_HANDBACK_DISTANCE;
    }

    /** Hands the camera back, but only if we were the ones holding it. */
    protected void releaseCamera() {
        if (!holdingCamera) return;

        Global.getSector().getViewport().setExternalControl(false);
        holdingCamera = false;
    }

    protected void stop() {
        releaseCamera();
        focusing = false;
        done = true;
    }

    protected MaskedFishingPondEntityPlugin getPondPlugin() {
        if (pond == null || !(pond.getCustomPlugin() instanceof MaskedFishingPondEntityPlugin)) return null;

        return (MaskedFishingPondEntityPlugin) pond.getCustomPlugin();
    }
}
