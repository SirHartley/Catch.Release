package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.OuterReaches;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.SurveyPlanetListener;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

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
 * A beacon in tight orbit rather than an option bolted onto the survey screen: the survey is
 * vanilla's and stays vanilla's, and a beacon is a thing the player chooses to answer.
 */
public class Castaway extends BaseCustomEntityPlugin {

    /** Watches for the survey. Transient; the beacon is what persists. */
    public static class Watcher implements SurveyPlanetListener {

        public static void register() {
            Global.getSector().getListenerManager().removeListenerOfClass(Watcher.class);
            Global.getSector().getListenerManager().addListener(new Watcher(), true);
        }

        @Override
        public void reportPlayerSurveyedPlanet(PlanetAPI planet) {
            if (isPlaced()) return;
            if (FishingIntro.isAtLeast(FishingIntro.TAUGHT)) return;

            if (planet == null || planet.getStarSystem() == null) return;

            //somewhere nobody lives: a man marooned four hundred metres from a spaceport is not
            //marooned, he is loitering
            if (OuterReaches.isPopulated(planet.getStarSystem())) return;

            place(planet);
        }
    }

    public static boolean isPlaced() {
        return Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.CASTAWAY_PLACED_KEY);
    }

    protected static void place(PlanetAPI host) {
        Global.getSector().getMemoryWithoutUpdate()
                .set(TutorialConstants.CASTAWAY_PLACED_KEY, true);

        StarSystemAPI system = host.getStarSystem();

        SectorEntityToken beacon = system.addCustomEntity(Misc.genUID(),
                TutorialConstants.CASTAWAY_NAME, TutorialConstants.CASTAWAY_ENTITY_ID, null, null);

        beacon.setCircularOrbit(host, MathUtils.getRandomNumberInRange(0f, 360f),
                host.getRadius() + TutorialConstants.CASTAWAY_SURFACE_PAD,
                TutorialConstants.CASTAWAY_ORBIT_DAYS);

        beacon.setDiscoverable(false);

        Misc.makeImportant(beacon, "catchrelease_tutorial");

        Global.getSector().getCampaignUI().addMessage("A survivor's beacon is transmitting from the"
                + " surface of " + host.getName() + ".", Misc.getHighlightColor());
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
