package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishShopDialog;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Map;

/**
 * Baha, who is the reason anybody aboard knows what any of this is.
 * <p>
 * The scientist and the boat's captain both, which is the joke and also the explanation: a fishing
 * fleet run by somebody who came out here to measure the water and stayed because of what came out
 * of it. The Fisherman does not explain himself; Baha explains everything, which is what makes them
 * the one who hands over the gear.
 * <p>
 * A plugin swapped into the same frame as the outfitter and the chart counter, and handed back the
 * same way - see {@link FishShopDialog.OnClose}. The conversation is short by design: the
 * introduction is three paragraphs and a rig, and the actual teaching is a task rather than a wall
 * of text, because the first thing anybody does with a wall of text is close it.
 */
public class BahaDialog implements InteractionDialogPlugin {

    protected enum Option {
        LISTEN,
        GEAR,
        HAND_OVER,
        WHAT_NOW,
        BACK
    }

    protected InteractionDialogAPI dialog;
    protected final FishShopDialog.OnClose onClose;

    public BahaDialog(FishShopDialog.OnClose onClose) {
        this.onClose = onClose;
    }

    /** Whether Baha has anything to say that the player has not heard - drives the option's colour. */
    public static boolean hasBusiness() {
        if (!FishingIntro.isAtLeast(FishingIntro.TAUGHT)) return true;

        return !FishingIntro.isAtLeast(FishingIntro.DONE) && hasCatch();
    }

    /**
     * Anything at all, of any kind.
     * <p>
     * Not {@code count(COMMON)}: {@link FishCurrency} counts and spends by <b>exact</b> rarity, so
     * asking for a common would leave somebody whose first catch came up uncommon standing there
     * holding a fish and being told the hold is empty. "A common will do" is a floor in Baha's
     * mouth and a floor is not what that method means.
     */
    protected static boolean hasCatch() {
        return cheapestAboard() != null;
    }

    /** The least valuable rarity the player actually has, which is what a sample should cost. */
    protected static FishRarity cheapestAboard() {
        Map<FishRarity, Integer> aboard = FishCurrency.count();

        for (FishRarity rarity : FishRarity.values()) {
            Integer held = aboard.get(rarity);
            if (held != null && held >= TutorialConstants.FIRST_CATCH_COUNT) return rarity;
        }

        return null;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.getVisualPanel().showPersonInfo(FishingIntro.getBaha(), true);

        switch (FishingIntro.getStage()) {
            case FishingIntro.DONE:
                sayIdle();
                break;
            case FishingIntro.TAUGHT:
                sayWaiting();
                break;
            default:
                sayHello();
                break;
        }
    }

    //---------------------------------------------------------------- the introduction

    protected void sayHello() {
        dialog.getTextPanel().addPara("Somebody else takes the channel - younger, and a great deal"
                + " more pleased to see you. \"You found us. Good. I'm Baha, I run the science"
                + " end and, on paper, the boat.\"");

        //the campaign that predates the gate already has the rig, and being handed it again would
        //read as the game not knowing what the player is holding
        if (FishingIntro.hasGearAlready()) {
            dialog.getTextPanel().addPara("\"- and you're already rigged for it, I see. Somebody"
                    + " got to you first. Fine. Then you know the shape of it.\"",
                    Misc.getGrayColor());
        }

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("\"What exactly am I looking at?\"", Option.LISTEN);
        dialog.getOptionPanel().addOption("Leave", Option.BACK);
    }

    protected void listen() {
        dialog.getTextPanel().addPara("\"The fabric out here is thin in places. Has been since the"
                + " gates went. Where it's thinnest, things come through it - alive, mostly, and"
                + " they don't hold together well on this side.\"");

        dialog.getTextPanel().addPara("\"We call them fish. It isn't accurate. It stuck.\"",
                Misc.getGrayColor());

        dialog.getTextPanel().addPara("\"Anyone can do it. You burn a window through with a lamp,"
                + " you see what's swimming, and you put a line in it. Ponds are the polite"
                + " version - a hole that's already open, and you drop a rod down it.\"");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("\"Show me.\"", Option.GEAR, Misc.getHighlightColor(),
                null);
    }

