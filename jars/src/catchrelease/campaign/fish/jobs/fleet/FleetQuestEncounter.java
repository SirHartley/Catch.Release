package catchrelease.campaign.fish.jobs.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;


public class FleetQuestEncounter extends BaseCampaignEventListenerAndScript implements EveryFrameScript {


    public static final float OFFER_DAYS = 30f;

    protected CampaignFleetAPI fleet;
    protected FleetQuest quest;

    protected boolean talked = false;
    protected boolean done = false;
    protected float daysWaited = 0f;

    public static FleetQuestEncounter attach(CampaignFleetAPI fleet, FleetQuest quest) {
        FleetQuestEncounter encounter = new FleetQuestEncounter(fleet, quest);

        // the listener half registered itself; only the script half is left to do
        Global.getSector().addScript(encounter);

        return encounter;
    }

    public FleetQuestEncounter(CampaignFleetAPI fleet, FleetQuest quest) {
        this.fleet = fleet;
        this.quest = quest;
    }


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

        if (fleet == null || fleet.isExpired() || !fleet.isAlive()) {
            finish();
            return;
        }

        // the answer is not final until the conversation is over, so this waits for it to close
        if (talked && !isDialogOpen()) {
            answer();
            return;
        }

        daysWaited += Global.getSector().getClock().convertToDays(amount);
        if (daysWaited > OFFER_DAYS) {
            turnedDown();
            return;
        }

        // renderers do not survive a save, and the offer does
        if (quest != null) quest.ensureMarked();
    }

    protected boolean isDialogOpen() {
        return Global.getSector().getCampaignUI() != null
                && Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null;
    }


    protected void answer() {
        if (fleet.getMemoryWithoutUpdate().getBoolean(FleetQuest.TAKEN_FLAG)) {
            if (quest != null) quest.take();

            finish();
            return;
        }

        turnedDown();
    }


    protected void turnedDown() {
        if (quest != null) quest.abandon();

        finish();
    }

    protected void finish() {
        done = true;

        Global.getSector().removeListener(this);
    }
}
