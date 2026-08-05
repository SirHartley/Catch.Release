package catchrelease.campaign.crime;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

/**
 * What a crew has to say to the person who put a harpoon in them.
 * <p>
 * The encounter itself is left exactly as vanilla built it - every option, every disengage rule,
 * every allied-fleet branch. Stripping those out to say something rude is how a player ends up
 * unable to leave a conversation. This adds a line and nothing else.
 */
public class HarpoonedFleetFID extends FleetInteractionDialogPluginImpl {

    /** Said once per encounter rather than on every refresh of the options. */
    protected boolean spoken = false;

    /**
     * Vanilla's own key for "this fleet is worth talking to", read and cleared by
     * {@code updateMainState} as it builds the comm link option. Not in MemFlags - vanilla only
     * ever writes it from the HighlightComms rule command, and reads it as a literal.
     */
    public static final String HIGHLIGHT_COMMS = "$highlightComms";

    /**
     * Spoken as the encounter opens, which is the only hook that sees every encounter.
     * <p>
     * The obvious-looking one is updatePreCombat, and it is wrong: vanilla only reaches it from the
     * options that commit to a fight, so the line would have been read out on the deployment screen
     * to a player who had already decided to shoot - and never at all to one who looked, talked and
     * left. After super, so it lands under the encounter's own description rather than ahead of it.
     * <p>
     * Cutting a comm link comes back through reinit rather than init, so this stays said once.
     */
    @Override
    public void init(InteractionDialogAPI dialog) {
        //before super, because super is what builds the options: vanilla reads this key while it is
        //adding the comm link and clears it on the way past. Set afterwards it would colour nothing
        //and sit on the fleet waiting to colour the next encounter instead
        highlightComms(dialog);

        super.init(dialog);

        speak();
    }

    /**
     * Lights the comm link up when there is something on the other end of it.
     * <p>
     * Which there is, for as long as this dialogue is the one being used at all: the comm rules are
     * keyed on the same harpooning flag that chooses this plugin, and they come in a pair - one set
     * for a crew still willing to talk about it and another for one that is not - so a harpooned
     * fleet always has a line waiting. Highlighting it says the encounter has changed rather than
     * leaving the player to open a link on the off-chance.
     * <p>
     * Through vanilla's key rather than by colouring the option directly, so it clears itself the
     * moment the link is opened. The highlight is a notice that something is waiting, and once it
     * has been heard there is nothing left to give notice of.
     */
    protected void highlightComms(InteractionDialogAPI dialog) {
        if (dialog == null) return;
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return;

        CampaignFleetAPI other = (CampaignFleetAPI) dialog.getInteractionTarget();
        if (!HarpoonOffence.wasHarpooned(other)) return;

        other.getMemoryWithoutUpdate().set(HIGHLIGHT_COMMS, true, 0f);
    }

    protected void speak() {
        if (spoken || dialog == null) return;
        spoken = true;

        CampaignFleetAPI other = getOtherFleet();
        if (other == null) return;

        Color colour = other.getFaction() == null
                ? Misc.getNegativeHighlightColor()
                : other.getFaction().getBaseUIColor();

        dialog.getTextPanel().addPara(pick(other), colour);
    }

    /**
     * Something to say about the rope.
     * <p>
     * Picked off the fleet's own id rather than at random, so one crew says one thing however many
     * times they are talked to, and the line does not change while it is being read.
     */
    protected String pick(CampaignFleetAPI other) {
        String name = other.getFaction() == null ? "The crew" : other.getFaction().getDisplayNameWithArticle();

        String[] lines = {
                Misc.ucFirst(name) + " opens with a question about the harpoon, and does not appear"
                        + " to be waiting for an answer.",
                "There is a hole in their hull the shape of your fishing gear, and"
                        + " " + name + " would like it noted.",
                Misc.ucFirst(name) + " has your transponder code, the puncture, and a great deal to"
                        + " say about both.",
                "Whatever " + name + " expected to be shot at with today, it was not a rope.",
        };

        return lines[Math.floorMod(other.getId().hashCode(), lines.length)];
    }

    protected CampaignFleetAPI getOtherFleet() {
        if (dialog == null) return null;
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return null;

        return (CampaignFleetAPI) dialog.getInteractionTarget();
    }
}
