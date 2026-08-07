package catchrelease.campaign.fish.tutorial;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * The one thing the bar rating needs that a sheet cannot count: how many ports the player has
 * walked into.
 * <p>
 * The rating himself is entirely in {@code rules.csv} - an {@code AddBarEvents} row with its own
 * blurb, option and reply, gated on this number and on the introduction not having started. Two
 * ways to meet one person: marooned on a surveyed rock he is a scene, in a bar he is a tip, and a
 * player who never surveys an empty world would otherwise only ever meet the trade by flying into
 * it. Both hand over the same thing, a direction, and both are optional.
 */
public class RatingBarEvent {

    /** Counts the markets walked into, which is all "not the very first port" needs. */
    public static class VisitCounter implements ColonyInteractionListener {

        public static void register() {
            Global.getSector().getListenerManager().removeListenerOfClass(VisitCounter.class);
            Global.getSector().getListenerManager().addListener(new VisitCounter(), true);
        }

        /** Stops counting once somebody has pointed the player at a boat; nothing asks after that. */
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
