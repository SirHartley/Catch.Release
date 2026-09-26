package catchrelease.campaign.crime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

public class HarpoonedFleetFID extends FleetInteractionDialogPluginImpl {

    public static final String HIGHLIGHT_COMMS = "$highlightComms";
    public static final String AUTO_COMMS_FLAG = "$catchreleaseHarpoonAutoComms";

    public static boolean isAutoCommsRequested(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(AUTO_COMMS_FLAG);
    }

    public static void openComms(CampaignFleetAPI fleet) {
        if (fleet == null) return;

        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        memory.set(AUTO_COMMS_FLAG, true);

        if (!Global.getSector().getCampaignUI().showInteractionDialog(fleet)) {
            memory.unset(AUTO_COMMS_FLAG);
        }
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        boolean autoOpenComms = consumeAutoComms(dialog);

        // before super: vanilla reads/clears this key while building the comm link option in super.init
        highlightComms(dialog);

        super.init(dialog);

        if (autoOpenComms && !inConversation) optionSelected(null, OptionId.OPEN_COMM);
    }

    protected boolean consumeAutoComms(InteractionDialogAPI dialog) {
        if (dialog == null) return false;
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return false;

        MemoryAPI memory = dialog.getInteractionTarget().getMemoryWithoutUpdate();
        boolean requested = memory.getBoolean(AUTO_COMMS_FLAG);
        memory.unset(AUTO_COMMS_FLAG);

        return requested;
    }

    protected void highlightComms(InteractionDialogAPI dialog) {
        if (dialog == null) return;
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return;

        CampaignFleetAPI other = (CampaignFleetAPI) dialog.getInteractionTarget();
        if (!HarpoonOffence.wasHarpooned(other)) return;

        if (!HarpoonOffence.isDemanding(other)) return;

        other.getMemoryWithoutUpdate().set(HIGHLIGHT_COMMS, true, 0f);
    }
}
