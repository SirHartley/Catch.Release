package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

// T2: the captain's fleet. A captain the Fisherman knows is hunting an epic in an empty system; the player's lamps
// find it for him.
public final class CaptainsFleet {

    private CaptainsFleet() {
    }

    // Keeps the saved system and species while they still qualify, so a declined offer names the same place again.
    public static boolean prepareOffer() {
        FishermanChain.Saved saved = FishermanChain.get(true);
        if (saved == null) return false;

        StarSystemAPI kept = FishRumors.findSystem(saved.t2SystemId);
        if (isCandidate(kept) && isTarget(FishSpecLoader.getFishSpec(saved.t2SpeciesId), kept)) return true;

        StarSystemAPI system = pickSystem();
        FishSpec species = system == null ? null : pickSpecies(system);
        if (species == null) return false;

        saved.t2SystemId = system.getId();
        saved.t2SystemName = system.getNameWithNoType();
        saved.t2SpeciesId = species.id;

        return true;
    }

    public static StarSystemAPI getSystem() {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null ? null : FishRumors.findSystem(saved.t2SystemId);
    }

    public static String getSpeciesName() {
        FishermanChain.Saved saved = FishermanChain.get(false);
        FishSpec spec = saved == null ? null : FishSpecLoader.getFishSpec(saved.t2SpeciesId);

        return spec == null ? "" : spec.getDisplayName();
    }

    // The nearest qualifying system, at any distance.
    protected static StarSystemAPI pickSystem() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Vector2f from = player == null ? new Vector2f() : player.getLocationInHyperspace();

        StarSystemAPI nearest = null;
        float best = Float.MAX_VALUE;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!isCandidate(system) || pickSpecies(system) == null) continue;

            float distance = Misc.getDistanceLY(from, system.getLocation());
            if (distance < best) {
                best = distance;
                nearest = system;
            }
        }

        return nearest;
    }

    // No markets or colonies, and a primary star the lamps can circle: never a nebula, pulsar or black hole.
    protected static boolean isCandidate(StarSystemAPI system) {
        if (system == null || system.getId() == null || system.isNebula()) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER) || system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) return false;
        if (system.getHyperspaceAnchor() == null || OuterReaches.isPopulated(system)) return false;
        if (FishRumors.isOccupied(system.getId())) return false;

        PlanetAPI star = system.getStar();
        if (star == null || star.getSpec() == null) return false;

        return !star.getSpec().isPulsar() && !star.getSpec().isBlackHole();
    }

    protected static FishSpec pickSpecies(StarSystemAPI system) {
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (isTarget(spec, system)) picker.add(spec, spec.spawnWeight);
        }

        return picker.pick();
    }

    // An ordinary epic the lamps can find there; Lantern Jack and the other legendaries never qualify.
    protected static boolean isTarget(FishSpec spec, StarSystemAPI system) {
        if (spec == null || system == null || spec.rarity != FishRarity.EPIC) return false;
        if (spec.spawnWeight <= 0f || !spec.hasHabitat()) return false;

        return FishRanges.matches(spec, system, CatchImplement.BREACH_LAMP);
    }
}
