package catchrelease.campaign.fish.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.util.DelayedActionScript;

public final class FishIntelNotifications {

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

    private FishIntelNotifications() {
    }

    public static void queue(BaseIntelPlugin intel) {
        queue(intel, null);
    }

    public static void queue(BaseIntelPlugin intel, TextPanelAPI textPanel) {
        if (intel == null || Global.getSector() == null) return;

        IntelManagerAPI manager = Global.getSector().getIntelManager();
        if (manager.hasIntel(intel) || manager.hasIntelQueued(intel)) return;

        intel.setForceAddNextFrame(true);
        manager.queueIntel(intel);
        showAdded(intel, textPanel);
    }

    // Adds the intel at once, inside an open dialogue, with its card in the text panel and no later popup.
    public static void addNow(BaseIntelPlugin intel, TextPanelAPI textPanel) {
        if (intel == null || Global.getSector() == null) return;

        IntelManagerAPI manager = Global.getSector().getIntelManager();
        if (manager.hasIntel(intel)) return;

        manager.unqueueIntel(intel);
        manager.addIntel(intel, false, textPanel);
    }

    public static void showAdded(BaseIntelPlugin intel, TextPanelAPI textPanel) {
        if (intel == null || textPanel == null || Global.getSector() == null) return;

        Global.getSector().getIntelManager().addIntelToTextPanel(intel, textPanel);
    }

    public static void update(BaseIntelPlugin intel, Object listInfoParam) {
        if (intel == null || Global.getSector() == null) return;

        Global.getSector().addScript(new DeferredUpdate(intel, listInfoParam));
    }
}
