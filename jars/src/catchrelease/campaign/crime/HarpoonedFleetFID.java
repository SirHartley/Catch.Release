package catchrelease.campaign.crime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.util.Misc;

public class HarpoonedFleetFID extends FleetInteractionDialogPluginImpl {

    public static final String HIGHLIGHT_COMMS = "$highlightComms";
    public static final String AUTO_COMMS_FLAG = "$catchreleaseHarpoonAutoComms";
    public static final int LINES = 4;
    public static final String LINE_KEY = "$catchreleaseHarpoonLine";

    public static final String NAME_KEY = "$otherFleetFactionArticle";
    public static final String NAME_CAP_KEY = "$otherFleetFactionArticleCap";

    protected boolean spoken = false;

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

        speak();
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

    protected CampaignFleetAPI getOtherFleet() {
        if (dialog == null) return null;
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return null;

        return (CampaignFleetAPI) dialog.getInteractionTarget();
    }
}
