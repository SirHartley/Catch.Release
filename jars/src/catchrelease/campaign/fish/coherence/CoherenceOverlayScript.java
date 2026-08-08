package catchrelease.campaign.fish.coherence;

import catchrelease.abilities.FishingRigs;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import catchrelease.rendering.plugins.CoherenceOverlayRenderer;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

/**
 * Decides when the low-coherence overlay shows and how hard, and keeps the whisper loop fed.
 * Drawing is {@link CoherenceOverlayRenderer}'s; this owns the rules.
 * <p>
 * Shows while a rig is running ({@link FishingRigs}), near an open pond, <i>or</i> near a fishing
 * boat - the strongest of the three wins - in water aberrated enough to matter, and eases out under
 * any dialog or core UI screen - the minigame is hosted as a dialog, and a screen warping under it
 * would fight the very track the player is trying to read.
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

    /**
     * The place's steady reading ({@link Aberration#baseAt} - no per-catch jitter), and how near the
     * fleet is standing to whatever in this system is causing it.
     * <p>
     * Two figures because they answer at two speeds. The reading is a property of the system and is
     * cached there - it is the same number anywhere in it, and asking for it is a map lookup. The
     * pull is the part that changes as the fleet moves, and it is measured against that system's own
     * entities and nothing else, so it costs a short list walk rather than a crawl of the sector.
     * <p>
     * Both are taken every frame now. They used to be one figure on a one-second interval, because
     * the one figure cost six sector-wide entity crawls and could not be afforded any more often
     * than that; neither of these costs anything worth spacing out.
     */
    protected float aberration = 0f;
    protected float pull = 0f;

    @Override
    public void advance(float amount) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        aberration = Aberration.baseAt(fleet.getLocationInHyperspace(),
                fleet.getContainingLocation());
        pull = Aberration.localPull(fleet);

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
        //whispers arrive and leave with the warp. Earmuffs stop the loop being fed rather than
        //muting it - a loop at zero volume is still a loop the engine is keeping alive
        if (level > 0f && here() >= 0.3f && !catchrelease.campaign.fish.crab.CrabWares.EARMUFFS.isOn()) {
            Global.getSoundPlayer().playLoop(FishConstants.SOUND_COHERENCE_WHISPERS, fleet, 1f,
                    FishConstants.COHERENCE_WHISPER_VOLUME * level * here(), fleet.getLocation(), Misc.ZERO);
        }
    }

    /**
     * Zero with nothing going on, zero under any dialog or menu, else the strongest of the three
     * sources - a maximum, not a sum, so the loudest reason sets the level and the rest add nothing.
     * <p>
     * Three things count as something going on. A rig running is the player doing something to the
     * fabric, full weight wherever they are. An open pond is a hole already in it, weighted down
     * with distance. A fishing boat close by is the fabric having already done something to
     * somebody - whatever is aboard is what bad water does given long enough - weighted the same
     * way, but never fading to nothing alongside: the boat's vicinity is bad water even where the
     * system's reading is not ({@link FishConstants#COHERENCE_FISHERMAN_ABERRATION}).
     * <p>
     * All three ride {@link #here()} - the system's reading, lifted where the fleet is standing on
     * top of whatever in the system is causing it. A lift and not a scale: the system's reading is
     * what the water is worth everywhere in that system, and standing next to the thing responsible
     * is more than that rather than the only way to get any of it.
     */
    protected float getTargetLevel() {
        if (Global.getSector().getCampaignUI().isShowingDialog()) return 0f;
        if (Global.getSector().getCampaignUI().isShowingMenu()) return 0f;

        float target = FishingRigs.isAnyRunning() ? levelFor(here()) : 0f;

        target = Math.max(target, getPondWeight() * levelFor(here()));
        target = Math.max(target, getBoatWeight() * Math.max(levelFor(here()), boatMinimum()));

        return target;
    }

    /**
     * The system's steady reading, lifted by however near the fleet is to what causes it.
     * <p>
     * Scaling by the nearness instead - which this did for exactly one commit - takes the system's
     * own reading out of the answer altogether, because most systems are thin on account of
     * something outside them and nothing outside a system is a thing to stand near. Every one of
     * those came out flat at the floor, so the screen stopped responding to the water and answered
     * only to how close the rig was to a pond.
     */
    protected float here() {
        return Math.min(1f, aberration * (1f + FishConstants.ABERRATION_LOCAL_LIFT * pull));
    }

    /**
     * The strongest open pond's pull, 0-1: 1 anywhere on the water, 0 past
     * {@link FishConstants#COHERENCE_POND_RANGE_MULT} radii beyond its edge.
     * <p>
     * Measured from the surface rather than the centre, because a pond is an area and a big one
     * would otherwise read as half-strength while the player is stood on it with the rod out -
     * which is the case this replaced the rig-only gate to cover.
     */
    protected float getPondWeight() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() == null) return 0f;

        float best = 0f;

        for (SectorEntityToken pond : player.getContainingLocation()
                .getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID)) {

            MaskedFishingPondTerrainPlugin plugin = MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
            if (plugin == null || !plugin.isActive()) continue;

            float beyondEdge = Math.max(0f, Misc.getDistance(player, pond) - pond.getRadius());

            best = Math.max(best, falloff(beyondEdge,
                    pond.getRadius() * FishConstants.COHERENCE_POND_RANGE_MULT));
        }

        return best;
    }

    /** The nearest fishing boat's pull, 0-1: 1 alongside, 0 at
     *  {@link FishConstants#COHERENCE_FISHERMAN_RANGE}. */
    protected float getBoatWeight() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() == null) return 0f;

        float best = 0f;

        for (CampaignFleetAPI fleet : player.getContainingLocation().getFleets()) {
            if (!FishermanSpawner.isFisherman(fleet)) continue;

            best = Math.max(best, falloff(Misc.getDistance(player.getLocation(), fleet.getLocation()),
                    FishConstants.COHERENCE_FISHERMAN_RANGE));
        }

        return best;
    }

    /** The least level a boat shows at, with {@link #levelFor}'s floor bypassed - the floor would
     *  eat {@link FishConstants#COHERENCE_FISHERMAN_ABERRATION} whole and the boat must always show. */
    protected static float boatMinimum() {
        return MathUtils.clamp(
                FishConstants.COHERENCE_FISHERMAN_ABERRATION / FishConstants.COHERENCE_OVERLAY_CEIL,
                0f, 1f);
    }

    /** {@link Aberration}'s falloff curve, redone here because its own is protected: 1 on top of
     *  the thing, 0 at the given range, squared so most of the effect is close in. */
    protected static float falloff(float distance, float range) {
        if (distance >= range || range <= 0f) return 0f;

        float near = 1f - distance / range;

        return near * near;
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
