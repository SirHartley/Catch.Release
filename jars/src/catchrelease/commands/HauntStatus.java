package catchrelease.commands;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LegendaryHaunt;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

/** Displays the state needed to diagnose legendary chase and haunt behavior. */
public class HauntStatus implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign() || Global.getSector() == null
                || Global.getSector().getPlayerFleet() == null) {
            return CommandResult.WRONG_CONTEXT;
        }

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        StarSystemAPI here = player.getContainingLocation() instanceof StarSystemAPI system
                ? system : null;

        LegendaryHaunt haunt = LegendaryHaunt.getInstance();
        if (haunt == null) {
            Console.showMessage("Haunt script: not registered.");
        } else if (haunt.getActiveSpeciesId() == null) {
            Console.showMessage("Haunt: none active.");
        } else {
            Console.showMessage(String.format(
                    "Haunt: %s, intensity %.2f, unseen %.1fs, modules %d.",
                    haunt.getActiveSpeciesId(), haunt.getIntensity(),
                    haunt.getSinceSeen(), haunt.getModuleCount()));
        }

        Console.showMessage("Player system: " + (here == null
                ? "none (player is in hyperspace)" : here.getName() + " [" + here.getId() + "]"));

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.rarity != FishRarity.LEGENDARY) continue;

            StringBuilder line = new StringBuilder(spec.id).append(": ");

            if (LegendaryChases.isCaught(spec.id)) {
                Console.showMessage(line.append("caught; spawning disabled.").toString());
                continue;
            }

            String hostId = LegendaryChases.getHostSystemId(spec);
            line.append("host=").append(describeSystem(hostId));
            if (here != null && here.getId().equals(hostId)) line.append(" [HERE]");

            line.append(", provoked=").append(LegendaryChases.isProvoked(spec.id));
            if (LegendaryShields.isHauntSuppressed(spec)) {
                line.append(", haunt suppressed (hull shield intact)");
            }
            if (LegendaryChases.isRevealed(spec.id)) line.append(", revealed");

            if (here != null) {
                line.append(", ").append(describeMote(spec, here, player));
                if (haunt != null && haunt.isSightedNow(spec, here)) line.append(" [SIGHTED]");
            }

            Console.showMessage(line.toString());
        }

        return CommandResult.SUCCESS;
    }

    protected static String describeSystem(String systemId) {
        if (systemId == null) return "NONE";

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getId().equals(systemId)) {
                return system.getName() + " [" + systemId + "]";
            }
        }

        return "unknown [" + systemId + "]";
    }

    protected static String describeMote(FishSpec spec, StarSystemAPI here,
                                         CampaignFleetAPI player) {
        float nearest = Float.MAX_VALUE;
        boolean diving = false;

        for (SectorEntityToken candidate
                : here.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (candidate.isExpired()) continue;
            if (!(candidate.getCustomPlugin() instanceof FishEntityPlugin fish)) continue;
            if (fish.isPhantom() || fish.isDecoy() || fish.getFishSpec() == null) continue;
            if (!spec.id.equals(fish.getFishSpec().id)) continue;

            float distance = Misc.getDistance(player.getLocation(), candidate.getLocation());
            if (distance < nearest) {
                nearest = distance;
                diving = fish.isDiving();
            }
        }

        if (nearest == Float.MAX_VALUE) return "no active mote in system";

        return String.format("mote distance=%du%s, sight range=%du",
                (int) nearest, diving ? " (diving)" : "", (int) LegendaryHaunt.SIGHT_RANGE);
    }
}
