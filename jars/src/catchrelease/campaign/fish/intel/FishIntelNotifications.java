package catchrelease.campaign.fish.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.util.DelayedActionScript;

/**
 * The one delivery policy for Catch.Release intel.
 * <p>
 * Vanilla's intel manager only drains its comm queue while the campaign is unpaused. Forcing a
 * queued entry on the next campaign frame therefore gives it immediately after a modal dialog
 * closes, instead of spending its campaign message behind that dialog. Updates use the same
 * zero-day vanilla script boundary.
 */
public final class FishIntelNotifications {

    private FishIntelNotifications() {
    }

    /** Adds a new entry on the first unpaused campaign frame, with its normal campaign message. */
    public static void queue(BaseIntelPlugin intel) {
        if (intel == null || Global.getSector() == null) return;

        IntelManagerAPI manager = Global.getSector().getIntelManager();
        if (manager.hasIntel(intel) || manager.hasIntelQueued(intel)) return;

        intel.setForceAddNextFrame(true);
        manager.queueIntel(intel);
    }

    /** Sends an existing entry's update on the first unpaused campaign frame. */
    public static void update(BaseIntelPlugin intel, Object listInfoParam) {
        if (intel == null || Global.getSector() == null) return;

        Global.getSector().addScript(new DeferredUpdate(intel, listInfoParam));
    }

    protected static final class DeferredUpdate extends DelayedActionScript {

        private final BaseIntelPlugin intel;
        private final Object listInfoParam;

        protected DeferredUpdate(BaseIntelPlugin intel, Object listInfoParam) {
            super(0f);
            this.intel = intel;
            this.listInfoParam = listInfoParam;
        }

        @Override
        public void doAction() {
            if (Global.getSector() == null
                    || !Global.getSector().getIntelManager().hasIntel(intel)) return;

            intel.sendUpdateIfPlayerHasIntel(listInfoParam, false);
        }
    }
}
