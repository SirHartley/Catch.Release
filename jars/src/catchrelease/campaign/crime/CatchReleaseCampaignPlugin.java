package catchrelease.campaign.crime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * Hands the game our own encounter screen for the fleets we have wronged, and nothing else.
 * <p>
 * Keyed on a flag on the fleet rather than applied to fleets in general, and at the narrowest
 * priority there is: every other mod that overrides an encounter expects to be able to, and a mod
 * that claims all of them because one of its abilities can annoy people is a bad neighbour.
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
        if (!HarpoonOffence.wasHarpooned((CampaignFleetAPI) target)) return null;

        return new PluginPick<InteractionDialogPlugin>(
                new HarpoonedFleetFID(), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
}
