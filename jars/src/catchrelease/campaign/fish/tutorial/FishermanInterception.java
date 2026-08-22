package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.CoreFisherSpawner;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.jobs.QuestPond;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class FishermanInterception implements EveryFrameScript {

    public static final String INTERCEPTED_KEY = "$catchrelease_intercepted";
    public static final String CHASING_KEY = "$catchrelease_fisherClosing";
    public static final float CHASE_DAYS = 3f;

    protected final IntervalUtil interval = new IntervalUtil(
            TutorialConstants.INTERCEPT_CHECK_SECONDS, TutorialConstants.INTERCEPT_CHECK_SECONDS);

    public static void register() {
        Global.getSector().addTransientScript(new FishermanInterception());
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
        if (FishingIntro.isAtLeast(FishingIntro.RODDED)) return;

        interval.advance(amount);
        if (!interval.intervalElapsed()) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;

        if (!(player.getContainingLocation() instanceof StarSystemAPI)) return;
        StarSystemAPI system = (StarSystemAPI) player.getContainingLocation();

        if (!OuterReaches.isPopulated(system)) return;

        CampaignFleetAPI boat = CoreFisherSpawner.getBoat(system);
        if (boat == null) return;
        if (boat.getMemoryWithoutUpdate().getBoolean(INTERCEPTED_KEY)) return;

        if (!isNosingAroundARupture(player, system)) return;

        cutOff(boat, player);
    }

    protected boolean isNosingAroundARupture(CampaignFleetAPI player, StarSystemAPI system) {
        for (SectorEntityToken pond : QuestPond.getPonds(system)) {
            if (Misc.getDistance(player.getLocation(), pond.getLocation())
                    <= TutorialConstants.INTERCEPT_TRIGGER_RANGE) {
                return true;
            }
        }

        return false;
    }

    protected void cutOff(CampaignFleetAPI boat, CampaignFleetAPI player) {
        boat.getMemoryWithoutUpdate().set(INTERCEPTED_KEY, true);

        Vector2f at = MathUtils.getPointOnCircumference(player.getLocation(),
                TutorialConstants.INTERCEPT_SPAWN_DISTANCE,
                MathUtils.getRandomNumberInRange(0f, 360f));

        if (player.getContainingLocation() instanceof StarSystemAPI system) {
            at = OuterReaches.place(system, at);
        }

        boat.setLocation(at.x, at.y);

        boat.clearAssignments();

        boat.getMemoryWithoutUpdate().set(FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET, new Vector2f(player.getLocation()), 30f);
        if (boat.getAI() instanceof ModularFleetAIAPI) {
            ((ModularFleetAIAPI)boat.getAI()).getTacticalModule().setTarget(player);
        }

        boat.addAssignment(FleetAssignment.INTERCEPT, player, 10f, "closing on your fleet");

        boat.getMemoryWithoutUpdate().set(CHASING_KEY, true, CHASE_DAYS);

        // it wants to be talked to, and it does not lose interest halfway there
        boat.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true, 10f);
        boat.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true, 10f);
        boat.getMemoryWithoutUpdate().set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true, 10f);
        boat.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true, 10f);

        Global.getSector().getCampaignUI().addMessage(boat.getName()
                + " is closing on your position.", Misc.getHighlightColor());
    }

    public static boolean isClosing(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(CHASING_KEY);
    }

    public static boolean hasIntercepted(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(INTERCEPTED_KEY);
    }
}
