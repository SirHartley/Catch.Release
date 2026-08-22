package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.OuterReaches;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * The one crewman who was put off the boat, and what is left of him.
 * <p>
 * Found the way vanilla's mission targets are found: interacting with an eligible planet is taken
 * over by his scene - a score-boosted {@code OpenInteractionDialog} row on the sheet, the same
 * mechanism the Academy's own planet-bound errands use - before the ordinary survey menu gets a
 * word in. The first uninhabited, unsurveyed world the player looks at somewhere nobody lives
 * becomes his; no entity is ever spawned, the planet itself carries the scene as market memory.
 * He was exiled for trying to look at the catch. He has been alive on rum out of a smuggler's
 * cache ever since, is barely coherent, and has exactly one useful sentence in him.
 * <p>
 * Deliberately not the whole story. He does not explain what he saw and could not if he wanted to;
 * the only thing he can still do is point, and the pointing is the hook.
 * <p>
 * Leaving without offering passage leaves the host flags standing, so the next interaction with
 * that world opens on him again; the rescue closes the scene for good and points the tutorial.
 */
public class Castaway {

    public static boolean isPlaced() {
        return Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.CASTAWAY_PLACED_KEY);
    }

    /** Whether this world already hosts him and he is still waiting on it. */
    public static boolean isHost(PlanetAPI planet) {
        if (planet == null || planet.getMarket() == null) return false;

        return planet.getMarket().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.CASTAWAY_HOST_KEY)
                && !planet.getMarket().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.CASTAWAY_RESCUED_KEY);
    }

    /** Whether this unsurveyed, uninhabited world may host the one remaining first-contact scene. */
    public static boolean canStart(PlanetAPI host) {
        return host != null
                && host.getStarSystem() != null
                && host.getMarket() != null
                && host.getMarket().getSurveyLevel() != MarketAPI.SurveyLevel.FULL
                && !isPlaced()
                && !FishingIntro.isAtLeast(FishingIntro.POINTED)
                && !OuterReaches.isPopulated(host.getStarSystem());
    }

    /** The sheet's gate for taking the planet's dialog over: his world, or the first fit one. */
    public static boolean isEligible(PlanetAPI planet) {
        return isHost(planet) || canStart(planet);
    }

    /** Claims the host before the rules route into the scene; a no-op on his own world. */
    public static boolean start(PlanetAPI host) {
        if (isHost(host)) return true;
        if (!canStart(host)) return false;

        Global.getSector().getMemoryWithoutUpdate()
                .set(TutorialConstants.CASTAWAY_PLACED_KEY, true);
        host.getMarket().getMemoryWithoutUpdate().set(TutorialConstants.CASTAWAY_HOST_KEY, true);
        host.getMarket().getMemoryWithoutUpdate().unset(TutorialConstants.CASTAWAY_RESCUED_KEY);

        return true;
    }

    /** Marks the planet-hosted scene complete without ever touching the planet entity itself. */
    public static boolean rescue(SectorEntityToken target) {
        FishingIntro.point();

        if (target instanceof PlanetAPI planet && planet.getMarket() != null) {
            planet.getMarket().getMemoryWithoutUpdate()
                    .set(TutorialConstants.CASTAWAY_RESCUED_KEY, true);
        }

        return true;
    }
}
