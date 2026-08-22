package catchrelease.campaign.crime;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.util.Misc;


public class HarpoonedFleetFID extends FleetInteractionDialogPluginImpl {


    protected boolean spoken = false;


    public static final String HIGHLIGHT_COMMS = "$highlightComms";


    @Override
    public void init(InteractionDialogAPI dialog) {
        // before super: vanilla reads/clears this key while building the comm link option in super.init
        highlightComms(dialog);

        super.init(dialog);

        speak();
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
