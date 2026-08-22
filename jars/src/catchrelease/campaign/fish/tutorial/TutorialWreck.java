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

public class TutorialWreck {

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

            // somebody would have towed it out of an inhabited system, and the scene only works where the answer to "why is this still here" is that nobody comes out this far
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

        // recoverable the way any found hulk is - through the salvage screen, story point and all
        ShipRecoverySpecialCreator creator =
                new ShipRecoverySpecialCreator(null, 0, 0, false, null, null);
        Misc.setSalvageSpecial(wreck, creator.createSpecial(wreck, null));

        wreck.getMemoryWithoutUpdate().set(TutorialConstants.WRECK_FLAG, true);
        Misc.makeImportant(wreck, TutorialConstants.WRECK_IMPORTANT);
    }

    public static boolean isWreck(SectorEntityToken entity) {
        return entity != null
                && entity.getMemoryWithoutUpdate().getBoolean(TutorialConstants.WRECK_FLAG);
    }

    public static void retire(SectorEntityToken wreck) {
        if (!isWreck(wreck)) return;

        wreck.getMemoryWithoutUpdate().unset(TutorialConstants.WRECK_FLAG);
        Misc.makeUnimportant(wreck, TutorialConstants.WRECK_IMPORTANT);
    }
}
