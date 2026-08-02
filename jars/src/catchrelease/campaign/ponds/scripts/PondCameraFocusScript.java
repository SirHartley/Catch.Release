package catchrelease.campaign.ponds.scripts;

import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
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

    /** 0 puts the camera on the fleet, 1 on the pond, and it eases between the two. */
    protected float focus = 0f;

    protected boolean done = false;

    /** Not saved - the viewport is rebuilt on load, so nobody is holding it at that point. */
    transient protected boolean holdingCamera = false;

    /** Visible area at zoom factor 1, captured when the camera is taken over. */
    transient protected float widthAtZoomOne = 0f;
    transient protected float heightAtZoomOne = 0f;

    /**
     * Where the camera actually was when this started, as an offset from the fleet - free look, in
     * practice. Eased away rather than dropped, so the move begins from where the player is looking
     * instead of snapping to the fleet first.
     */
    transient protected Vector2f carry = null;

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

        MaskedFishingPondTerrainPlugin plugin = getPondPlugin();

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

        start(fleet);

        //every frame, not just at the start: free look toggled on mid-hold would pan against this and
        //leave an offset behind to jump on when the camera goes back
        Global.getSector().getCampaignUI().resetViewOffset();

        focus = approach(focus, shouldFocus(fleet) ? 1f : 0f, amount);

        carry.x = approach(carry.x, 0f, amount);
        carry.y = approach(carry.y, 0f, amount);

        Vector2f center = getFocusedCenter(fleet.getLocation(), pond.getLocation());
        center.x += carry.x;
        center.y += carry.y;

        //all the way back on the fleet - hand the camera over and leave it alone
        if (Misc.getDistance(center, fleet.getLocation()) <= PondConstants.POND_FOCUS_HANDBACK_DISTANCE) {
            focus = 0f;
            releaseCamera();

            if (isOutOfSight()) {
                plugin.deactivate();
                stop();
            }

            return;
        }

        holdCameraAt(center);
    }

    /**
     * Notes where the camera is before anything is done to it, and puts free look away.
     * <p>
     * Both halves matter. Free look pans the camera off the fleet, so a hold that assumes the fleet
     * position starts by jumping the width of that pan - and a pan still set when the camera goes
     * back jumps again on the way out. Turning it off clears the pan; carrying the offset the camera
     * already had covers the gap that clearing it would otherwise leave.
     */
    protected void start(CampaignFleetAPI fleet) {
        if (carry != null) return;

        Vector2f center = Global.getSector().getViewport().getCenter();
        carry = new Vector2f(
                center.x - fleet.getLocation().x,
                center.y - fleet.getLocation().y);

        Global.getSector().getCampaignUI().resetViewOffset();
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
     * Where the camera sits for the current {@link #focus} - on the fleet at 0, on the pond at 1.
     * <p>
     * Deliberately anchored to the fleet rather than eased from wherever the camera happens to be: a
     * fleet under way is a moving target, and easing towards one leaves a permanent lag of about its
     * speed times {@link PondConstants#POND_FOCUS_TIME_CONSTANT}. The camera would never arrive, so
     * it would never be handed back - and the fleet would sit that far off centre, which runs it off
     * the top or bottom of the screen first, there being less room that way.
     */
    protected Vector2f getFocusedCenter(Vector2f fleetLocation, Vector2f pondLocation) {
        return new Vector2f(
                fleetLocation.x + (pondLocation.x - fleetLocation.x) * focus,
                fleetLocation.y + (pondLocation.y - fleetLocation.y) * focus);
    }

    /**
     * Eases a value a frame's worth of the way towards a target. Exponential, so it covers the same
     * fraction of what is left every second no matter the frame rate - quick at first, gentle as it
     * arrives.
     * <p>
     * Sector-level scripts are handed the real frame time even while the campaign is paused, so this
     * needs no special case for it.
     */
    protected float approach(float current, float target, float delta) {
        float travelled = 1f - (float) Math.exp(-delta / PondConstants.POND_FOCUS_TIME_CONSTANT);

        return current + (target - current) * travelled;
    }

    /**
     * Takes the camera off the game for this frame and puts it where we want it.
     * <p>
     * Sets the whole viewport rather than just its centre, because external control means the game
     * stops sizing the viewport too - and it never stops feeding the player's scrolling into the zoom
     * tracker. Left on its own the visible size would be frozen at whatever it was when we took over,
     * so scrolling would do nothing until the camera was handed back and the whole accumulated zoom
     * arrived at once. Reading the zoom factor back every frame keeps scrolling live throughout.
     */
    protected void holdCameraAt(Vector2f center) {
        ViewportAPI viewport = Global.getSector().getViewport();
        float zoom = Global.getSector().getCampaignUI().getZoomFactor();

        if (!holdingCamera) {
            //while the game still owns the viewport, its size is the zoom-one size times the zoom
            widthAtZoomOne = zoom > 0f ? viewport.getVisibleWidth() / zoom : viewport.getVisibleWidth();
            heightAtZoomOne = zoom > 0f ? viewport.getVisibleHeight() / zoom : viewport.getVisibleHeight();

            viewport.setExternalControl(true);
            holdingCamera = true;
        }

        float width = widthAtZoomOne * zoom;
        float height = heightAtZoomOne * zoom;

        viewport.set(center.x - width * 0.5f, center.y - height * 0.5f, width, height);
    }

    /** Hands the camera back, but only if we were the ones holding it. */
    protected void releaseCamera() {
        if (!holdingCamera) return;

        Global.getSector().getViewport().setExternalControl(false);
        holdingCamera = false;
    }

    protected void stop() {
        releaseCamera();
        focus = 0f;
        done = true;
    }

    protected MaskedFishingPondTerrainPlugin getPondPlugin() {
        return MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
    }
}
