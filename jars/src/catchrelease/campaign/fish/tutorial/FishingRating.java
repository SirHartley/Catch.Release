package catchrelease.campaign.fish.tutorial;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson;
import com.fs.starfarer.api.util.Misc;

import java.util.Map;

/**
 * A rating off one of the boats, in a bar, with a story about their old berth.
 * <p>
 * The second way in, and the one that requires nothing of the player - the wreck has to be stumbled
 * over and a fishing boat has to be hailed, but everybody ends up in a bar. It waits until the
 * player has walked into a couple of markets, so it is not the first thing a new campaign says.
 * <p>
 * Not a {@link catchrelease.campaign.fish.jobs.FishJob}: nothing is asked and nothing is owed, so
 * there is no mission to run and no {@code rules.csv} rows to write - the same reasoning that keeps
 * Crablobab out of the sheet. It hands over exactly what the transponder hands over, a direction,
 * and then it is done for the campaign.
 */
public class FishingRating extends BaseBarEventWithPerson {

    public static final String OPTION_LISTEN = "catchrelease_ratingListen";
    public static final String OPTION_ASK = "catchrelease_ratingAsk";
    public static final String OPTION_LEAVE = "catchrelease_ratingLeave";

    /**
     * Counts the markets the player has walked into, which is all the "second planet" needs.
     * <p>
     * Registered as a listener rather than checked against anything vanilla keeps, because vanilla
     * does not keep it. Stops counting once the introduction is under way - the number is only ever
     * asked about before that.
     */
    public static class VisitCounter implements ColonyInteractionListener {

        public static void register() {
            Global.getSector().getListenerManager()
                    .removeListenerOfClass(VisitCounter.class);
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

    public static int getMarketsSeen() {
        return Global.getSector().getMemoryWithoutUpdate()
                .getInt(TutorialConstants.MARKETS_SEEN_KEY);
    }

    @Override
    protected Gender getPersonGender() {
        return Gender.ANY;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) return false;

        if (FishingIntro.isAtLeast(FishingIntro.POINTED)) return false;

        return getMarketsSeen() >= TutorialConstants.MARKETS_BEFORE_APPROACH;
    }

    /** Once the player knows, there is nothing left for them to say, ever. */
    @Override
    public boolean shouldRemoveEvent() {
        return FishingIntro.isAtLeast(FishingIntro.POINTED);
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);

        dialog.getTextPanel().addPara("Somebody at the next table has been listening to you for a"
                + " while, and has decided to stop pretending they haven't. Deck hands' coveralls,"
                + " no ship patch, and a drink they are making last.");

        dialog.getOptionPanel().addOption("See what they want", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);

        done = false;

        dialog.getVisualPanel().showPersonInfo(person, true);

        text.addPara("\"You're heading out past the picket, aren't you. Everyone"
                + " who does has that look.\" They turn the glass around. \"I used to crew a boat"
                + " that went further.\"");

        showOptions();
    }

    protected void showOptions() {
        options.clearOptions();

        options.addOption("\"What kind of boat?\"", OPTION_LISTEN);
        options.addOption("\"Where would I find one?\"", OPTION_ASK, Misc.getHighlightColor(),
                null);
        options.addOption("Make your excuses", OPTION_LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPTION_LISTEN.equals(optionData)) {
            text.addPara("\"A fishing boat. I know how that sounds.\" They are not"
                    + " smiling. \"There are holes in the fabric out there and there are things"
                    + " living in them, and somebody worked out you can put a line in one.\"");

            text.addPara("\"I lasted two seasons. It's the water that gets you -"
                    + " not the catch. Nothing out there sits still enough to look at twice.\"",
                    Misc.getGrayColor());

            showOptions();
            return;
        }

        if (OPTION_ASK.equals(optionData)) {
            text.addPara("\"They keep a boat in every system anybody lives in."
                    + " Out past the last colony, where they're not in the way of anything. Hail"
                    + " one and ask for the science end - the captain doesn't explain things.\"");

            FishingIntro.point();

            text.addPara("An intel note tracks the nearest one.", Misc.getGrayColor());

            if (BarEventManager.getInstance() != null) {
                BarEventManager.getInstance().notifyWasInteractedWith(this);
            }

            options.clearOptions();
            options.addOption("Thank them", OPTION_LEAVE);
            return;
        }

        text.addPara("\"Suit yourself.\" They go back to the drink, and to making it last.");

        done = true;
    }
}
