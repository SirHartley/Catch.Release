package catchrelease.commands;

import catchrelease.distress.DistressCallFramework;
import catchrelease.distress.vanilla.VanillaDistressCallSpawner;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.Console;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SpawnDistressCall implements BaseCommandWithSuggestion {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign() || Global.getSector() == null
                || Global.getSector().getPlayerFleet() == null) {
            return CommandResult.WRONG_CONTEXT;
        }
        if (!isAvailable(context)) {
            Console.showMessage("SpawnDistressCall is only available while fully in hyperspace.");
            return CommandResult.ERROR;
        }

        String eventId = args == null ? "" : args.trim().toLowerCase(Locale.ROOT);
        if (eventId.isEmpty() || !getEventIds().contains(eventId)) {
            return CommandResult.BAD_SYNTAX;
        }

        StarSystemAPI system;
        if (VanillaDistressCallSpawner.hasId(eventId)) {
            system = DistressCallFramework.claimVanillaSystemForTesting();
            if (system != null && !VanillaDistressCallSpawner.spawn(eventId, system)) system = null;
        } else {
            system = DistressCallFramework.spawnForTesting(eventId);
        }

        if (system == null) {
            Console.showMessage("No naturally eligible, unreserved nearby system is available, "
                    + "or another distress call is already active.");
            return CommandResult.ERROR;
        }

        Console.showMessage("Spawned " + eventId + " in " + system.getNameWithLowercaseType() + ".");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous,
                                       CommandContext context) {
        if (parameter != 0 || !isAvailable(context)) return Collections.emptyList();
        return getEventIds();
    }

    private static List<String> getEventIds() {
        List<String> result = new ArrayList<>(VanillaDistressCallSpawner.getIds());
        result.addAll(DistressCallFramework.getEventIds());
        return result;
    }

    private static boolean isAvailable(CommandContext context) {
        if (!context.isInCampaign() || Global.getSector() == null) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        return player != null && player.isInHyperspace() && !player.isInHyperspaceTransition();
    }
}
