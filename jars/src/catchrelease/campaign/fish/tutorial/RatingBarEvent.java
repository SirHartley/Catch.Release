package catchrelease.campaign.fish.tutorial;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;


public class RatingBarEvent {


    public static class VisitCounter implements ColonyInteractionListener {

        public static void register() {
            Global.getSector().getListenerManager().removeListenerOfClass(VisitCounter.class);
            Global.getSector().getListenerManager().addListener(new VisitCounter(), true);
        }


        @Override
        public void reportPlayerOpenedMarket(MarketAPI market) {
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
