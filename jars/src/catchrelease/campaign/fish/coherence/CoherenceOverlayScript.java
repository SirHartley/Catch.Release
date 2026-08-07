package catchrelease.campaign.fish.coherence;

import catchrelease.abilities.FishingRigs;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
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
 * Shows while a rig is running ({@link FishingRigs}) <i>or</i> a fishing boat is alongside, in water
 * aberrated enough to matter, and eases out under any dialog or core UI screen - the minigame is
 * hosted as a dialog, and a screen warping under it would fight the very track the player is trying
 * to read.
 * <p>
 * Transient, registered every load from ModPlugin; the level starts at 0 and earns its way up.
 */
public class CoherenceOverlayScript implements EveryFrameScript {

    protected float level = 0f;

    /**
     * The eased level the overlay is currently at, for anything that has to agree with the screen.
     * <p>
     * Read off the renderer rather than kept here: the renderer is the one thing that survives
     * being asked from anywhere, and a second copy on a transient script is a second copy to get
     * out of step. {@link CoherenceTerrain} is what asks - it is inside the fabric exactly when the
     * screen says it is.
     */
    public static float getLevel() {
        return CoherenceOverlayRenderer.getLevel();
    }

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

        //the terrain bar is built from terrain the fleet is standing in, so the readout needs
        //something to stand in wherever the rigs are being run. It takes itself away again
        if (level > 0f) CoherenceTerrain.ensureIn(fleet.getContainingLocation());

        //refreshed every frame or the engine fades it out itself; volume rides the level, so the
        //whispers arrive and leave with the warp
        if (level > 0f) {
            Global.getSoundPlayer().playLoop(FishConstants.SOUND_COHERENCE_WHISPERS, fleet, 1f,
                    FishConstants.COHERENCE_WHISPER_VOLUME * level, fleet.getLocation(), Misc.ZERO);
        }
    }

    /**
     * Zero with nothing going on, zero under any dialog or menu, else the place's own level.
     * <p>
     * Two things count as something going on. A rig running is the player doing something to the
     * fabric. A fishing boat close by is the fabric having already done something to somebody -
     * whatever is aboard is what bad water does given long enough - and in water bad enough to
     * matter, coming alongside one should turn the screen over exactly the way a lamp does.
     */
    protected float getTargetLevel() {
        if (Global.getSector().getCampaignUI().isShowingDialog()) return 0f;
        if (Global.getSector().getCampaignUI().isShowingMenu()) return 0f;

        if (!FishingRigs.isAnyRunning() && !isNearFishingBoat()) return 0f;

        return levelFor(aberration);
    }

    /** Whether one of the trade's boats is within reach of the player, wherever they are. */
    protected boolean isNearFishingBoat() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() == null) return false;

        for (CampaignFleetAPI fleet : player.getContainingLocation().getFleets()) {
            if (!FishermanSpawner.isFisherman(fleet)) continue;

            if (Misc.getDistance(player.getLocation(), fleet.getLocation())
                    <= FishConstants.COHERENCE_FISHERMAN_RANGE) {

                return true;
            }
        }

        return false;
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
