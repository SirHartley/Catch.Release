package catchrelease.abilities;

import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.abilities.rod.scripts.FishingDroneSwarmScript;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;


public class FishingRigs {

    public static boolean isAnyRunning() {
        return SearchlightAbilityPlugin.isBreaching()
                || FishingDroneSwarmScript.getExisting() != null
                || HarpoonEntityPlugin.isAnyLineOut();
    }
}
