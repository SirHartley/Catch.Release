package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// T2's follow-up: the Fisherman's Needle Sensor schematic, then building it. T3 starts from the return step.
public final class SensorBuild {

    private SensorBuild() {
    }

    // Build the sensor, then go back to the Fisherman; it ends once T3 moves the chain on.
    public static class SensorIntel extends BaseIntelPlugin {

        protected static boolean isReturning() {
            return FishermanChain.isAt(FishermanChain.Stage.T2_SENSOR_BOUGHT);
        }

        @Override
        public boolean shouldRemoveIntel() {
            if (isEnded()) return true;
            if (!FishermanChain.isAt(FishermanChain.Stage.T2_SENSOR) && !isReturning()) endImmediately();

            return isEnded();
        }

        @Override
        public String getName() {
            return isReturning() ? "Needle Sensor: return to the Fisherman" : "Build the Needle Sensor";
        }

        @Override
        public String getSmallDescriptionTitle() {
            return getName();
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), getTitleColor(mode), 0f);

            addBulletPoints(info, mode);
        }

        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Color text = getBulletColorForMode(mode);
            float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);
            if (isReturning()) {
                info.addPara("Return to the nearest fishing boat", text, pad);
            } else {
                info.addPara("Buy the %s at the Outfitter", pad, text, Misc.getHighlightColor(),
                        Tackle.NEEDLE_SENSOR.name);
            }
            unindent(info);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            float opad = 10f;
            FactionAPI faction = Global.getSector().getFaction(FishermanConstants.FACTION);

            info.addImages(width, 128, opad, opad, FishermanIdentity.getPortrait(0f), faction.getCrest());

            if (isReturning()) {
                info.addPara("You have the %s. The Fisherman has something he wants you to look at.", opad,
                        Misc.getHighlightColor(), Tackle.NEEDLE_SENSOR.name);
                addBulletPoints(info, ListInfoMode.IN_DESC);
                FishIntelMapButton.addSetAutopilot(info, width, FishingIntro.getNearestBoat());
                return;
            }

            info.addPara("The Fisherman gave you the %s schematic. Build it, then go back to him.", opad,
                    Misc.getHighlightColor(), Tackle.NEEDLE_SENSOR.name);
            addBulletPoints(info, ListInfoMode.IN_DESC);
        }

        @Override
        public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
            if (isReturning() && FishIntelMapButton.handleSetAutopilot(buttonId, FishingIntro.getNearestBoat())) {
                return;
            }

            super.buttonPressConfirmed(buttonId, ui);
        }

        @Override
        public String getIcon() {
            return FishermanIdentity.getPortrait(0f);
        }

        @Override
        public String getSortString() {
            return getSortStringNewestFirst();
        }

        @Override
        public FactionAPI getFactionForUIColors() {
            return Global.getSector().getPlayerFaction();
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_MISSIONS);
            tags.add(Tags.INTEL_ACCEPTED);
            tags.add(FishermanConstants.FACTION);

            return tags;
        }
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
        FishermanChain.listWantedPurchase();
        // added inside the dialogue, so its card follows the Fisherman's line
        FishIntelNotifications.addNow(new SensorIntel(), text);

        return true;
    }

    // A legendary reported before the debrief is over (LegendaryHunt.skipChain): the schematic, unless known.
    public static void skip(List<FishReward.Receipt> receipts) {
        if (FishermanChain.isBefore(FishermanChain.Stage.T2_SENSOR) && !ShopSchematics.has(Tackle.NEEDLE_SENSOR)) {
            receipts.add(FishReward.tackleSchematic(Tackle.NEEDLE_SENSOR).grantWithReceipt());
        }
    }

    public static boolean isOwned() {
        return TackleManager.isOwned(Tackle.NEEDLE_SENSOR);
    }

    // Run when the Outfitter closes, where ownership is final (its undo refunds without passing ShopEntry), and when
    // the schematic is given.
    public static void checkBought() {
        if (!FishermanChain.isAt(FishermanChain.Stage.T2_SENSOR) || !isOwned()) return;

        FishermanChain.setStage(FishermanChain.Stage.T2_SENSOR_BOUGHT);
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(SensorIntel.class)) {
            FishIntelNotifications.update((SensorIntel) intel, null);
        }
    }
}
