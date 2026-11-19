package catchrelease.abilities;

import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.abilities.rod.scripts.FishingDroneSwarmScript;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class FishingRigs {

    public static boolean isAnyRunning() {
        return SearchlightAbilityPlugin.isBreaching()
                || FishingDroneSwarmScript.getExisting() != null
                || HarpoonEntityPlugin.isAnyLineOut();
    }

    // follows the Fitted line: modules that stay fitted while their fishing ship is not ready
    public static void addInactiveModules(TooltipMakerAPI tooltip, Tackle.Fit rig, float pad) {
        List<Tackle> idle = TackleManager.getIdle(rig);
        if (idle.isEmpty()) return;

        List<String> names = new ArrayList<>();
        for (Tackle tackle : idle) names.add(tackle.name);

        String hull = Global.getSettings().getHullSpec(FishingShips.forRig(rig).hullId).getHullName();
        tooltip.addPara("Inactive: %s. Your %s is mothballed, short of crew or below %s combat readiness.", pad,
                Misc.getHighlightColor(), String.join(", ", names), hull,
                Math.round(FishingShips.MIN_CR * 100f) + "%");
    }
}
