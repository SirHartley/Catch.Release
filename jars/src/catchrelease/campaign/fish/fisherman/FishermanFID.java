package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.tutorial.FishingIntro;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * Points a player who has walked up to a fishing boat at the one thing on the screen worth clicking.
 * <p>
 * A trawler is a fleet, so approaching one opens the ordinary encounter - engage, disengage, open a
 * comm link, leave. That screen is written for somebody deciding whether to fight, and a player who
 * has never heard of the trade reads it as exactly that and leaves. Everything the boat is for is
 * behind the link.
 * <p>
 * Vanilla already has two ways to say so and this uses both, at their own strengths: an unstarted
 * campaign gets {@code $hailing}, which turns the option into "Accept the comm request" and reads as
 * the boat having called first, which is what a stranger running lights past the picket would get.
 * A campaign part-way up the ladder gets {@code $highlightComms}, which only colours the option -
 * they know what the boat is, they just have a rung waiting at it. Neither is needed once the
 * introduction is finished. Both keys self-clear when the link is opened.
 */
public class FishermanFID extends FleetInteractionDialogPluginImpl {

    /**
     * Vanilla's keys for "this fleet wants to talk", read and cleared by {@code updateMainState}
     * while it builds the comm link option. Neither is in MemFlags - vanilla writes them from rule
     * commands and reads them as literals.
     */
    public static final String HAILING = "$hailing";
    public static final String HIGHLIGHT_COMMS = "$highlightComms";

    /** Before super, because the option is built inside {@code super.init}. */
    @Override
    public void init(InteractionDialogAPI dialog) {
        offerTheLink(dialog);

        super.init(dialog);
    }

    protected void offerTheLink(InteractionDialogAPI dialog) {
        if (dialog == null) return;
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return;

        CampaignFleetAPI boat = (CampaignFleetAPI) dialog.getInteractionTarget();
        if (!FishermanSpawner.isFisherman(boat)) return;

        if (FishingIntro.isAtLeast(FishingIntro.DONE)) return;

        MemoryAPI memory = boat.getMemoryWithoutUpdate();

        if (FishingIntro.getStage() == FishingIntro.UNSTARTED) {
            memory.set(HAILING, true, 0f);
        } else {
            memory.set(HIGHLIGHT_COMMS, true, 0f);
        }
    }
}
