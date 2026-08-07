package catchrelease.campaign.fish.coherence;

import catchrelease.abilities.FishingRigs;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.rendering.plugins.CoherenceOverlayRenderer;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

/**
 * Decides when the low-coherence overlay shows and how hard, and keeps the whisper loop fed.
 * Drawing is {@link CoherenceOverlayRenderer}'s; this owns the rules.
 * <p>
 * Shows only while a rig is running ({@link FishingRigs}) somewhere aberrated enough to matter,
 * and eases out under any dialog or core UI screen - the minigame is hosted as a dialog, and a
 * screen warping under it would fight the very track the player is trying to read.
 * <p>
 * Transient, registered every load from ModPlugin; the level starts at 0 and earns its way up.
 */
public class CoherenceOverlayScript implements EveryFrameScript {

    protected float level = 0f;

    /** The place's steady reading ({@link Aberration#baseAt} - no per-catch jitter). Cached on an
     *  interval: the read walks every system and slipstream, and the answer only moves when the
     *  fleet does - in light-years. */
    protected float aberration = 0f;
    protected boolean aberrationRead = false;
    protected IntervalUtil recheck = new IntervalUtil(1f, 1f);

    @Override
    public void advance(float amount) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        recheck.advance(amount);
        if (!aberrationRead || recheck.intervalElapsed()) {
            aberration = Aberration.baseAt(fleet.getLocationInHyperspace(),
                    fleet.getContainingLocation());
            aberrationRead = true;
        }

        float target = getTargetLevel();

        if (level < target) {
            level = Math.min(target, level + amount / FishConstants.COHERENCE_OVERLAY_EASE_IN);
        } else {
            level = Math.max(target, level - amount / FishConstants.COHERENCE_OVERLAY_EASE_OUT);
        }

        CoherenceOverlayRenderer.setLevel(level);

        //refreshed every frame or the engine fades it out itself; volume rides the level, so the
        //whispers arrive and leave with the warp
        if (level > 0f) {
            Global.getSoundPlayer().playLoop(FishConstants.SOUND_COHERENCE_WHISPERS, fleet, 1f,
                    FishConstants.COHERENCE_WHISPER_VOLUME * level, fleet.getLocation(), Misc.ZERO);
        }
    }

    /** Zero with no rig running, zero under any dialog or menu, else the place's own level. */
    protected float getTargetLevel() {
        if (Global.getSector().getCampaignUI().isShowingDialog()) return 0f;
        if (Global.getSector().getCampaignUI().isShowingMenu()) return 0f;

        if (!FishingRigs.isAnyRunning()) return 0f;

        return levelFor(aberration);
    }

    /** Aberration to overlay level 0-1: nothing through "stable", full by "barely holding" -
     *  FLOOR and CEIL are the same cuts the coherence labels use, so what the screen does always
     *  matches what a specimen from here would say. */
    public static float levelFor(float aberration) {
        return MathUtils.clamp((aberration - FishConstants.COHERENCE_OVERLAY_FLOOR)
                / (FishConstants.COHERENCE_OVERLAY_CEIL - FishConstants.COHERENCE_OVERLAY_FLOOR),
                0f, 1f);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    /** Dialogs pause the game, and the ease-out has to run exactly then. */
    @Override
    public boolean runWhilePaused() {
        return true;
    }
}
