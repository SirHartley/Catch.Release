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
 * Holds the campaign camera on an open pond while the fleet is within
 * {@link PondConstants#POND_INTERACT_RANGE_MULT} (same range the rod ability uses), eases it back
 * to the fleet once out of range, and closes the pond once it or the fleet leaves view.
 * <p>
 * Must be a sector-level script, not one on the pond entity: entity scripts only advance as part
 * of the location advance, which the campaign engine skips while paused, and aiming a skillshot
 * happens paused.
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

    /** Camera's offset from the fleet when this started (free look); eased to zero rather than dropped. */
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

        boolean uiUp = isUiUp();

        if (!uiUp) {
            float target = shouldFocus(fleet) ? 1f : 0f;

            focus = approach(focus, target, amount, getTimeConstant(target));

            carry.x = approach(carry.x, 0f, amount, PondConstants.POND_FOCUS_TIME_CONSTANT);
            carry.y = approach(carry.y, 0f, amount, PondConstants.POND_FOCUS_TIME_CONSTANT);
        }

        Vector2f center = getFocusedCenter(fleet.getLocation(), pond.getLocation());
        center.x += carry.x;
        center.y += carry.y;

        keepFleetOnScreen(center, fleet.getLocation());

        //dialog open (e.g. the catch minigame): freeze camera in place; only hold it if we already
        //do - a dialog is no reason to take over a camera we'd already given back
        if (uiUp) {
            if (holdingCamera) holdCameraAt(center);
            return;
        }

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
     * Records the camera's offset from the fleet (free look) once, and clears free look - carrying
     * the offset avoids a jump to the fleet position that clearing it alone would cause.
     */
    protected void start(CampaignFleetAPI fleet) {
        if (carry != null) return;

        Vector2f center = Global.getSector().getViewport().getCenter();
        carry = new Vector2f(
                center.x - fleet.getLocation().x,
                center.y - fleet.getLocation().y);

        Global.getSector().getCampaignUI().resetViewOffset();
    }

    /** Whether anything is over the campaign view - a dialog, or one of the core UI tabs. */
    protected boolean isUiUp() {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();

        return ui.getCurrentInteractionDialog() != null || ui.getCurrentCoreTab() != null;
    }

    /** Whether the camera should be sitting on the pond right now. */
    protected boolean shouldFocus(CampaignFleetAPI fleet) {
        if (fleet.getContainingLocation() != pond.getContainingLocation()) return false;

        return Misc.getDistance(fleet.getLocation(), pond.getLocation())
                <= pond.getRadius() * PondConstants.POND_INTERACT_RANGE_MULT;
    }

    /** True once the pond has left the visible area entirely. */
    protected boolean isOutOfSight() {
        return !Global.getSector().getViewport().isNearViewport(pond.getLocation(), pond.getRadius());
    }

    /**
     * Interpolated centre for the current {@link #focus} (fleet at 0, pond at 1). Anchored directly
     * to the fleet rather than eased toward it - easing toward a moving target would leave a
     * permanent lag, so the camera would never reach handback distance.
     */
    protected Vector2f getFocusedCenter(Vector2f fleetLocation, Vector2f pondLocation) {
        return new Vector2f(
                fleetLocation.x + (pondLocation.x - fleetLocation.x) * focus,
                fleetLocation.y + (pondLocation.y - fleetLocation.y) * focus);
    }

    /**
     * Clamps the camera centre so the fleet stays within
     * {@link PondConstants#POND_FOCUS_FLEET_MARGIN} of the half-screen extent on each axis; a
     * circular hold radius doesn't fit a rectangular screen, so X/Y are clamped independently rather
     * than as one radius. Read from the viewport every frame, so zoom changes it live.
     */
    protected void keepFleetOnScreen(Vector2f center, Vector2f fleetLocation) {
        ViewportAPI viewport = Global.getSector().getViewport();

        float maxX = viewport.getVisibleWidth() * 0.5f * PondConstants.POND_FOCUS_FLEET_MARGIN;
        float maxY = viewport.getVisibleHeight() * 0.5f * PondConstants.POND_FOCUS_FLEET_MARGIN;

        //unsized viewport (maxX/Y == 0) would clamp onto the fleet position and hand the camera back immediately
        if (maxX > 0f) center.x = clamp(center.x, fleetLocation.x - maxX, fleetLocation.x + maxX);
        if (maxY > 0f) center.y = clamp(center.y, fleetLocation.y - maxY, fleetLocation.y + maxY);
    }

    protected static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Frame-rate-independent exponential ease toward {@code target}. Sector-level scripts get real
     * frame time even while paused, so no special-casing needed here.
     */
    protected float approach(float current, float target, float delta, float timeConstant) {
        float travelled = 1f - (float) Math.exp(-delta / Math.max(0.01f, timeConstant));

        return current + (target - current) * travelled;
    }

    /** Faster time constant returning to the fleet than approaching the pond - free look stays suppressed until handback, so the return should be brisk. */
    protected float getTimeConstant(float target) {
        return target > focus
                ? PondConstants.POND_FOCUS_TIME_CONSTANT
                : PondConstants.POND_FOCUS_RETURN_TIME_CONSTANT;
    }

    /**
     * Takes the camera for this frame and points it at {@code center}.
     * <p>
     * Sets the whole viewport, not just its centre - external control stops the game from resizing
     * it, but zoom-scroll input keeps accumulating regardless, so the zoom factor must be re-read
     * every frame or scrolling has no visible effect until handback.
     */
    protected void holdCameraAt(Vector2f center) {
        ViewportAPI viewport = Global.getSector().getViewport();
        float zoom = Global.getSector().getCampaignUI().getZoomFactor();

        //suppress free look only while we hold the camera - stops being called once handed back,
        //which is what lets free look work again
        Global.getSector().getCampaignUI().resetViewOffset();

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
