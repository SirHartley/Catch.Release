package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.shop.FishShopDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Map;

/**
 * The Fisherman explaining breach fishing, which is the whole tutorial.
 * <p>
 * There is no scientist any more. Old paperwork on the boat says Captain Baha and one or two people
 * still say it out loud; that was a man, and a long time ago. Whatever answers now does not correct
 * anybody and does not explain itself either, which is exactly why it is the one doing the
 * explaining - the trade is strange, and being taught it by something strange is the point.
 * <p>
 * A plugin swapped into the same frame as the outfitter and the chart counter and handed back the
 * same way ({@link FishShopDialog.OnClose}). Short by design: three paragraphs and a rig. The
 * teaching is the gear working, not the text.
 */
public class FishingIntroDialog implements InteractionDialogPlugin {

    protected enum Option {
        HANDOVER,
        LISTEN,
        GEAR,
        BACK
    }

    protected InteractionDialogAPI dialog;
    protected final FishShopDialog.OnClose onClose;

    public FishingIntroDialog(FishShopDialog.OnClose onClose) {
        this.onClose = onClose;
    }

    /** Whether there is anything here the player has not had yet - drives the option's colour. */
    public static boolean hasBusiness() {
        return !FishingIntro.isAtLeast(FishingIntro.TAUGHT);
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.getVisualPanel().showPersonInfo(FishermanIdentity.get(), true);

        if (FishingIntro.isAtLeast(FishingIntro.TAUGHT)) {
            sayIdle();
            return;
        }

        //the head comes out first if it is being carried - it is the only thing here the player
        //brought, and everything after it is easier once it has been put on the table
        if (FishingIntro.isCarryingHarpoon()) {
            sayAboutTheHarpoon();
            return;
        }

        sayHello();
    }

    //---------------------------------------------------------------- the harpoon

    protected void sayAboutTheHarpoon() {
        dialog.getTextPanel().addPara("You put the head on the table before anybody has said"
                + " anything. It is not asked where it came from.");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Hand it over", Option.HANDOVER,
                Misc.getHighlightColor(), null);
        dialog.getOptionPanel().addOption("Keep it for now", Option.BACK);
    }

    /**
     * The one line about the hulk, and the only explanation anybody gets for it.
     * <p>
     * Deliberately not an answer. Somebody died out there and this is the entire obituary, said
     * without emphasis by whatever is running the boat - which tells the player more about the
     * Fisherman than about the wreck, and is meant to.
     */
    protected void takeHarpoon() {
        Global.getSector().getMemoryWithoutUpdate().unset(TutorialConstants.CARRYING_KEY);

        dialog.getTextPanel().addPara("It is turned over once. \"They intruded on what they should"
                + " not have intruded upon.\"");

        dialog.getTextPanel().addPara("Nothing further is offered, and the silence afterwards is"
                + " not the kind you fill.", Misc.getGrayColor());

        sayHello();
    }

    //---------------------------------------------------------------- the introduction

    protected void sayHello() {
        dialog.getTextPanel().addPara("\"You have questions. Everybody who gets this far has the"
                + " same three, so I will save you two of them.\"");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("\"What exactly is out here?\"", Option.LISTEN);
        dialog.getOptionPanel().addOption("Leave", Option.BACK);
    }

    protected void listen() {
        dialog.getTextPanel().addPara("\"The fabric is thin in places. Has been since the gates"
                + " went. Where it is thinnest, things come through - alive, mostly, and they do not"
                + " hold together well on this side.\"");

        dialog.getTextPanel().addPara("\"We call them fish. It is not accurate. It stuck.\"",
                Misc.getGrayColor());

        dialog.getTextPanel().addPara("\"Anyone can do it. You burn a window through with a lamp,"
                + " you see what is swimming, and you put a line in it. A pond is the polite"
                + " version - a hole that is already open, and you drop a rod down it.\"");

        dialog.getTextPanel().addPara("\"The second question is whether it is safe. It is not. The"
                + " third one you will not ask until later.\"");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("\"Show me.\"", Option.GEAR, Misc.getHighlightColor(),
                null);
    }

    protected void takeGear() {
        dialog.getTextPanel().addPara("There is a crate on the deck before the sentence is"
                + " finished. Lamps, a rod, a line, and a ledger for trading the catch back in."
                + " All of it secondhand and all of it working.");

        FishingIntro.teach(dialog.getTextPanel());

        dialog.getTextPanel().addPara("\"Out past the last colony, where nothing is in the way."
                + " Find a pond, or run the lamps until something lights up. Come back when you"
                + " have something worth the freight.\"");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Leave", Option.BACK);
    }

    protected void sayIdle() {
        dialog.getTextPanel().addPara("\"Still at it. Good. The charts are what I sell and the"
                + " water is what I know - between the two that is most of a map.\"");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Leave", Option.BACK);
    }

    //---------------------------------------------------------------- plumbing

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData != null) dialog.addOptionSelectedText(optionData);

        if (!(optionData instanceof Option)) return;

        switch ((Option) optionData) {
            case HANDOVER:
                takeHarpoon();
                break;
            case LISTEN:
                listen();
                break;
            case GEAR:
                takeGear();
                break;
            case BACK:
                if (onClose == null) {
                    dialog.dismiss();
                } else {
                    onClose.onShopClosed(dialog);
                }
                break;
        }
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
