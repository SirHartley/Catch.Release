package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.jobs.QuestPond;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner.ShipRecoverySpecialCreator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

/**
 * Somebody who did this before you, still where it happened.
 * <p>
 * A battered cruiser with the Fisherman's damaged LYNE service assembly still clamped to its
 * handling deck - put beside the first rupture the player comes within sight of out where nobody
 * lives. It is a breadcrumb for somebody who finds unstable fabric before finding the trade, not
 * usable fishing gear granted ahead of the introduction.
 * <p>
 * The hulk is vanilla's own wreck, made vanilla's own way: {@code DerelictShipData} through
 * {@code BaseThemeGenerator.addSalvageEntity} with a recovery special, exactly the sequence the
 * stock missions use to park a derelict somewhere. That buys everything a bespoke entity had to
 * fake - the real hull rendered as the wreck, the map icon and sensor behaviour of salvage, and
 * the standard scavenge-and-recover screen once the assembly scene lets go. The scene itself is a
 * score-boosted rules row on the {@link TutorialConstants#WRECK_FLAG} memory flag; recovering the
 * assembly {@link #retire retires} the flag and the mission marker, and the wreck goes back to
 * being ordinary salvage.
 * <p>
 * It appears the moment the pond comes into view rather than at sector generation, so it is always
 * <i>this</i> rupture - the one being looked at - and never a thing already sitting on the map
 * waiting to be got round to. Marked important, because a hulk with the trade's property clamped
 * to it is the one piece of scenery in the mod that has to be walked up to.
 * <p>
 * Populated systems are excluded. Somebody would have towed it.
 */
public class TutorialWreck {

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
            if (FishingIntro.isAtLeast(FishingIntro.RODDED)) return;

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

    /** The hulk itself, in a slow orbit of the rupture that killed it - vanilla's wreck, whole. */
    protected static void place(StarSystemAPI system, SectorEntityToken pond) {
        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.WRECK_PLACED_KEY, true);

        String variant = TutorialConstants.WRECK_HULLS[(int) MathUtils.getRandomNumberInRange(0f,
                TutorialConstants.WRECK_HULLS.length - 0.01f)];

        DerelictShipData params = new DerelictShipData(
                new PerShipData(variant, ShipCondition.BATTERED, 0f), false);
        SectorEntityToken wreck = BaseThemeGenerator.addSalvageEntity(system,
                Entities.WRECK, Factions.NEUTRAL, params);

        wreck.setDiscoverable(true);
        wreck.setCircularOrbit(pond, MathUtils.getRandomNumberInRange(0f, 360f),
                pond.getRadius() + TutorialConstants.WRECK_ORBIT_RADIUS,
                TutorialConstants.WRECK_ORBIT_DAYS);

        //recoverable the way any found hulk is - through the salvage screen, story point and all
        ShipRecoverySpecialCreator creator =
                new ShipRecoverySpecialCreator(null, 0, 0, false, null, null);
        Misc.setSalvageSpecial(wreck, creator.createSpecial(wreck, null));

        wreck.getMemoryWithoutUpdate().set(TutorialConstants.WRECK_FLAG, true);
        Misc.makeImportant(wreck, TutorialConstants.WRECK_IMPORTANT);
    }

    /** Whether an entity is the hulk still carrying the assembly, for anything routing on it. */
    public static boolean isWreck(SectorEntityToken entity) {
        return entity != null
                && entity.getMemoryWithoutUpdate().getBoolean(TutorialConstants.WRECK_FLAG);
    }

    /** The assembly is aboard; the hulk goes back to being ordinary salvage. */
    public static void retire(SectorEntityToken wreck) {
        if (!isWreck(wreck)) return;

        wreck.getMemoryWithoutUpdate().unset(TutorialConstants.WRECK_FLAG);
        Misc.makeUnimportant(wreck, TutorialConstants.WRECK_IMPORTANT);
    }
}
