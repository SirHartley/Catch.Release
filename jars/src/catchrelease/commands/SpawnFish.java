package catchrelease.commands;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.fisherman.FishermanMapIcon;
import catchrelease.campaign.fish.fisherman.FishermanShelf;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LegendaryHaunt;
import catchrelease.campaign.fish.legendary.LonglinerDecoy;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.Console;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpawnFish implements BaseCommandWithSuggestion {

    private static final float LAMP_SAMPLE_ANGLE = 2f;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign() || Global.getSector() == null
                || Global.getSector().getPlayerFleet() == null) {
            return CommandResult.WRONG_CONTEXT;
        }

        String query = args == null ? "" : args.trim();
        if (query.isEmpty()) return CommandResult.BAD_SYNTAX;

        AddFish.Match match = AddFish.findMatch(query);
        if (match.spec == null) {
            showMatchFailure(query, match);
            return CommandResult.ERROR;
        }

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (!(player.getContainingLocation() instanceof StarSystemAPI system)) {
            Console.showMessage("The player fleet is not currently inside a star system.");
            return CommandResult.ERROR;
        }

        SectorEntityToken pond = findOpenPond(player);
        boolean lampsOn = SearchlightAbilityPlugin.isBreaching();
        Vector2f litPoint = lampsOn ? findLitPoint(player) : null;

        SectorEntityToken spawned;
        String source;

        if (pond != null && match.spec.canBeReachedBy(CatchImplement.POND)) {
            spawned = spawnInPond(match.spec, pond);
            source = "the nearby rupture";
        } else if (litPoint != null && match.spec.canBeReachedBy(CatchImplement.BREACH_LAMP)) {
            prepareLegendary(match.spec, system);
            spawned = spawnUnderLamps(match.spec, system, litPoint);
            source = "the breach lamps";
        } else {
            showInvalidSource(match.spec, pond, lampsOn, litPoint);
            return CommandResult.ERROR;
        }

        if (spawned == null) {
            Console.showMessage("Could not spawn " + match.spec.getDisplayName() + ".");
            return CommandResult.ERROR;
        }

        if (match.fuzzy) {
            Console.showMessage("Matched \"" + query + "\" to "
                    + match.spec.getDisplayName() + " [" + match.spec.id + "].");
        }
        Console.showMessage("Spawned " + match.spec.getDisplayName() + " [" + match.spec.id
                + "] in " + source + ".");

        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous,
                                       CommandContext context) {
        return AddFish.getFishSuggestions(parameter, previous, context);
    }

    private static void showMatchFailure(String query, AddFish.Match match) {
        if (!match.suggestions.isEmpty()) {
            Console.showMessage("Multiple fish match \"" + query + "\". Try one of:");
            for (FishSpec suggestion : match.suggestions) {
                Console.showMessage("  " + suggestion.getDisplayName() + " [" + suggestion.id + "]");
            }
        } else {
            Console.showMessage("No fish id or name matches \"" + query + "\".");
        }
    }

    private static SectorEntityToken findOpenPond(CampaignFleetAPI player) {
        SectorEntityToken nearest = null;
        float nearestDistance = Float.MAX_VALUE;

        for (SectorEntityToken pond : player.getContainingLocation()
                .getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID)) {
            MaskedFishingPondTerrainPlugin plugin =
                    MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
            if (plugin == null || !plugin.isActive()) continue;

            float distance = Misc.getDistance(pond, player);
            if (distance >= pond.getRadius() * PondConstants.POND_INTERACT_RANGE_MULT) continue;
            if (distance >= nearestDistance) continue;

            nearest = pond;
            nearestDistance = distance;
        }

        return nearest;
    }

    private static Vector2f findLitPoint(CampaignFleetAPI player) {
        float area = Searchlight.getArea();
        float bestStrength = 0f;
        Vector2f best = null;

        for (float radius = area * 0.5f; radius <= Searchlight.getMaxReach(); radius += area * 0.5f) {
            for (float angle = 0f; angle < 360f; angle += LAMP_SAMPLE_ANGLE) {
                Vector2f point = MathUtils.getPointOnCircumference(
                        player.getLocation(), radius, angle);
                float strength = SearchlightAbilityPlugin.getBeamStrengthAt(point);

                if (strength <= bestStrength) continue;
                bestStrength = strength;
                best = point;
            }
        }

        return bestStrength > 0f ? best : null;
    }

    private static SectorEntityToken spawnInPond(FishSpec spec, SectorEntityToken pond) {
        MaskedFishingPondTerrainPlugin plugin =
                MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
        if (plugin == null) return null;

        float radius = pond.getRadius() * plugin.activity * 0.5f;
        if (radius < 1f) {
            Console.showMessage("The nearby rupture is still opening.");
            return null;
        }

        float angle = MathUtils.getRandomNumberInRange(0f, 360f);
        Vector2f at = MathUtils.getPointOnCircumference(pond.getLocation(), radius, angle);
        Vector2f to = MathUtils.getPointOnCircumference(pond.getLocation(), radius, angle + 180f);

        SectorEntityToken mote = pond.getContainingLocation().addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                new FishEntityPlugin.Params(to, spec.id, pond));
        mote.setLocation(at.x, at.y);
        mote.getMemoryWithoutUpdate().set(FishEntityPlugin.HOLDS_KEY, true);

        return mote;
    }

    private static SectorEntityToken spawnUnderLamps(FishSpec spec, LocationAPI location,
                                                      Vector2f at) {
        SectorEntityToken buried = location.addCustomEntity(
                Misc.genUID(), null, FishConstants.BURIED_ENTITY_ID, null,
                new BuriedMoteEntityPlugin.Params(spec.id));
        buried.setLocation(at.x, at.y);

        return buried;
    }

    private static void prepareLegendary(FishSpec spec, StarSystemAPI system) {
        if (spec.rarity != FishRarity.LEGENDARY) return;

        LegendaryHaunt.resetForTesting();
        clearLegendaryEntities();

        LegendaryChases.Chase state = LegendaryChases.getState(spec.id);
        state.systemId = system.getId();
        state.seenAt = 0L;
        state.caught = false;
        // SpawnFish creates the actual mote, so the Longliner's separate boat must stay retired.
        state.revealed = LonglinerDecoy.spawnsAsBoat(spec);
        state.shieldPopped = false;
        state.shieldUnits = -1;
        state.shieldStampAt = 0L;
    }

    private static void clearLegendaryEntities() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (SectorEntityToken mote : new ArrayList<>(
                    system.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG))) {
                if (!(mote.getCustomPlugin() instanceof FishEntityPlugin fish)) continue;

                FishSpec active = fish.getFishSpec();
                if ((active != null && active.rarity == FishRarity.LEGENDARY)
                        || fish.getOrbitAnchor() != null || fish.isDecoy()) {
                    mote.setExpired(true);
                }
            }

            for (SectorEntityToken buried : new ArrayList<>(
                    system.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG))) {
                if (!(buried.getCustomPlugin() instanceof BuriedMoteEntityPlugin fish)) continue;

                FishSpec active = fish.getFishSpec();
                if (active != null && active.rarity == FishRarity.LEGENDARY) {
                    buried.setExpired(true);
                }
            }

            for (CampaignFleetAPI fleet : new ArrayList<>(system.getFleets())) {
                if (!LonglinerDecoy.isDecoyBoat(fleet)) continue;

                FishermanShelf.releaseFor(fleet);
                FishermanMapIcon.removeFor(fleet);
                fleet.despawn();
            }
        }

        Global.getSector().getMemoryWithoutUpdate().unset(LonglinerDecoy.BOAT_KEY);
    }

    private static void showInvalidSource(FishSpec spec, SectorEntityToken pond,
                                          boolean lampsOn, Vector2f litPoint) {
        if (pond == null && !lampsOn) {
            Console.showMessage("Open a nearby rupture or turn on the breach lamps first.");
            return;
        }

        if (lampsOn && litPoint == null) {
            Console.showMessage("The breach lamps are on, but no live beam is available yet.");
            return;
        }

        if (pond != null && !spec.canBeReachedBy(CatchImplement.POND)) {
            Console.showMessage(spec.getDisplayName()
                    + " cannot be reached through this rupture. Use the breach lamps.");
            return;
        }

        if (litPoint != null && !spec.canBeReachedBy(CatchImplement.BREACH_LAMP)) {
            Console.showMessage(spec.getDisplayName()
                    + " cannot be reached through the breach lamps. Open a nearby rupture.");
            return;
        }

        Console.showMessage("No valid spawn area is available for " + spec.getDisplayName() + ".");
    }
}
