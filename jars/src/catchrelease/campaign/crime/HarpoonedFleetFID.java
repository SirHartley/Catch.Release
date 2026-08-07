package catchrelease.campaign.crime;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.util.Misc;

/**
 * What a crew has to say to the person who harpooned them. Vanilla's encounter (options, disengage
 * rules, allied-fleet branches) is left untouched; this only fires a line.
 * <p>
 * The line lives in {@code rules.csv} like every other word in the mod. This picks <i>which</i> of
 * them off the fleet id, so the same crew always says the same thing.
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

    /**
     * Fires the sheet's opening line for a harpooned crew.
     * <p>
     * Which of the four they use is picked off the fleet id, so the same crew always says the same
     * thing - but the lines themselves are rows, like every other word in the mod. The faction's
     * own name goes over as a token for the row to use.
     */
    protected void speak() {
        if (spoken || dialog == null) return;
        spoken = true;

        CampaignFleetAPI other = getOtherFleet();
        if (other == null) return;

        String name = other.getFaction() == null
                ? "the crew" : other.getFaction().getDisplayNameWithArticle();

        MemoryAPI memory = other.getMemoryWithoutUpdate();

        memory.set(LINE_KEY, Math.floorMod(other.getId().hashCode(), LINES), 0f);
        memory.set(NAME_KEY, name, 0f);
        memory.set(NAME_CAP_KEY, Misc.ucFirst(name), 0f);

        FireBest.fire(null, dialog, getMemoryMap(), "CatchReleaseHarpoonedGreeting");
    }

    /** How many lines the sheet carries, and where the pick and the name are handed over. */
    public static final int LINES = 4;
    public static final String LINE_KEY = "$catchreleaseHarpoonLine";
    public static final String NAME_KEY = "$otherFleetFactionArticle";
    public static final String NAME_CAP_KEY = "$otherFleetFactionArticleCap";

    protected CampaignFleetAPI getOtherFleet() {
        if (dialog == null) return null;
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return null;

        return (CampaignFleetAPI) dialog.getInteractionTarget();
    }
}
