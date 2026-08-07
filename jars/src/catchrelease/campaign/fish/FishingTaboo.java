package catchrelease.campaign.fish;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;

/**
 * Who will not have anything to do with fishing, and what that rules out.
 * <p>
 * The Church and the Path are not squeamish about fish. They are against the water. A rupture is a
 * hole opened between here and hyperspace, and whatever is pulled through it was never born on the
 * right side of that hole - the whole trade is people making a living off a wound in creation and
 * calling the wound a fishing spot. The Church has a word for that and the Path has a method.
 * <p>
 * So neither of them produces fishers, buyers, brokers, or anybody in a bar with a favour to ask
 * about it. There is no Luddic fishing job, no Luddic boat working a core system, and nobody in a
 * Church port who wants a specimen for any reason. What they do produce is everything in
 * {@code campaign/crime} - patrols that stop you, cells that sit on ruptures so nobody can work
 * them - and that is the only shape a Luddic interaction with this mod takes.
 * <p>
 * One list, read by everything that needs it, rather than the same two constants written out in
 * five places and forgotten in the sixth.
 */
public class FishingTaboo {

    /** Whether this flag is one of the two that will not touch the water at all. */
    public static boolean isTaboo(String factionId) {
        return Factions.LUDDIC_CHURCH.equals(factionId) || Factions.LUDDIC_PATH.equals(factionId);
    }

    /**
     * Whether nobody in this port would be sitting in the bar with fishing work.
     * <p>
     * The market's own flag and nothing cleverer. A Church world is a Church world regardless of who
     * else has a station in the system, and the question here is who is drinking in that bar.
     */
    public static boolean isTaboo(MarketAPI market) {
        return market != null && isTaboo(market.getFactionId());
    }

    /**
     * Whether this system is somewhere a fishing boat would not be working out of.
     * <p>
     * Judged on the largest market rather than on anything held anywhere, because those are two
     * different questions. A system the Church runs is a system where a trawler has nowhere to land
     * its catch and nobody to sell it to; a system the Hegemony runs that happens to contain a
     * monastery is still a working system, and a boat in it is somebody else's boat.
     */
    public static boolean holds(StarSystemAPI system) {
        if (system == null) return false;

        MarketAPI biggest = null;

        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (market.isPlanetConditionMarketOnly()) continue;

            if (biggest == null || market.getSize() > biggest.getSize()) biggest = market;
        }

        return isTaboo(biggest);
    }
}
