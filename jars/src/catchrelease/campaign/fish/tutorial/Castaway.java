package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.OuterReaches;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.SurveyPlanetListener;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

/**
 * The one crewman who was put off the boat, and what is left of him.
 * <p>
 * Turns up on a survey - the first world the player properly looks at somewhere nobody lives, which
 * is a thing every campaign does early and none of them does on purpose. He was exiled for trying to
 * look at the catch. He has been alive on rum out of a smuggler's cache ever since, is barely
 * coherent, and has exactly one useful sentence in him.
 * <p>
 * Deliberately not the whole story. He does not explain what he saw and could not if he wanted to;
 * the only thing he can still do is point, and the pointing is the hook.
 * <p>
 * The scene is injected into the planet's ordinary survey interaction, the same way the Academy's
 * survey package interrupts a survey. The custom entity class remains only so old saves can load
 * their beacon once, convert it to its host planet and retire it safely.
 */
public class Castaway extends BaseCustomEntityPlugin {

    /** Performs legacy-beacon migration on load; new surveys are handled by rules.csv. */
    public static class Watcher implements SurveyPlanetListener {

        public static void register() {
            Global.getSector().getListenerManager().removeListenerOfClass(Watcher.class);
            migrateLegacyBeacons();
        }

        @Override
        public void reportPlayerSurveyedPlanet(PlanetAPI planet) {
            //New saves enter through the surveyPerform rules hook. Kept as a no-op listener
            //implementation for binary compatibility with saves that serialised this watcher.
        }
    }

    public static boolean isPlaced() {
        return Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.CASTAWAY_PLACED_KEY);
    }

    /** Whether this unsurveyed, uninhabited world may host the one remaining first-contact scene. */
    public static boolean canStart(PlanetAPI host) {
        return host != null
                && host.getStarSystem() != null
                && !isPlaced()
                && !FishingIntro.isAtLeast(FishingIntro.POINTED)
                && !OuterReaches.isPopulated(host.getStarSystem());
    }

    /** Records the host before the rules route into the preserved conversation. */
    public static boolean start(PlanetAPI host) {
        if (!canStart(host) || host.getMarket() == null) return false;

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
            return true;
        }

        //Only the old beacon is allowed to fade. A planet is a campaign object, not a cache.
        if (isCastaway(target)) Misc.fadeAndExpire(target);

        return true;
    }

    /**
     * Converts the old orbiting beacon to a flag on its actual planet, then retires that beacon.
     * A save which already advanced the tutorial only loses the obsolete object; it never receives
     * a fresh scene or a new progression flag.
     */
    public static void migrateLegacyBeacons() {
        if (Global.getSector() == null) return;

        for (LocationAPI location : Global.getSector().getAllLocations()) {
            List<SectorEntityToken> candidates = new ArrayList<>(
                    location.getEntitiesWithTag(TutorialConstants.CASTAWAY_TAG));

            for (SectorEntityToken beacon : candidates) {
                if (!isCastaway(beacon)) continue;

                SectorEntityToken focus = beacon.getOrbitFocus();
                if (!FishingIntro.isAtLeast(FishingIntro.POINTED)
                        && focus instanceof PlanetAPI planet && planet.getMarket() != null) {

                    Global.getSector().getMemoryWithoutUpdate()
                            .set(TutorialConstants.CASTAWAY_PLACED_KEY, true);
                    planet.getMarket().getMemoryWithoutUpdate()
                            .set(TutorialConstants.CASTAWAY_HOST_KEY, true);
                }

                Misc.fadeAndExpire(beacon);
            }
        }
    }

    /** Whether an entity is the beacon, for the dialog router. */
    public static boolean isCastaway(SectorEntityToken entity) {
        return entity != null
                && TutorialConstants.CASTAWAY_ENTITY_ID.equals(entity.getCustomEntityType());
    }

    //---------------------------------------------------------------- the entity

    @Override
    public boolean hasCustomMapTooltip() {
        return true;
    }

    @Override
    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle(TutorialConstants.CASTAWAY_NAME);
        tooltip.addPara("Personal-issue, on a loop, and running down.", Misc.getGrayColor(), 10f);
    }
}
