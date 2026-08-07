package catchrelease.campaign.crime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * Hands the game the one encounter screen the mod still writes in Java: the fleets we have wronged.
 * <p>
 * Everything else the mod says now goes through {@code rules.csv}. The fishing boats, the hulk and
 * the castaway are all ordinary rules-driven interactions keyed on a flag or a tag, which is what
 * lets their dialogue live in the sheet where the jobs' already does. This is the exception because
 * a harpooned fleet's screen is a <i>fleet interaction</i> - engage, disengage, comm link - and that
 * is a plugin's shape rather than a conversation's.
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
