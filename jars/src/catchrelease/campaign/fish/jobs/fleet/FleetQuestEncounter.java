package catchrelease.campaign.fish.jobs.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;

/**
 * Runs one fleet's offer from the moment it arrives to the moment it is taken, turned down, or
 * gives up waiting.
 * <p>
 * A fleet that can fly is flown at the player until they talk to it - one that came looking and then
 * sat waiting to be noticed is one nobody notices. Once the conversation has closed this reads the
 * answer off the hull: taken, and the job starts and the fleet settles down to wait for delivery;
 * not taken, and it drops the exclamation, turns for home and is gone.
 * <p>
 * A stranded fleet does none of the flying, its drive being the reason it is asking, and turning one
 * down leaves it where it is - it cannot leave, and an offer that deleted itself because the player
 * said not now is one they could never come back to.
 * <p>
 * Extends vanilla's listener-and-script pair rather than implementing both, because that base
 * registers itself as a listener in its own constructor; doing it by hand as well registers twice
 * and every dialogue is then reported to this object twice over.
 */
public class FleetQuestEncounter extends BaseCampaignEventListenerAndScript implements EveryFrameScript {

    /** Days a fleet will keep chasing the player before giving up and going home. */
    public static final float PURSUE_DAYS = 30f;

    /**
     * Days an offer stands before its owner stops waiting.
     * <p>
     * Both exist so that an offer nobody ever answers cannot become permanent scenery - a fleet
     * chasing the player forever with an exclamation over it, or a hull parked in a far system for
     * the rest of the campaign. The stranded one is the longer of the two because its distress call
     * is what the player is working from and that stands for sixty days on vanilla's own clock.
     */
    public static final float OFFER_DAYS_WANDERING = 20f;
    public static final float OFFER_DAYS_STRANDED = 90f;

    protected CampaignFleetAPI fleet;
    protected FleetQuest quest;

    protected boolean talked = false;
    protected boolean done = false;
    protected float daysWaited = 0f;

    public static FleetQuestEncounter attach(CampaignFleetAPI fleet, FleetQuest quest) {
        FleetQuestEncounter encounter = new FleetQuestEncounter(fleet, quest);

        //the listener half registered itself; only the script half is left to do
        Global.getSector().addScript(encounter);

        return encounter;
    }

    public FleetQuestEncounter(CampaignFleetAPI fleet, FleetQuest quest) {
        this.fleet = fleet;
        this.quest = quest;
    }

    /** Offers still waiting on an answer, so the spawner can count what it has not heard back about. */
    public static int countLive() {
        int count = 0;

        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof FleetQuestEncounter && !script.isDone()) count++;
        }

        return count;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        super.reportShownInteractionDialog(dialog);

        if (dialog != null && dialog.getInteractionTarget() == fleet) talked = true;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        //the hull went while this was waiting on it - nothing left to run
        if (fleet == null || fleet.isExpired() || !fleet.isAlive()) {
            finish();
            return;
        }

        //the answer is not final until the conversation is over, so this waits for it to close
        if (talked && !isDialogOpen()) {
            answer();
            return;
        }

        daysWaited += Global.getSector().getClock().convertToDays(amount);
        if (daysWaited > getOfferDays()) {
            giveUp();
            return;
        }

        chase();
    }

    protected float getOfferDays() {
        return isWandering() ? OFFER_DAYS_WANDERING : OFFER_DAYS_STRANDED;
    }

    protected boolean isWandering() {
        return quest != null && quest.getType() != null && quest.getType().wandering;
    }

    protected boolean isDialogOpen() {
        return Global.getSector().getCampaignUI() != null
                && Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null;
    }

    /**
     * Keeps a wandering fleet coming. Reasserted rather than set once: a fleet's own AI drops an
     * assignment it has finished or thought better of, and one that quietly stopped chasing would
     * sit at the edge of the system with an exclamation over it doing nothing.
     */
    protected void chase() {
        if (!isWandering()) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || fleet.getContainingLocation() != player.getContainingLocation()) return;

        if (fleet.getAI() == null) return;
        if (fleet.getAI().getCurrentAssignmentType() == FleetAssignment.INTERCEPT) return;

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.INTERCEPT, player, PURSUE_DAYS, quest.getType().actionText);
    }

    /** Reads what the player decided off the hull, the dialogue having written it there. */
    protected void answer() {
        if (fleet.getMemoryWithoutUpdate().getBoolean(FleetQuest.TAKEN_FLAG)) {
            if (quest != null) quest.take();

            finish();
            return;
        }

        turnedDown();
    }

    /**
     * Turned down, or waited out. A fleet that came looking has no further reason to be here, so it
     * gives up the exclamation and leaves under its own power - the standard return assignments are
     * what carry it out of the system and despawn it there.
     */
    protected void turnedDown() {
        if (quest != null) quest.abandon();

        if (fleet.getAI() != null) fleet.getAI().setActionTextOverride(null);

        finish();
    }

    /**
     * Nobody came. A wandering fleet leaves the same way a refused one does; a stranded one cannot,
     * so it is simply let go of - unmarked, and left to the game's own cleanup rather than parked in
     * a far system with an exclamation over it for the rest of the campaign.
     */
    protected void giveUp() {
        turnedDown();

        if (!isWandering() && fleet != null && !fleet.isExpired()) fleet.despawn();
    }

    protected void finish() {
        done = true;

        Global.getSector().removeListener(this);
    }
}
