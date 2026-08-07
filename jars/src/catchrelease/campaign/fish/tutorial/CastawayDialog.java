package catchrelease.campaign.fish.tutorial;

import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Map;

/**
 * Answering the beacon.
 * <p>
 * He is not a quest-giver and does not behave like one. He cannot be recruited, will not come
 * aboard, and has one sentence worth hearing which he gets to on his own schedule. What the scene
 * is actually for is tone: the first thing the player learns about the Fisherman is that somebody
 * was put off a boat for looking at the catch, and that they would rather stay here.
 */
public class CastawayDialog implements InteractionDialogPlugin {

    protected enum Option {
        ASK,
        OFFER,
        WHY,
        LEAVE
    }

    protected InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.getVisualPanel().showImageVisual(
                new InteractionDialogImageVisual("illustrations", "vacuum_colony", 640, 400));

        dialog.getTextPanel().addPara("The beacon is personal-issue and has been running for years."
                + " Under it, dug into the regolith, is a smuggler's cache somebody stopped coming"
                + " back for, and a man who has been living inside it.");

        dialog.getTextPanel().addPara("He is upright, which is generous. The cache was three"
                + " quarters rum and he has made serious progress on the rest of it.",
                Misc.getGrayColor());

        showOptions();
    }

    protected void showOptions() {
        dialog.getOptionPanel().clearOptions();

        dialog.getOptionPanel().addOption("Ask how he got here", Option.ASK);
        dialog.getOptionPanel().addOption("Offer him passage off the rock", Option.OFFER);
        dialog.getOptionPanel().addOption("Leave", Option.LEAVE);
        dialog.getOptionPanel().setShortcut(Option.LEAVE, org.lwjgl.input.Keyboard.KEY_ESCAPE,
                false, false, false, true);
    }

    protected void ask() {
        dialog.getTextPanel().addPara("\"Put off,\" he says, like it is a place. \"Put off. I"
                + " looked in the tank. That's all. They land the thing and they cover it and you"
                + " do not look, and I looked.\"");

        dialog.getTextPanel().addPara("He does not say what was in it. He starts to, twice.",
                Misc.getGrayColor());

        //the one useful sentence he has, and the only thing this scene exists to deliver
        pointHim();

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("\"Who is 'they'?\"", Option.WHY);
        dialog.getOptionPanel().addOption("Leave", Option.LEAVE);
    }

    protected void why() {
        dialog.getTextPanel().addPara("\"The Fisherman.\" He says it the way you say a place you"
                + " are not going back to. \"Go and look at it yourself, if you're the looking"
                + " sort. Everybody is, the first time.\"");

        dialog.getTextPanel().addPara("\"They keep boats where people are. Out past the last"
                + " colony, where nothing's in the way.\"");

        showOptions();
    }

    protected void offer() {
        dialog.getTextPanel().addPara("He looks at your ship for a while and then at the ground."
                + " \"No,\" he says. \"Thank you. No.\"");

        dialog.getTextPanel().addPara("He does not explain that either, and it is somehow the most"
                + " informative thing he has said.", Misc.getGrayColor());

        pointHim();

        showOptions();
    }

    /** Both routes through the conversation put the mark up; neither of them has to be finished. */
    protected void pointHim() {
        boolean fresh = !FishingIntro.isAtLeast(FishingIntro.POINTED);

        FishingIntro.point();

        if (!fresh) return;

        CampaignFleetAPI boat = FishingIntro.getNearestBoat();

        if (boat != null && boat.getContainingLocation() != null) {
            dialog.getTextPanel().addPara("An intel note tracks the nearest one, working %s.",
                    Misc.getGrayColor(), Misc.getHighlightColor(),
                    boat.getContainingLocation().getName());
        } else {
            dialog.getTextPanel().addPara("An intel note tracks the nearest one.",
                    Misc.getGrayColor());
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData != null) dialog.addOptionSelectedText(optionData);

        if (optionData == Option.ASK) {
            ask();
            return;
        }

        if (optionData == Option.WHY) {
            why();
            return;
        }

        if (optionData == Option.OFFER) {
            offer();
            return;
        }

        dialog.dismiss();
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
