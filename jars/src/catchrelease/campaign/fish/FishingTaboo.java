package catchrelease.campaign.fish;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;

public class FishingTaboo {

    public static boolean isTaboo(String factionId) {
        return Factions.LUDDIC_CHURCH.equals(factionId) || Factions.LUDDIC_PATH.equals(factionId);
    }

    public static boolean isTaboo(MarketAPI market) {
        return market != null && isTaboo(market.getFactionId());
    }

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
