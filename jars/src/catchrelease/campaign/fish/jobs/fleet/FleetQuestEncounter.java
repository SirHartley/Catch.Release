package catchrelease.campaign.fish.jobs.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;

/**
 * Runs one fleet's offer from the moment it is hung to the moment it is taken, turned down, or
 * quietly lapses.
 * <p>
 * Nothing is driven at the player. The hull carrying the offer is somebody who was already going
 * where they were going, and the whole of this side of it is a mark over them and the patience to
 * wait for somebody to notice. Once a conversation has closed this reads the answer off the hull:
 * taken, and the job takes the fleet over; not taken, and the offer is unhooked and the fleet is
 * left exactly as it was found.
 * <p>
 * That last part is why turning one down is cheap. There is no spawned hull to tidy away, so a
 * player who says not now has cost nothing and lost nothing.
 * <p>
 * Extends vanilla's listener-and-script pair rather than implementing both, because that base
 * registers itself as a listener in its own constructor; doing it by hand as well registers twice
 * and every dialogue is then reported to this object twice over.
 */
public class FleetQuestEncounter extends BaseCampaignEventListenerAndScript implements EveryFrameScript {

    /**
     * Days an offer stands before its owner stops waiting.
     * <p>
     * So that an offer nobody ever answers cannot become permanent scenery, and because the hull is
     * not ours - somebody else's trader wearing a mark for the rest of the campaign is a fleet with
     * a thing on it nobody can explain.
     */
    public static final float OFFER_DAYS = 30f;

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
        if (daysWaited > OFFER_DAYS) {
            turnedDown();
            return;
        }

        //renderers do not survive a save, and the offer does
        if (quest != null) quest.ensureMarked();
    }

    protected boolean isDialogOpen() {
        return Global.getSector().getCampaignUI() != null
                && Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null;
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
     * Turned down, or waited out. The mark comes off and the two memory keys with it, and that is
     * the whole of the cleanup - the hull was never ours to move, rename or send anywhere.
     */
    protected void turnedDown() {
        if (quest != null) quest.abandon();

        finish();
    }

    protected void finish() {
        done = true;

        Global.getSector().removeListener(this);
    }
}
