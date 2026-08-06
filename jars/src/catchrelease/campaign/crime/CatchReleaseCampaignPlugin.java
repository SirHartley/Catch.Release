package catchrelease.campaign.crime;

import catchrelease.campaign.fish.fisherman.FishermanDialog;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * Hands the game our own encounter screens - the fleets we have wronged, and the one fleet that
 * wants to talk shop. Keyed on flags on the fleet, at the narrowest priority, so other mods
 * overriding encounters still can.
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

        if (FishermanSpawner.isFisherman((CampaignFleetAPI) target)) {
            return new PluginPick<InteractionDialogPlugin>(
                    new FishermanDialog(), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }

        if (!HarpoonOffence.wasHarpooned((CampaignFleetAPI) target)) return null;

        return new PluginPick<InteractionDialogPlugin>(
                new HarpoonedFleetFID(), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
}
