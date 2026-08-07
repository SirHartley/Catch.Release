package catchrelease.testing;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

/**
 * One key that puts a campaign where a tester needs it: every rig in hand, the shop open, and a
 * spread of charts to read.
 * <p>
 * Nothing downstream of the introduction can be looked at until the introduction is behind you -
 * the shop shelves only gear that has been granted, no job of any kind is offered before the first
 * errand, and the chart counter does not open at all. Which means that testing a change to the
 * catch minigame, the harpoon, or the codex otherwise costs the same fifteen minutes of fetching
 * commons every time, in a fresh save, before the thing being tested is reachable. This is that
 * fifteen minutes, spent once, in a keypress.
 * <p>
 * It leans on {@link FishingIntro#skip} rather than granting anything itself, so there is exactly
 * one description of what a finished introduction looks like and this cannot drift away from it.
 * The charts are the one addition: skipping pays commons only, and a dev campaign wants something
 * of every rung on the ladder to look at, so the graduation package goes on top.
 * <p>
 * Dev mode gates the whole thing, and the gate is checked every frame rather than at registration -
 * dev mode is a settings toggle a player can turn on mid-session, and a shortcut that only exists
 * if it happened to be on at load would be a worse surprise than one that appears when asked for.
 * Transient, holding no state a save would want, and registered by hand: it is a workshop tool and
 * has no business in {@code ModPlugin}'s ordinary boot for a shipped build.
 */
public class DevShortcut implements EveryFrameScript {

    /**
     * The physical key marked Ü on a German QWERTZ board.
     * <p>
     * Not a typo. LWJGL reports scancodes rather than characters, and the scancode names are the
     * US layout's - the key that types Ü here is the key that types [ there, so the constant to
     * match it with is {@code KEY_LBRACKET}.
     */
    public static final int KEY = Keyboard.KEY_LBRACKET;

    /** Last frame's state of that key, so the shortcut fires on the press and not on the hold. */
    protected boolean wasDown = false;

    public static void register() {
        Global.getSector().addTransientScript(new DevShortcut());
    }

    @Override
    public boolean isDone() {
        return false;
    }

    /**
     * True, unlike everything else in the mod: half of what this is for is setting a campaign up
     * while it sits paused, and a shortcut that needs time running to answer is half a shortcut.
     */
    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (!Global.getSettings().isDevMode()) return;

        boolean down = Keyboard.isKeyDown(KEY);
        boolean pressed = down && !wasDown;
        wasDown = down;

        if (!pressed) return;

        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null) return;

        //anything that owns the keyboard hands this key straight through otherwise, and the ways
        //to be typing are not only conversations: a ship being renamed in refit, a codex search, a
        //colony being named. All three are a core tab rather than a dialog, so all three are asked
        //about - a rename ending in Ü should not skip the tutorial
        if (ui.isShowingDialog() || ui.isShowingMenu()) return;
        if (ui.getCurrentCoreTab() != null) return;

        fire(ui);
    }

    /** Everything the introduction would have granted, plus one chart of every rung to test with. */
    protected void fire(CampaignUIAPI ui) {
        FishingIntro.skip(null);

        for (int rung = 0; rung < TutorialConstants.GRADUATION_CHARTS.length; rung++) {
            FishingIntro.giveChartsOfRarity(FishRarity.values()[rung],
                    TutorialConstants.GRADUATION_CHARTS[rung]);
        }

        ui.addMessage("Dev shortcut: fishing gear granted, tutorial skipped, charts issued.",
                Misc.getHighlightColor());
    }
}
