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
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * The boat heading somebody off before they do something stupid.
 * <p>
 * In an inhabited system there is a fishing boat already posted, so a player walking up to a rupture
 * with no gear is a problem it can actually be present for. It becomes present. It does not travel;
 * it is simply somewhere else the next time anybody looks, a short way off and closing, and nobody
 * aboard remarks on this.
 * <p>
 * The teleport is the point rather than a shortcut. Whatever is running that boat is not bound by
 * the ordinary business of being in one place, and the cheapest way to say so is to break the rule
 * in front of the player and never mention it. It is dropped in outside the viewport so the arrival
 * is never actually witnessed - what is witnessed is that it is there now and was not before.
 * <p>
 * Only before the introduction, and only in a populated system: out on the frontier the hulk does
 * this job, and doing both would be two answers to one question.
 */
public class FishermanInterception implements EveryFrameScript {

    /** Set on a boat that has moved itself to cut somebody off, so it is only ever done once. */
    public static final String INTERCEPTED_KEY = "$catchrelease_intercepted";

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

    /** Whether the player is close enough to a rupture to be about to try something. */
    protected boolean isNosingAroundARupture(CampaignFleetAPI player, StarSystemAPI system) {
        for (SectorEntityToken pond : QuestPond.getPonds(system)) {
            if (Misc.getDistance(player.getLocation(), pond.getLocation())
                    <= TutorialConstants.INTERCEPT_TRIGGER_RANGE) {

                return true;
            }
        }

        return false;
    }

    /**
     * The boat is now here.
     * <p>
     * Placed off the far side of the player from wherever it was, so the move is never on screen,
     * and pointed straight at them with the flags that make a fleet close and hold rather than go
     * back to what it was doing.
     */
    protected void cutOff(CampaignFleetAPI boat, CampaignFleetAPI player) {
        boat.getMemoryWithoutUpdate().set(INTERCEPTED_KEY, true);

        Vector2f at = MathUtils.getPointOnCircumference(player.getLocation(),
                TutorialConstants.INTERCEPT_SPAWN_DISTANCE,
                MathUtils.getRandomNumberInRange(0f, 360f));

        boat.setLocation(at.x, at.y);

        boat.clearAssignments();
        boat.addAssignment(FleetAssignment.INTERCEPT, player, 3f, "closing on your fleet");

        //it wants to be talked to, and it does not lose interest halfway there
        boat.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true, 3f);
        boat.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true, 3f);
        boat.getMemoryWithoutUpdate().set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true, 3f);

        Global.getSector().getCampaignUI().addMessage(boat.getName()
                + " is closing on your position.", Misc.getHighlightColor());
    }

    /** Whether this boat is the one that came to head somebody off - for what it says first. */
    public static boolean hasIntercepted(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(INTERCEPTED_KEY);
    }
}
