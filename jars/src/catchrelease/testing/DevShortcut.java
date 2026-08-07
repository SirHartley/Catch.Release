package catchrelease.testing;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

/**
 * One key that puts a campaign where a tester needs it: every rig in hand, the shop open, and a
 * spread of charts to read.
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

        for (InputEventAPI event : events) {
            if (event.isConsumed() || !event.isKeyDownEvent()) continue;
            if (Character.toLowerCase(event.getEventChar()) != KEY) continue;

            //taken rather than passed on, so the press cannot also mean something else this frame
            event.consume();

            fire();
            return;
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {

    }

    /** Everything the introduction would have granted, plus charts of every rung to test with. */
    protected void fire() {
        FishingIntro.skip(null);

        for (int rung = 0; rung < TutorialConstants.GRADUATION_CHARTS.length; rung++) {
            FishingIntro.giveChartsOfRarity(FishRarity.values()[rung],
                    TutorialConstants.GRADUATION_CHARTS[rung]);
        }

        Global.getSector().getCampaignUI().addMessage(
                "Dev shortcut: fishing gear granted, tutorial skipped, charts issued.",
                Misc.getHighlightColor());
    }
}
