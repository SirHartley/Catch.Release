package catchrelease.commands;

import catchrelease.campaign.fish.fisherman.CoreFisherSpawner;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

/** Console Commands integration for placing the ordinary visiting Fisherman beside the player. */
public class SpawnFisherman implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign() || Global.getSector() == null
                || Global.getSector().getPlayerFleet() == null) {
            return CommandResult.WRONG_CONTEXT;
        }
        if (args != null && !args.trim().isEmpty()) return CommandResult.BAD_SYNTAX;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI location = player.getContainingLocation();
        if (!(location instanceof StarSystemAPI system)) {
            Console.showMessage("The player fleet is not currently inside a star system.");
            return CommandResult.ERROR;
        }

        CampaignFleetAPI existing = CoreFisherSpawner.getAnyBoat(system);
        CampaignFleetAPI fisherman = FishermanSpawner.spawnNow(system, player.getLocation());
        if (fisherman == null) {
            Console.showMessage("Could not spawn the Fisherman in " + system.getNameWithLowercaseType() + ".");
            return CommandResult.ERROR;
        }

        if (existing == fisherman) {
            Console.showMessage("The Fisherman is already in " + system.getNameWithLowercaseType() + ".");
        } else {
            Console.showMessage("Spawned the Fisherman in " + system.getNameWithLowercaseType() + ".");
        }
        return CommandResult.SUCCESS;
    }
}