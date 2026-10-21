package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.jobs.QuestPond;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.function.BiConsumer;

// Epics hunt in two stages through the legendary shield entry points. State lives on each fish because epics,
// unlike legendaries, are not unique and have no chase ledger.
public final class EpicHunt {

    public static final float RESET_RANGE = 3000f;
    public static final float DRIFT_OFF_DISTANCE = 500f;
    public static final float ACTIVE_WANDER_MULT = 1.2f;

    // Awake epics never fade at their swim target, so the ones left behind when the player departs are removed.
    public static class DepartureCleanup implements CurrentLocationChangedListener {

        @Override
        public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
            if (prev == null || prev == curr) return;

            for (SectorEntityToken mote : new ArrayList<>(prev.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG))) {
                if (mote.isExpired() || !(mote.getCustomPlugin() instanceof FishEntityPlugin fish)
                        || !isActive(fish)) continue;

                mote.setExpired(true);
                reportReset(mote);
            }
        }
    }

    private EpicHunt() {
    }

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        listeners.removeListenerOfClass(DepartureCleanup.class);
        listeners.addListener(new DepartureCleanup(), true);
    }

    public static boolean applies(FishEntityPlugin fish) {
        if (fish == null || fish.isPhantom() || fish.isDecoy() || fish.getOrbitAnchor() != null) return false;

        FishSpec spec = fish.getFishSpec();
        return spec != null && spec.rarity == FishRarity.EPIC;
    }

    public static boolean isActive(FishEntityPlugin fish) {
        return applies(fish) && fish.isEpicActive();
    }

    // The waking hit is deflected and leaves the one-charge shield intact; the next hit spends it for good, and a
    // reset never restores it.
    public static LegendaryShields.HitResult onHarpoonContact(FishEntityPlugin fish) {
        if (fish.isEpicShieldSpent()) return LegendaryShields.HitResult.NONE;

        SectorEntityToken mote = fish.getMote();

        if (!fish.isEpicActive()) {
            fish.setEpicStage(true, false);
            fish.flashShield();
            LegendaryShields.say(mote, "Deflected - Now awake");
            return LegendaryShields.HitResult.DEFLECTED;
        }

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

    // Wander swings the heading on a clock, not per distance travelled: at drift speed the full swing would curl the
    // path into loops, so a dormant epic's swing shrinks with its speed.
    public static float getWanderMult(FishEntityPlugin fish) {
        if (!applies(fish)) return 1f;

        return fish.isEpicActive() ? ACTIVE_WANDER_MULT : LegendaryShields.LAZY_SPEED_MULT;
    }

    // Outdistancing the player ends the hunt: back to drifting, with the shield as it was, on an awake leg cut short
    // so the fish fades at its end. An awake epic in a system the player has left is removed instead, like
    // DepartureCleanup does. Whichever of the two sees the departure first removes the fish; the other skips the
    // expired mote, so the reset is reported once.
    public static void advance(FishEntityPlugin fish) {
        if (!isActive(fish)) return;

        SectorEntityToken mote = fish.getMote();
        CampaignFleetAPI player = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (mote == null || mote.isExpired() || player == null) return;

        if (mote.getContainingLocation() != player.getContainingLocation()) {
            mote.setExpired(true);
            reportReset(mote);
        } else if (Misc.getDistance(mote.getLocation(), player.getLocation()) > RESET_RANGE) {
            fish.setEpicStage(false, fish.isEpicShieldSpent());
            fish.shortenSwim(DRIFT_OFF_DISTANCE);
            reportReset(mote);
        }
    }

    public static void reportReset(SectorEntityToken mote) {
        report(mote, (listener, planter) -> listener.reportQuestEpicReset(mote, planter));
    }

    public static void reportResolved(SectorEntityToken mote, FishCatch caught) {
        if (caught == null) {
            report(mote, (listener, planter) -> listener.reportQuestEpicEscaped(mote, planter));
        } else {
            report(mote, (listener, planter) -> listener.reportQuestEpicCaught(mote, planter, caught));
        }
    }

    public static void reportBlasted(SectorEntityToken mote) {
        report(mote, (listener, planter) -> listener.reportQuestEpicBlasted(mote, planter));
    }

    protected static void report(SectorEntityToken mote, BiConsumer<QuestEpicListener, String> event) {
        if (mote == null || !(mote.getCustomPlugin() instanceof FishEntityPlugin fish) || !applies(fish)) return;
        if (!QuestPond.isQuestMote(mote) || Global.getSector() == null) return;

        String planter = QuestPond.getPlanter(mote);
        for (QuestEpicListener listener
                : Global.getSector().getListenerManager().getListeners(QuestEpicListener.class)) {
            event.accept(listener, planter);
        }
    }
}
