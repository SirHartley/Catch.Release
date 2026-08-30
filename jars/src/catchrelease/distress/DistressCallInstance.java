package catchrelease.distress;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.CallEvent.CallableEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class DistressCallInstance implements CallableEvent {

    public final String id;
    public final String specId;
    public final String systemId;

    StarSystemAPI system;
    CampaignFleetAPI fleet;
    IntelInfoPlugin intel;
    float pendingDays;
    boolean spawned;
    boolean resolved;

    DistressCallInstance(String specId, StarSystemAPI system) {
        this.id = Misc.genUID();
        this.specId = specId;
        this.systemId = system.getId();
        this.system = system;
    }

    public CampaignFleetAPI getFleet() {
        return fleet;
    }

    public StarSystemAPI getSystem() {
        return system;
    }

    public DistressCallSpec getSpec() {
        return DistressCallRegistry.get(specId);
    }

    public boolean isResolved() {
        return resolved;
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params,
                             Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty() || !"open".equals(params.get(0).getString(memoryMap))) return false;

        DistressCallSpec spec = getSpec();
        if (spec == null || !owns(dialog)) return false;

        return FireAll.fire(ruleId, dialog, memoryMap, spec.dialogTrigger);
    }

    private boolean owns(InteractionDialogAPI dialog) {
        if (resolved || fleet == null || dialog == null || dialog.getInteractionTarget() != fleet) {
            return false;
        }

        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        return memory.getBoolean(DistressCallSettings.ENTITY_FLAG)
                && memory.get(DistressCallSettings.INSTANCE_REF) == this;
    }
}
