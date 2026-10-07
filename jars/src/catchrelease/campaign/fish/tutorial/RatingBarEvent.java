package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.FishRumors;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

public class RatingBarEvent {

    public static final String REPORT_KEY = "$catchrelease_ratingReport";

    public static void prepareReport() {
        if (!FishRumors.areLegendaryReportsUnlocked() || !FishRumors.isAvailable()) return;
        if (getReport() != null) return;

        FishRumors.Saved report = FishRumors.rollLegendary();
        if (report != null) Global.getSector().getPersistentData().put(REPORT_KEY, report);
        else Global.getSector().getPersistentData().remove(REPORT_KEY);
    }

    public static FishRumors.Saved getReport() {
        if (!FishRumors.areLegendaryReportsUnlocked() || !FishRumors.isAvailable()) return null;

        Object stored = Global.getSector().getPersistentData().get(REPORT_KEY);
        if (!(stored instanceof FishRumors.Saved report) || FishRumors.isExpired(report)) return null;
        for (FishRumors.Saved active : FishRumors.getActiveRumors()) {
            if (report.legendaryId.equals(active.legendaryId)) return null;
        }
        return report;
    }

    public static FishRumors.Saved hearReport() {
        FishRumors.Saved report = getReport();
        if (report == null) return null;
        FishRumors.Saved published = FishRumors.publish(report);
        Global.getSector().getPersistentData().remove(REPORT_KEY);
        return published;
    }

    public static class VisitCounter implements ColonyInteractionListener {

        public static void register() {
            Global.getSector().getListenerManager().removeListenerOfClass(VisitCounter.class);
            Global.getSector().getListenerManager().addListener(new VisitCounter(), true);
        }

        @Override
        public void reportPlayerOpenedMarket(MarketAPI market) {
            // Generate outside AddBarEvents conditions so rebuilding a menu cannot reroll a sighting.
            prepareReport();
            if (FishingIntro.isAtLeast(FishingIntro.POINTED)) return;

            MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();

            memory.set(TutorialConstants.MARKETS_SEEN_KEY,
                    memory.getInt(TutorialConstants.MARKETS_SEEN_KEY) + 1);
        }

        @Override
        public void reportPlayerClosedMarket(MarketAPI market) {
        }

        @Override
        public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        }

        @Override
        public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        }
    }
}
