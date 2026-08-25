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

public class CoherenceOverlayScript implements EveryFrameScript {

    protected float level = 0f;
    protected float aberration = 0f;
    protected float pull = 0f;

    // session-only: a legendary haunt drives the overlay to full force through this
    protected static float hauntFloor = 0f;

    public CoherenceOverlayScript() {
        // the script is rebuilt on every load; a floor from another save must not survive it
        hauntFloor = 0f;
    }

    public static float getLevel() {
        return CoherenceOverlayRenderer.getLevel();
    }

    public static void setHauntFloor(float floor) {
        hauntFloor = MathUtils.clamp(floor, 0f, 1f);
    }

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

        if (level > 0f) CoherenceTerrain.ensureIn(fleet.getContainingLocation());

        if (level > 0f && here() >= 0.3f && !catchrelease.campaign.fish.crab.CrabWares.EARMUFFS.isOn()) {
            Global.getSoundPlayer().playLoop(FishConstants.SOUND_COHERENCE_WHISPERS, fleet, 1f,
                    FishConstants.COHERENCE_WHISPER_VOLUME * level * here(), fleet.getLocation(), Misc.ZERO);
        }
    }

    protected float getTargetLevel() {
        if (Global.getSector().getCampaignUI().isShowingDialog()) return 0f;
        if (Global.getSector().getCampaignUI().isShowingMenu()) return 0f;

        float target = FishingRigs.isAnyRunning() ? levelFor(here()) : 0f;

        target = Math.max(target, getPondWeight() * levelFor(here()));
        target = Math.max(target, getBoatWeight() * Math.max(levelFor(here()), boatMinimum()));
        target = Math.max(target, hauntFloor);

        return target;
    }

    protected float here() {
        return Math.min(1f, aberration * (1f + FishConstants.ABERRATION_LOCAL_LIFT * pull));
    }

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

    protected static float boatMinimum() {
        return MathUtils.clamp(
                FishConstants.COHERENCE_FISHERMAN_ABERRATION / FishConstants.COHERENCE_OVERLAY_CEIL,
                0f, 1f);
    }

    protected static float falloff(float distance, float range) {
        if (distance >= range || range <= 0f) return 0f;

        float near = 1f - distance / range;

        return near * near;
    }

    public static float levelFor(float aberration) {
        return MathUtils.clamp((aberration - FishConstants.COHERENCE_OVERLAY_FLOOR)
                / (FishConstants.COHERENCE_OVERLAY_CEIL - FishConstants.COHERENCE_OVERLAY_FLOOR),
                0f, 1f);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }
}
