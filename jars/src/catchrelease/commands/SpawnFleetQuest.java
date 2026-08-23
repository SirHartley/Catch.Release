package catchrelease.commands;

import catchrelease.campaign.fish.jobs.fleet.FleetQuestSpawner;
import catchrelease.campaign.fish.jobs.fleet.FleetQuestType;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.Console;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpawnFleetQuest implements BaseCommandWithSuggestion {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign() || Global.getSector() == null
                || Global.getSector().getPlayerFleet() == null) {
            return CommandResult.WRONG_CONTEXT;
        }

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (!(player.getContainingLocation() instanceof StarSystemAPI system)) {
            Console.showMessage("SpawnFleetQuest is only available inside a star system.");
            return CommandResult.ERROR;
        }

        FleetQuestType type = FleetQuestType.getLocalOffer(args);
        if (type == null) return CommandResult.BAD_SYNTAX;

        if (FleetQuestSpawner.countActive() > 0) {
            Console.showMessage("A fleet quest offer or accepted fleet quest is already active.");
            return CommandResult.ERROR;
        }

        CampaignFleetAPI fleet = FleetQuestSpawner.spawnForTesting(type);
        if (fleet == null) {
            Console.showMessage("Could not create a route-backed scavenger fleet in "
                    + system.getNameWithLowercaseType() + ".");
            return CommandResult.ERROR;
        }

        Console.showMessage("Spawned a scavenger fleet with the " + type.getId()
                + " quest in " + system.getNameWithLowercaseType() + ".");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous,
                                       CommandContext context) {
        if (parameter != 0 || !isAvailable(context)) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        for (FleetQuestType type : FleetQuestType.getLocalOffers()) {
            result.add(type.getId());
        }
        return result;
    }

    private static boolean isAvailable(CommandContext context) {
        if (!context.isInCampaign() || Global.getSector() == null) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        return player != null && player.getContainingLocation() instanceof StarSystemAPI;
    }
}
