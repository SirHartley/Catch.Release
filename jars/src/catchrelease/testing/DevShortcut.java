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

/**
 * One key that puts a campaign where a tester needs it: every rig in hand, the shop open, a spread
 * of charts to read, every backdrop, and every outfitter schematic.
 * <p>
 * Nothing downstream of the introduction can be looked at until the introduction is behind you - the
 * shop only shelves gear that has been granted, no job of any kind is offered before the first
 * errand, and the chart counter does not open at all. So testing a change to the catch minigame, the
 * harpoon or the codex otherwise costs the same fifteen minutes of fetching commons every time, in a
 * fresh save, before the thing being tested is reachable. This is that fifteen minutes, spent once.
 * <p>
 * It leans on {@link FishingIntro#skip} rather than granting anything itself, so there is exactly one
 * description of what a finished introduction looks like and this cannot drift away from it. The
 * charts are the one addition: skipping pays commons only, and a dev campaign wants something of
 * every rung on the ladder to look at, so the graduation package goes on top.
 * <p>
 * A {@link CampaignInputListener} rather than a frame script polling the keyboard. The difference is
 * not tidiness - it is that this is the mechanism that actually knows what the key press <i>was</i>.
 * Events carry the typed character, so the key can be identified as the one marked Ü rather than by
 * the scancode that happens to sit under it on one layout; they can be {@link InputEventAPI#consume}
 * so nothing downstream sees the press again; and they arrive already sorted into the three passes
 * of the campaign's own input cycle, so "after the core UI has taken what it wants" is a hook rather
 * than a guess. Polling had none of that and had to reconstruct all three badly.
 * <p>
 * Registered transient. It holds nothing a save would want, and dev mode is read per press rather
 * than at registration, because it is a settings toggle that can be turned on mid-session.
 */
public class DevShortcut implements CampaignInputListener {

    /**
     * The key as the player's own keyboard produces it, which is the only description that survives
     * a change of layout. Compared case-insensitively so a stray shift is not a different key.
     */
    public static final char KEY = 'j';

    /** Behind vanilla's own listeners: this should be the last thing offered a key, not the first. */
    public static final int PRIORITY = -1000;

    public static void register() {
        Global.getSector().getListenerManager().addListener(new DevShortcut(), true);
    }

    @Override
    public int getListenerInputPriority() {
        return PRIORITY;
    }

    //claude you retard if you put this in postCore it'll only eat consumed events
    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        if (!Global.getSettings().isDevMode()) return;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String key =  "$" + ModPlugin.MOD_ID + "_devSkip";
        int amt = mem.contains(key) ? mem.getInt(key) : 0;

        for (InputEventAPI event : events) {
            if (event.isConsumed() || !event.isKeyDownEvent()) continue;
            if (Character.toLowerCase(event.getEventChar()) != KEY) continue;

            //taken rather than passed on, so the press cannot also mean something else this frame
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

    /**
     * Every scene in the table, whether or not the table hands it out for free.
     * <p>
     * {@code Backdrop.owned} is the row saying a conservatory has this one the day it is built,
     * which is the opposite of what a grant-me-everything key wants: reading it as a filter meant
     * this granted the two scenes the player already had and nothing else. There is nothing to
     * filter on - {@link Backdrops#own} is idempotent, so the ones already had cost a set lookup.
     */
    protected void addBackgrounds(){
        for (Backdrop backdrop : BackdropLoader.getAll()) Backdrops.own(backdrop.id);

        Global.getSector().getCampaignUI().addMessage(
                "Dev shortcut: All backdrops unlocked.",
                Misc.getHighlightColor());
    }

    /** Every stocked tackle plan and every schematic-gated upgrade rung, without buying either. */
    protected void addSchematics() {
        ShopSchematics.unlockAll();

        Global.getSector().getCampaignUI().addMessage(
                "Dev shortcut: all tackle and upgrade schematics unlocked.",
                Misc.getHighlightColor());
    }

    /** Everything the introduction would have granted, plus charts of every rung to test with. */
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
