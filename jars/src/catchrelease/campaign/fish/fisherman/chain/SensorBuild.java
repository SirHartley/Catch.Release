package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import com.fs.starfarer.api.campaign.TextPanelAPI;

import java.util.ArrayList;
import java.util.List;

// T2's follow-up: the Fisherman's Needle Sensor schematic, then building it. T3 starts from the return step.
public final class SensorBuild {

    private SensorBuild() {
    }

    // The schematic unless the player can already build or owns one; an owned sensor skips straight to the return.
    public static boolean give(TextPanelAPI text) {
        if (!FishermanChain.isAt(FishermanChain.Stage.T2_DEBRIEF)) return false;

        List<FishReward.Receipt> receipts = new ArrayList<>();
        if (!ShopSchematics.has(Tackle.NEEDLE_SENSOR)) {
            receipts.add(FishReward.tackleSchematic(Tackle.NEEDLE_SENSOR).grantWithReceipt());
        }

        FishermanChain.setStage(FishermanChain.Stage.T2_SENSOR);
        FishReward.showReceipts(text, receipts);
        CaptainsFleet.updateIntel();
        checkBought();

        return true;
    }

    public static boolean isOwned() {
        return TackleManager.isOwned(Tackle.NEEDLE_SENSOR);
    }

    public static void checkBought() {
        if (FishermanChain.isAt(FishermanChain.Stage.T2_SENSOR) && isOwned()) {
            FishermanChain.setStage(FishermanChain.Stage.T2_SENSOR_BOUGHT);
        }
    }
}