    /** The rig, and the one task that turns being told into knowing. */
    protected void takeGear() {
        dialog.getTextPanel().addPara("Baha has a crate on the deck before the sentence is"
                + " finished. \"Lamps, a rod, and a line. All of it secondhand and all of it"
                + " works.\"");

        FishingIntro.teach(dialog.getTextPanel());

        dialog.getTextPanel().addPara("\"Go and land something. Anything - a common will do, I'm"
                + " not fussy. Bring it back to any of our boats and I'll fit you out"
                + " properly.\"");

        dialog.getTextPanel().addPara("An intel note tracks the nearest boat.",
                Misc.getGrayColor());

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Leave", Option.BACK);
    }

    //---------------------------------------------------------------- the first catch

    protected void sayWaiting() {
        dialog.getOptionPanel().clearOptions();

        if (hasCatch()) {
            dialog.getTextPanel().addPara("Baha is looking past you at the hold before the channel"
                    + " is properly open. \"That's one. Let's see it.\"");

            dialog.getOptionPanel().addOption("Hand over a specimen", Option.HAND_OVER,
                    Misc.getHighlightColor(), null);
        } else {
            dialog.getTextPanel().addPara("\"Still nothing in the hold. Lamps on, find something"
                    + " lit, put a line in it. It is genuinely that simple and it will still take"
                    + " you three tries.\"", Misc.getGrayColor());

            dialog.getOptionPanel().addOption("\"Where do I even look?\"", Option.WHAT_NOW);
        }

        dialog.getOptionPanel().addOption("Leave", Option.BACK);
    }

    protected void explainWhere() {
        dialog.getTextPanel().addPara("\"Out past the last colony, same as us. Look for a pond -"
                + " they sit still and they're already open. Failing that, run the lamps and"
                + " sweep until something lights up.\"");

        dialog.getTextPanel().addPara("\"What lives where is on the charts, and the charts are what"
                + " the man out front sells. That's the trade.\"", Misc.getGrayColor());

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Leave", Option.BACK);
    }

    /**
     * The specimen changes hands and the outfitter comes on.
     * <p>
     * Spent through {@link FishCurrency} at the cheapest rung the hold actually has, which is what
     * somebody handing over a sample would part with - and it breaks into a crate the same careful
     * way every other spend in the mod does.
     */
    protected void handOver() {
        FishRarity paying = cheapestAboard();

        if (paying == null
                || !FishCurrency.spend(paying, TutorialConstants.FIRST_CATCH_COUNT)) {

            sayWaiting();
            return;
        }

        dialog.getTextPanel().addPara("Baha turns it over twice, holds it up to the light, and"
                + " looks unreasonably happy about it. \"There. That's the whole thing. Everything"
                + " after this is just doing it better.\"");

        FishingIntro.finish(dialog.getTextPanel());

        dialog.getTextPanel().addPara("\"Outfitter's yours now. Trade the catch in for gear - it's"
                + " the only currency out here that anybody wants.\"");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Leave", Option.BACK);
    }

    protected void sayIdle() {
        dialog.getTextPanel().addPara("\"Still at it, then. Good. The man out front has the"
                + " charts, and I have the readings - between us that's most of a map.\"");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Leave", Option.BACK);
    }

    //---------------------------------------------------------------- plumbing

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData != null) dialog.addOptionSelectedText(optionData);

        if (!(optionData instanceof Option)) return;

        switch ((Option) optionData) {
            case LISTEN:
                listen();
                break;
            case GEAR:
                takeGear();
                break;
            case HAND_OVER:
                handOver();
                break;
            case WHAT_NOW:
                explainWhere();
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
