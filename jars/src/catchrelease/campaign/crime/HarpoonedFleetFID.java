package catchrelease.campaign.crime;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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

    @Override
    protected void updatePreCombat() {
        speak();

        super.updatePreCombat();
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

        return lines[Math.abs(other.getId().hashCode()) % lines.length];
    }

    protected CampaignFleetAPI getOtherFleet() {
        if (dialog == null) return null;
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return null;

        return (CampaignFleetAPI) dialog.getInteractionTarget();
    }
}
