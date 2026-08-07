package catchrelease.campaign.crime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * Hands the game the one encounter screen the mod writes in Java: the fleets we have wronged.
 * <p>
 * Every word it says still comes out of {@code rules.csv}, and so does all of the hulk and the
 * castaway. What cannot live in the sheet is the screen <i>around</i> the words, and this pick only
 * reaches into that shape - to fire a greeting the encounter would otherwise never ask for.
 * <p>
 * The fishing boats used to be picked here too and are not any more. They never needed a plugin:
 * what they needed was to skip the fleet screen entirely, and vanilla already has a command for
 * that - {@code OpenComms} on a {@code BeginFleetEncounter} row. See
 * {@code catchrelease_fisherEncounter}.
 */
public class CatchReleaseCampaignPlugin extends BaseCampaignPlugin {

    public static final String ID = "catchrelease_campaignPlugin";

    /**
     * Installed once per load. Registration is by id, so an unregister first makes a second call
     * harmless rather than leaving two of these answering the same question.
     */
    public static void register() {
        Global.getSector().unregisterPlugin(ID);
        Global.getSector().registerPlugin(new CatchReleaseCampaignPlugin());
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken target) {
        if (!(target instanceof CampaignFleetAPI)) return null;

        CampaignFleetAPI fleet = (CampaignFleetAPI) target;

        //asked first: a fishing boat somebody put a hook in is a harpooned fleet before it is
        //anything else, and it has a line waiting that the other screen would swallow
        if (HarpoonOffence.wasHarpooned(fleet)) {
            return new PluginPick<InteractionDialogPlugin>(
                    new HarpoonedFleetFID(), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }

        return null;
    }
}
