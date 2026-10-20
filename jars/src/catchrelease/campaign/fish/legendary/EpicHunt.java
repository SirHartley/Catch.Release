package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;

// Epics hunt in two stages through the legendary shield entry points. State lives on each fish because epics,
// unlike legendaries, are not unique and have no chase ledger.
public final class EpicHunt {

    public static final float RESET_RANGE = 3000f;
    public static final float ACTIVE_WANDER_MULT = 1.6f;

    private EpicHunt() {
    }

    public static boolean applies(FishEntityPlugin fish) {
        if (fish == null || fish.isPhantom() || fish.isDecoy() || fish.getOrbitAnchor() != null) return false;

        FishSpec spec = fish.getFishSpec();
        return spec != null && spec.rarity == FishRarity.EPIC;
    }

    public static boolean isActive(FishEntityPlugin fish) {
        return applies(fish) && fish.isEpicActive();
    }

    // The waking hit is deflected and leaves the one-charge shield intact; the next hit spends it for good.
    public static LegendaryShields.HitResult onHarpoonContact(FishEntityPlugin fish) {
        SectorEntityToken mote = fish.getMote();

        if (!fish.isEpicActive()) {
            fish.setEpicStage(true, false);
            fish.flashShield();
            LegendaryShields.say(mote, "Deflected - Now awake");
            return LegendaryShields.HitResult.DEFLECTED;
        }

        if (fish.isEpicShieldSpent()) return LegendaryShields.HitResult.NONE;

        fish.setEpicStage(true, true);
        fish.flashShield();
        LegendaryShields.say(mote, "Deflected");
        return LegendaryShields.HitResult.DEFLECTED;
    }

    public static boolean isShielded(FishEntityPlugin fish) {
        return isActive(fish) && !fish.isEpicShieldSpent();
    }

    public static float getSpeedMult(FishEntityPlugin fish) {
        if (!applies(fish)) return 1f;

        return fish.isEpicActive() ? 1f : LegendaryShields.LAZY_SPEED_MULT;
    }

    public static float getWanderMult(FishEntityPlugin fish) {
        return isActive(fish) ? ACTIVE_WANDER_MULT : 1f;
    }

    // Leaving the player behind ends the hunt: back to drifting, and the next wake raises a fresh shield.
    public static void advance(FishEntityPlugin fish) {
        if (!isActive(fish)) return;

        SectorEntityToken mote = fish.getMote();
        CampaignFleetAPI player = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (mote == null || player == null || mote.getContainingLocation() != player.getContainingLocation()
                || Misc.getDistance(mote.getLocation(), player.getLocation()) > RESET_RANGE) {
            fish.setEpicStage(false, false);
        }
    }

    // A slipped hook leaves the epic awake and in play, as with legendaries; the spent shield stays spent.
    public static boolean onFailedCatch(FishEntityPlugin fish) {
        if (!isActive(fish)) return false;

        fish.setHeld(false);
        return true;
    }
}
