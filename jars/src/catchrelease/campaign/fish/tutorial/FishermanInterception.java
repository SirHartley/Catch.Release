package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.CoreFisherSpawner;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.helper.math.ViewportEdge;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class FishermanInterception implements EveryFrameScript {

    public static final String INTERCEPTED_KEY = "$catchrelease_intercepted";
    public static final String CHASING_KEY = "$catchrelease_fisherClosing";
    public static final float CHASE_DAYS = 10f;
    private static final int SPAWN_TRIES = 24;

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
        if (boat.getBattle() != null || player.getBattle() != null) return;
        if (Global.getSector().getCampaignUI().isShowingDialog()) return;
        if (!(player.getContainingLocation() instanceof StarSystemAPI system)) return;

        ViewportAPI viewport = Global.getSector().getViewport();
        if (!ViewportEdge.contains(viewport, player.getLocation(), 0f)) return;
        Vector2f at = ViewportEdge.contains(viewport, boat.getLocation(), boat.getRadius())
                ? new Vector2f(boat.getLocation()) : pickApproach(system, player, viewport);
        if (at == null) return;
        if (!OuterReaches.isLegClear(system, at, player.getLocation())) return;

        boat.getMemoryWithoutUpdate().set(INTERCEPTED_KEY, true);
        if (!at.equals(boat.getLocation())) {
            boat.setLocation(at.x, at.y);
            boat.setVelocity(0f, 0f);
        }

        boat.clearAssignments();
        boat.setMoveDestinationOverride(player.getLocation().x, player.getLocation().y);

        boat.getMemoryWithoutUpdate().set(FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET,
                new Vector2f(player.getLocation()), CHASE_DAYS);
        if (boat.getAI() instanceof ModularFleetAIAPI ai) {
            ai.getTacticalModule().setTarget(player);
            ai.getTacticalModule().setPriorityTarget(player, 0.5f, false);
            ai.getTacticalModule().setTravelDestination(null, 0f);
        }

        boat.addAssignment(FleetAssignment.INTERCEPT, player, CHASE_DAYS, "closing on your fleet");

        boat.getMemoryWithoutUpdate().set(CHASING_KEY, true, CHASE_DAYS);

        boat.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true, CHASE_DAYS);
        boat.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true, CHASE_DAYS);
        boat.getMemoryWithoutUpdate().set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true, CHASE_DAYS);
        boat.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true, CHASE_DAYS);
    }

    protected Vector2f pickApproach(StarSystemAPI system, CampaignFleetAPI player, ViewportAPI viewport) {
        float firstAngle = MathUtils.getRandomNumberInRange(0f, 360f);
        for (int i = 0; i < SPAWN_TRIES; i++) {
            Vector2f at = ViewportEdge.outside(viewport, TutorialConstants.INTERCEPT_VIEWPORT_MARGIN_PX,
                    firstAngle + i * 360f / SPAWN_TRIES);
            if (at != null && OuterReaches.isLegClear(system, at, player.getLocation())) return at;
        }
        return null;
    }

    public static boolean isClosing(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(CHASING_KEY);
    }

    public static void cancelApproach(CampaignFleetAPI fleet) {
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        memory.unset(CHASING_KEY);
        memory.unset(FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET);
        memory.unset(MemFlags.MEMORY_KEY_PURSUE_PLAYER);
        memory.unset(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED);
        memory.unset(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER);
        memory.unset(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT);
        fleet.setInteractionTarget(null);
    }

    public static boolean hasIntercepted(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(INTERCEPTED_KEY);
    }
}
