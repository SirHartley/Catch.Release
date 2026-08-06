package catchrelease.campaign.crime;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

/**
 * What a crew has to say to the person who harpooned them. Vanilla's encounter (options,
 * disengage rules, allied-fleet branches) is left untouched; this only adds a line.
 */
public class HarpoonedFleetFID extends FleetInteractionDialogPluginImpl {

    /** Said once per encounter rather than on every refresh of the options. */
    protected boolean spoken = false;

    /**
     * Vanilla's key for "this fleet is worth talking to", read and cleared by
     * {@code updateMainState} while building the comm link option. Not in MemFlags - vanilla only
     * writes it from the HighlightComms rule command and reads it as a literal.
     */
    public static final String HIGHLIGHT_COMMS = "$highlightComms";

    /**
     * Speaks once as the encounter opens - {@code updatePreCombat} only fires for options that
     * commit to a fight, so it would miss a player who talks and leaves. Runs after super so the
     * line lands under the encounter description, not above it. Not resaid on reinit ({@link
     * #spoken}).
     */
    @Override
    public void init(InteractionDialogAPI dialog) {
        //before super: vanilla reads/clears this key while building the comm link option in super.init
        highlightComms(dialog);

        super.init(dialog);

        speak();
    }

    /**
     * Highlights the comm link, since a harpooned fleet always has a comm rule waiting (willing or
     * unwilling to talk). Set through vanilla's key rather than coloring the option directly, so it
     * self-clears once the link is opened.
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

    /** Picked deterministically off the fleet id, so the same crew always says the same line. */
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
