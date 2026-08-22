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

public class PondCameraFocusScript implements EveryFrameScript {
    protected SectorEntityToken pond;
    protected float focus = 0f;
    protected boolean done = false;
    transient protected boolean holdingCamera = false;
    transient protected float widthAtZoomOne = 0f;
    transient protected float heightAtZoomOne = 0f;
    transient protected Vector2f transitionOffset = null;

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

        if (plugin == null || !plugin.isActive()) {
            stop();
            return;
        }

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

        boolean uiUp = isUiUp();
        boolean focusRequested = shouldFocus(fleet);

        if (!uiUp && focusRequested && !holdingCamera) acquireCamera(fleet);

        // An inactive pond camera must not clear Free View merely because its script is still alive.
        if (!holdingCamera) {
            focus = 0f;

            if (!focusRequested && isOutOfSight()) {
                plugin.deactivate();
                stop();
            }

            return;
        }

        if (!uiUp) {
            float target = focusRequested ? 1f : 0f;

            focus = approach(focus, target, amount, getTimeConstant(target));

            transitionOffset.x = approach(transitionOffset.x, 0f, amount, PondConstants.POND_FOCUS_TIME_CONSTANT);
            transitionOffset.y = approach(transitionOffset.y, 0f, amount, PondConstants.POND_FOCUS_TIME_CONSTANT);
        }

        Vector2f center = getFocusedCenter(fleet.getLocation(), pond.getLocation());
        keepFleetOnScreen(center, fleet.getLocation());

        center.x += transitionOffset.x;
        center.y += transitionOffset.y;

        if (uiUp) {
            if (holdingCamera) holdCameraAt(center);
            return;
        }

        if (!focusRequested
                && Misc.getDistance(center, fleet.getLocation()) <= PondConstants.POND_FOCUS_HANDBACK_DISTANCE) {
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

    protected void acquireCamera(CampaignFleetAPI fleet) {
        ViewportAPI viewport = Global.getSector().getViewport();
        float zoom = Global.getSector().getCampaignUI().getZoomFactor();

        Vector2f anchor = getFocusedCenter(fleet.getLocation(), pond.getLocation());
        keepFleetOnScreen(anchor, fleet.getLocation());

        Vector2f visibleCenter = viewport.getCenter();
        transitionOffset = new Vector2f(
                visibleCenter.x - anchor.x,
                visibleCenter.y - anchor.y);

        widthAtZoomOne = zoom > 0f ? viewport.getVisibleWidth() / zoom : viewport.getVisibleWidth();
        heightAtZoomOne = zoom > 0f ? viewport.getVisibleHeight() / zoom : viewport.getVisibleHeight();

        Global.getSector().getCampaignUI().resetViewOffset();
        viewport.setExternalControl(true);
        holdingCamera = true;
    }

    protected boolean isUiUp() {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();

        return ui.getCurrentInteractionDialog() != null || ui.getCurrentCoreTab() != null;
    }

    protected boolean shouldFocus(CampaignFleetAPI fleet) {
        if (fleet.getContainingLocation() != pond.getContainingLocation()) return false;

        return Misc.getDistance(fleet.getLocation(), pond.getLocation())
                <= pond.getRadius() * PondConstants.POND_INTERACT_RANGE_MULT;
    }

    protected boolean isOutOfSight() {
        return !Global.getSector().getViewport().isNearViewport(pond.getLocation(), pond.getRadius());
    }

    protected Vector2f getFocusedCenter(Vector2f fleetLocation, Vector2f pondLocation) {
        return new Vector2f(
                fleetLocation.x + (pondLocation.x - fleetLocation.x) * focus,
                fleetLocation.y + (pondLocation.y - fleetLocation.y) * focus);
    }

    protected void keepFleetOnScreen(Vector2f center, Vector2f fleetLocation) {
        ViewportAPI viewport = Global.getSector().getViewport();

        float maxX = viewport.getVisibleWidth() * 0.5f * PondConstants.POND_FOCUS_FLEET_MARGIN;
        float maxY = viewport.getVisibleHeight() * 0.5f * PondConstants.POND_FOCUS_FLEET_MARGIN;

        // unsized viewport (maxX/Y == 0) would clamp onto the fleet position and hand the camera back immediately
        if (maxX > 0f) center.x = clamp(center.x, fleetLocation.x - maxX, fleetLocation.x + maxX);
        if (maxY > 0f) center.y = clamp(center.y, fleetLocation.y - maxY, fleetLocation.y + maxY);
    }

    protected static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    protected float approach(float current, float target, float delta, float timeConstant) {
        float travelled = 1f - (float) Math.exp(-delta / Math.max(0.01f, timeConstant));

        return current + (target - current) * travelled;
    }

    protected float getTimeConstant(float target) {
        return target > focus
                ? PondConstants.POND_FOCUS_TIME_CONSTANT
                : PondConstants.POND_FOCUS_RETURN_TIME_CONSTANT;
    }

    protected void holdCameraAt(Vector2f center) {
        ViewportAPI viewport = Global.getSector().getViewport();
        float zoom = Global.getSector().getCampaignUI().getZoomFactor();

        // suppress free look only while we hold the camera - stops being called once handed back, which is what lets free look work again
        Global.getSector().getCampaignUI().resetViewOffset();

        float width = widthAtZoomOne * zoom;
        float height = heightAtZoomOne * zoom;

        viewport.set(center.x - width * 0.5f, center.y - height * 0.5f, width, height);
    }

    protected void releaseCamera() {
        if (!holdingCamera) return;

        Global.getSector().getViewport().setExternalControl(false);
        holdingCamera = false;
        transitionOffset = null;
        widthAtZoomOne = 0f;
        heightAtZoomOne = 0f;
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
