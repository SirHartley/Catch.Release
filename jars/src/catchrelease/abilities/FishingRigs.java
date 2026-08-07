package catchrelease.abilities;

import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.abilities.rod.scripts.FishingDroneSwarmScript;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;

/**
 * One answer to "is a fishing rig running": breach lamps lit, a drone swarm out (pond-cast or
 * roaming), or a player harpoon line in the water. For anything reacting to fishing being in
 * progress rather than to one rig in particular - the coherence overlay is the first such thing.
 */
public class FishingRigs {

    public static boolean isAnyRunning() {
        return SearchlightAbilityPlugin.isBreaching()
                || FishingDroneSwarmScript.getExisting() != null
                || HarpoonEntityPlugin.isAnyLineOut();
    }
}
