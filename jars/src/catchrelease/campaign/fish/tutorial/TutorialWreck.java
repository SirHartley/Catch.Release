package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.jobs.QuestPond;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

/**
 * Somebody who did this before you, still where it happened.
 * <p>
 * A cruiser, dead, with a fishing harpoon buried in the hull - put beside the first rupture the
 * player comes within sight of out where nobody lives. That siting is the entire argument: the
 * player's first look at unstable terrain and their first look at what it costs arrive in the same
 * frame, and neither needs a word of explanation.
 * <p>
 * It appears the moment the pond comes into view rather than at sector generation, so it is always
 * <i>this</i> rupture - the one being looked at - and never a thing already sitting on the map
 * waiting to be got round to. Marked important, because a hulk with a harpoon in it is the one
 * piece of scenery in the mod that has to be walked up to.
 * <p>
 * Populated systems are excluded. Somebody would have towed it.
 */
public class TutorialWreck extends BaseCustomEntityPlugin {

    /** Watches for the first rupture worth putting it next to. Transient; the hulk is what persists. */
    public static class Watcher implements EveryFrameScript {

        public static void register() {
            Global.getSector().addTransientScript(new Watcher());
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
            if (isPlaced()) return;
            if (FishingIntro.isAtLeast(FishingIntro.TAUGHT)) return;

            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player == null) return;

            if (!(player.getContainingLocation() instanceof StarSystemAPI)) return;
            StarSystemAPI system = (StarSystemAPI) player.getContainingLocation();

            //somebody would have towed it out of an inhabited system, and the scene only works
            //where the answer to "why is this still here" is that nobody comes out this far
            if (OuterReaches.isPopulated(system)) return;

            for (SectorEntityToken pond : QuestPond.getPonds(system)) {
                if (Misc.getDistance(player.getLocation(), pond.getLocation())
                        > TutorialConstants.WRECK_SPOT_RANGE) {

                    continue;
                }

                place(system, pond);
                return;
            }
        }
    }

    public static boolean isPlaced() {
        return Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.WRECK_PLACED_KEY);
    }

    /** The hulk itself, in a slow orbit of the rupture that killed it. */
    protected static void place(StarSystemAPI system, SectorEntityToken pond) {
        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.WRECK_PLACED_KEY, true);

        SectorEntityToken wreck = system.addCustomEntity(Misc.genUID(),
                TutorialConstants.WRECK_NAME, TutorialConstants.WRECK_ENTITY_ID, null, null);

        wreck.setCircularOrbit(pond, MathUtils.getRandomNumberInRange(0f, 360f),
                pond.getRadius() + TutorialConstants.WRECK_ORBIT_RADIUS,
                TutorialConstants.WRECK_ORBIT_DAYS);

        //not discoverable: the point is that it is plainly there the moment the rupture is, and a
        //thing the player has to survey first is a thing they will fly past
        wreck.setDiscoverable(false);

        Misc.makeImportant(wreck, "catchrelease_tutorial");
    }

    /** The hull it turns out to be - rolled once and kept, since the entity outlives the roll. */
    public static final String HULL_KEY = "$catchrelease_wreckHull";

    public static String getHull(SectorEntityToken wreck) {
        if (wreck == null) return TutorialConstants.WRECK_HULLS[0];

        String stored = wreck.getMemoryWithoutUpdate().getString(HULL_KEY);
        if (stored != null) return stored;

        String hull = TutorialConstants.WRECK_HULLS[(int) MathUtils.getRandomNumberInRange(0f,
                TutorialConstants.WRECK_HULLS.length - 0.01f)];

        wreck.getMemoryWithoutUpdate().set(HULL_KEY, hull);

        return hull;
    }

    /** Whether an entity is the hulk, for the dialog router. */
    public static boolean isWreck(SectorEntityToken entity) {
        return entity != null
                && TutorialConstants.WRECK_ENTITY_ID.equals(entity.getCustomEntityType());
    }

    //---------------------------------------------------------------- the entity

    @Override
    public boolean hasCustomMapTooltip() {
        return true;
    }

    @Override
    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle(TutorialConstants.WRECK_NAME);
        tooltip.addPara("Cold, unclaimed, and holed by something that was not a weapon.",
                Misc.getGrayColor(), 10f);
    }
}
