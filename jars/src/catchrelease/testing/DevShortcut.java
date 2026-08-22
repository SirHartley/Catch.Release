package catchrelease.testing;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.campaign.fish.colony.Backdrops;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.helper.loading.BackdropLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

public class DevShortcut implements CampaignInputListener {

    public static final char KEY = 'j';
    public static final int PRIORITY = -1000;

    public static void register() {
        Global.getSector().getListenerManager().addListener(new DevShortcut(), true);
    }

    @Override
    public int getListenerInputPriority() {
        return PRIORITY;
    }

    // claude you retard if you put this in postCore it'll only eat consumed events
    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        if (!Global.getSettings().isDevMode()) return;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String key =  "$" + ModPlugin.MOD_ID + "_devSkip";
        int amt = mem.contains(key) ? mem.getInt(key) : 0;

        for (InputEventAPI event : events) {
            if (event.isConsumed() || !event.isKeyDownEvent()) continue;
            if (Character.toLowerCase(event.getEventChar()) != KEY) continue;

            // taken rather than passed on, so the press cannot also mean something else this frame
            event.consume();

            if (amt == 0) {
                addEquipment();
                mem.set(key, 1);
            }

            if (amt == 1) {
                addBackgrounds();
                mem.set(key, 2);
            }

            if (amt == 2) {
                addSchematics();
                mem.set(key, 3);
            }

            return;
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {
    }

    protected void addBackgrounds(){
        for (Backdrop backdrop : BackdropLoader.getAll()) Backdrops.own(backdrop.id);

        Global.getSector().getCampaignUI().addMessage(
                "Dev shortcut: All backdrops unlocked.",
                Misc.getHighlightColor());
    }

    protected void addSchematics() {
        ShopSchematics.unlockAll();

        Global.getSector().getCampaignUI().addMessage(
                "Dev shortcut: all module and upgrade tier schematics unlocked.",
                Misc.getHighlightColor());
    }

    protected void addEquipment() {
        FishingIntro.skip(null);

        for (int rung = 0; rung < TutorialConstants.GRADUATION_CHARTS.length; rung++) {
            FishingIntro.giveChartsOfRarity(FishRarity.ofRank(rung),
                    TutorialConstants.GRADUATION_CHARTS[rung]);
        }

        Global.getSector().getCampaignUI().addMessage(
                "Dev shortcut: fishing gear granted, tutorial skipped, charts issued.",
                Misc.getHighlightColor());
    }
}
