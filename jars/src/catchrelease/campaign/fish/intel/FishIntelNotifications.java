package catchrelease.campaign.fish.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.util.DelayedActionScript;


public final class FishIntelNotifications {

    private FishIntelNotifications() {
    }


    public static void queue(BaseIntelPlugin intel) {
        if (intel == null || Global.getSector() == null) return;

        IntelManagerAPI manager = Global.getSector().getIntelManager();
        if (manager.hasIntel(intel) || manager.hasIntelQueued(intel)) return;

        intel.setForceAddNextFrame(true);
        manager.queueIntel(intel);
    }


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
