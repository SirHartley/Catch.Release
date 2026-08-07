package catchrelease.campaign.crime;

import catchrelease.campaign.fish.fisherman.FishermanDialog;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import catchrelease.campaign.fish.tutorial.Castaway;
import catchrelease.campaign.fish.tutorial.CastawayDialog;
import catchrelease.campaign.fish.tutorial.TutorialWreck;
import catchrelease.campaign.fish.tutorial.TutorialWreckDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * Hands the game our own encounter screens - the fleets we have wronged, the fleets that want to
 * talk shop, and the two pieces of scenery the introduction is built out of. Keyed on flags on the
 * target, at the narrowest priority, so other mods overriding encounters still can.
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
        //the two things here that are not fleets: the hulk with the line in it, and the man who
        //was put off a boat for looking at what came out of the water
        if (TutorialWreck.isWreck(target)) {
            return new PluginPick<InteractionDialogPlugin>(
                    new TutorialWreckDialog(), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }

        if (Castaway.isCastaway(target)) {
            return new PluginPick<InteractionDialogPlugin>(
                    new CastawayDialog(), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }

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
