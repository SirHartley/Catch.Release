package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.OuterReaches;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;


public class Castaway {

    public static boolean isPlaced() {
        return Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.CASTAWAY_PLACED_KEY);
    }


    public static boolean isHost(PlanetAPI planet) {
        if (planet == null || planet.getMarket() == null) return false;

        return planet.getMarket().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.CASTAWAY_HOST_KEY)
                && !planet.getMarket().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.CASTAWAY_RESCUED_KEY);
    }


    public static boolean canStart(PlanetAPI host) {
        return host != null
                && host.getStarSystem() != null
                && host.getMarket() != null
                && host.getMarket().getSurveyLevel() != MarketAPI.SurveyLevel.FULL
                && !isPlaced()
                && !FishingIntro.isAtLeast(FishingIntro.POINTED)
                && !OuterReaches.isPopulated(host.getStarSystem());
    }


    public static boolean isEligible(PlanetAPI planet) {
        return isHost(planet) || canStart(planet);
    }


    public static boolean start(PlanetAPI host) {
        if (isHost(host)) return true;
        if (!canStart(host)) return false;

        Global.getSector().getMemoryWithoutUpdate()
                .set(TutorialConstants.CASTAWAY_PLACED_KEY, true);
        host.getMarket().getMemoryWithoutUpdate().set(TutorialConstants.CASTAWAY_HOST_KEY, true);
        host.getMarket().getMemoryWithoutUpdate().unset(TutorialConstants.CASTAWAY_RESCUED_KEY);

        return true;
    }


    public static boolean rescue(SectorEntityToken target) {
        FishingIntro.point();

        if (target instanceof PlanetAPI planet && planet.getMarket() != null) {
            planet.getMarket().getMemoryWithoutUpdate()
                    .set(TutorialConstants.CASTAWAY_RESCUED_KEY, true);
        }

        return true;
    }
}
