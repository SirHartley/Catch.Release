package catchrelease.campaign.fish.crab;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Map;

/**
 * Crablobab, who sells crabs, and who has other things in the coat.
 * <p>
 * A bar event rather than a job, and the only one here that is not a {@link
 * catchrelease.campaign.fish.jobs.FishJob} - nothing is asked of the player and nothing is owed
 * afterwards, so there is no mission to run and nothing to keep in the intel screen. That also means
 * no {@code rules.csv} rows: the sheet's whole shape is triggers and mission tokens, and a stall
 * with two things on it is a list of options over a price, which is what a plain bar event is for.
 * <p>
 * He stops turning up once both are sold. Nothing else here is finite, so this is the one place that
 * has to know it is done - see {@link #shouldRemoveEvent()}.
 */
public class CrabSalesman extends BaseBarEventWithPerson {

    /** The stall, one of its wares being looked at, the purchase itself, and the way out. */
    public static final String OPTION_STALL = "catchrelease_crabStall";
    public static final String OPTION_LEAVE = "catchrelease_crabLeave";
    public static final String OPTION_BUY = "catchrelease_crabBuy:";

    /** Set once he has actually sold something, which is what the timeout is measured from. */
    protected transient boolean sold = false;

    /**
     * One man rather than a role somebody happens to be filling. The same name in every bar, because
     * a player who runs into him twice is meant to know it is him.
     */
    @Override
    protected PersonAPI createPerson() {
        PersonAPI person = super.createPerson();

        person.getName().setFirst("Crablobab");
        person.getName().setLast("");

        return person;
    }

    @Override
    protected Gender getPersonGender() {
        return Gender.MALE;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) return false;

        //asked before the person is built, so a stall with nothing on it costs one question
        if (!CrabWares.isAnythingLeft()) return false;

        regen(market);

        return true;
    }

    /** Once both are sold he has no reason to be anywhere, and the event goes for good. */
    @Override
    public boolean shouldRemoveEvent() {
        return !CrabWares.isAnythingLeft();
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);

        //the dialog's own panels rather than the fields, which are only set once the event is
        //entered - this runs while the bar is still building its list of things to look at
        regen(dialog.getInteractionTarget().getMarket());

        dialog.getTextPanel().addPara("A man with a great many pockets is working the room with a"
                + " crate of live crabs, and is plainly losing money on every one of them.");

        dialog.getOptionPanel().addOption("Ask the man with the crate how the crab business is",
                this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);

        done = false;

        dialog.getVisualPanel().showPersonInfo(person, true);

        text.addPara("\"Terrible,\" he says, without being asked twice. \"Crablobab. That is me, and"
                + " that is the crabs as well, it saves time.\" One of the crate's occupants has got"
                + " a claw through the mesh and is working on the rest of itself.");

        text.addPara("He looks you over - the coat, the boots, whatever it is you have been doing to"
                + " your hands - and decides something. \"You do not want crabs.\"");

        showStall();
    }

    /**
     * What is left in the coat, priced. Rebuilt after every purchase rather than edited, so a ware
     * that has just been sold cannot be looked at again.
     */
    protected void showStall() {
        options.clearOptions();

        for (CrabWares ware : CrabWares.values()) {
            if (ware.isOwned()) continue;

            options.addOption(ware.name + " - " + describePrice(ware), ware);
        }

        options.addOption("Tell him you are all right for crabs", OPTION_LEAVE);
    }

    /**
     * One ware, said in his own words, with the price under it.
     * <p>
     * Priced here rather than only on the stall row because the two halves fail separately: a player
     * who cannot pay should be able to see which half is the problem without shopping to find out.
     */
    protected void showWare(CrabWares ware) {
        String credits = Misc.getDGSCredits(ware.credits);
        String catchAsk = ware.getCatch().describe();

        text.addPara(ware.pitch);

        LabelAPI line = text.addPara("He wants %s for it, and %s.", Misc.getHighlightColor(),
                credits, catchAsk);

        //each half coloured by whether it is the one that cannot be met, so somebody who is short
        //can see which of the two to go and do something about
        line.setHighlightColors(
                ware.hasCredits() ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(),
                ware.hasCatch() ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
        line.setHighlight(credits, catchAsk);

        //one object, used for all three calls - the panel keys options by the data it was handed,
        //and a freshly built string each time would not be the same one it is holding
        String buy = OPTION_BUY + ware.name();

        options.clearOptions();
        options.addOption("Pay him", buy);

        if (!ware.canAfford()) {
            options.setEnabled(buy, false);
            options.setTooltip(buy,
                    ware.hasCredits() ? "You do not have the crabs." : "You do not have the credits.");
        }

        options.addOption("Ask what else is in the coat", OPTION_STALL);
    }

    /**
     * The exchange, and then back to the stall - he has more than one thing and a player who has
     * just bought the first should not have to find him again for the second.
     */
    protected void handOver(CrabWares ware) {
        if (!ware.buy()) {
            text.addPara("You come up short, and he is kind enough to talk about"
                    + " the weather until you have stopped counting.");

            showStall();
            return;
        }

        text.addPara("It comes out of a pocket you would not have guessed at, and"
                + " into your hands before the credits have finished moving.");

        //booked at the first sale rather than at the goodbye: this is the moment the event was for,
        //and a player who buys and then closes the dialog has still interacted with it
        if (!sold && BarEventManager.getInstance() != null) {
            sold = true;
            BarEventManager.getInstance().notifyWasInteractedWith(this);
        }

        //and the stall is rebuilt, so what was just bought is not on it. With nothing left the
        //conversation has nowhere to go, which is the one place this ends itself
        if (!CrabWares.isAnythingLeft()) {
            text.addPara("\"That is the coat,\" he says, and sounds relieved about"
                    + " it. \"Now I am only a man with crabs.\"");

            done = true;
            return;
        }

        showStall();
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData instanceof CrabWares) {
            showWare((CrabWares) optionData);
            return;
        }

        String id = optionData == null ? "" : optionData.toString();

        if (id.startsWith(OPTION_BUY)) {
            handOver(CrabWares.valueOf(id.substring(OPTION_BUY.length())));
            return;
        }

        if (OPTION_STALL.equals(id)) {
            showStall();
            return;
        }

        text.addPara("He takes it well. \"Nobody wants crabs,\" he says, to the room, and goes"
                + " back to trying to sell them to it.");

        done = true;
    }

    protected static String describePrice(CrabWares ware) {
        return Misc.getDGSCredits(ware.credits) + " and " + ware.getCatch().describe();
    }
}
