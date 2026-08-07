package catchrelease.campaign.crime;

import catchrelease.campaign.fish.fisherman.FishermanFID;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * Hands the game the two encounter screens the mod writes in Java: the fleets we have wronged, and
 * the fishing boats.
 * <p>
 * Every word either of them says still comes out of {@code rules.csv}, and so does all of the hulk
 * and the castaway. What cannot live in the sheet is the screen <i>around</i> the words - a fleet
 * interaction is engage, disengage, comm link, leave, and that is a plugin's shape rather than a
 * conversation's. Both picks here only reach into that shape: one to fire a greeting the encounter
 * would otherwise never ask for, the other to mark the comm link as the thing to click.
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

        if (FishermanSpawner.isFisherman(fleet)) {
            return new PluginPick<InteractionDialogPlugin>(
                    new FishermanFID(), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }

        return null;
    }
}
