package catchrelease.commands;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import java.util.ArrayList;
import java.util.List;


public class AllFish implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign() || Global.getSector() == null
                || Global.getSector().getPlayerFleet() == null) {
            return CommandResult.WRONG_CONTEXT;
        }

        final int amount;
        try {
            amount = Integer.parseInt(args.trim());
        } catch (NumberFormatException ex) {
            return CommandResult.BAD_SYNTAX;
        }

        if (amount <= 0) return CommandResult.BAD_SYNTAX;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int species = 0;

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || spec.id.isBlank()) continue;

            List<FishCatch> crate = new ArrayList<>(amount);
            float aberration = (spec.minAberration + spec.maxAberration) * 0.5f;

            for (int i = 0; i < amount; i++) {
                FishCatch specimen = FishCatch.roll(spec, aberration);
                if (specimen != null) crate.add(specimen);
            }

            if (crate.isEmpty()) continue;

            cargo.addSpecial(FishItems.toBundle(crate), 1);
            species++;
        }

        Console.showMessage("Added " + amount + " of all " + species
                + " fish species to the player fleet's cargo ("
                + ((long) amount * species) + " specimens).");

        return CommandResult.SUCCESS;
    }
}
