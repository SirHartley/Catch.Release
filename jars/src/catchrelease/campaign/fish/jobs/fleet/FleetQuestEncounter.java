package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.distress.DistressCallFramework;
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

    public FleetQuestEncounter(CampaignFleetAPI fleet, FleetQuest quest) {
        this.fleet = fleet;
        this.quest = quest;
    }

    public static FleetQuestEncounter attach(CampaignFleetAPI fleet, FleetQuest quest) {
        FleetQuestEncounter encounter = new FleetQuestEncounter(fleet, quest);

        // the listener half registered itself; only the script half is left to do
        Global.getSector().addScript(encounter);

        return encounter;
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
        if (quest != null) {
            quest.keepStanding();
            quest.ensureMarked();
        }
    }

    protected boolean isDialogOpen() {
        return Global.getSector().getCampaignUI() != null
                && Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null;
    }

    protected void answer() {
        if (fleet.getMemoryWithoutUpdate().getBoolean(FleetQuest.TAKEN_FLAG)) {
            resolveDistress();
            if (quest != null) quest.take();

            finish();
            return;
        }

        if (quest != null) quest.decline();
        resolveDistress();

        finish();
    }

    protected void turnedDown() {
        if (quest != null) quest.abandon();
        resolveDistress();

        finish();
    }

    protected void resolveDistress() {
        if (DistressCallFramework.isManaged(fleet)) DistressCallFramework.resolve(fleet);
    }

    protected void finish() {
        done = true;

        Global.getSector().removeListener(this);
    }
}
