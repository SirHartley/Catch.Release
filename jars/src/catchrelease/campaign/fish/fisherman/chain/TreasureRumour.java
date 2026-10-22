package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

// T1: the treasure rumour. The Fisherman's compass comes up as a golden chest in one system's lamp catches.
public final class TreasureRumour {

    public static final String COMPASS = "catchrelease_compass";

    public static final float MIN_LY = 2f;
    public static final float MAX_LY = 10f;

    private TreasureRumour() {
    }

    // Keeps the saved system while it can still host the rumour, so a declined offer names the same place again.
    public static boolean prepareOffer() {
        FishermanChain.Saved saved = FishermanChain.get(true);
        if (saved == null) return false;
        if (isCandidate(FishRumors.findSystem(saved.systemId))) return true;

        StarSystemAPI system = pickSystem();
        if (system == null) return false;

        saved.systemId = system.getId();
        saved.systemName = system.getNameWithNoType();

        return true;
    }

    public static boolean start() {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved == null || !FishermanChain.isAt(FishermanChain.Stage.T1_OFFERED)) return false;

        StarSystemAPI system = FishRumors.findSystem(saved.systemId);
        if (!isCandidate(system) || FishRumors.publishHeld(system, FishRumors.Kind.LOOT) == null) return false;

        FishermanChain.setStage(FishermanChain.Stage.T1_ACTIVE);

        return true;
    }

    public static FishRumors.Saved getRumour() {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null ? null : FishRumors.findActive(saved.systemId, FishRumors.Kind.LOOT);
    }

    public static SpecialItemData compass() {
        return new SpecialItemData(COMPASS, null);
    }

    public static boolean isCarried() {
        CargoAPI cargo = getPlayerCargo();

        return cargo != null && cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, compass()) > 0f;
    }

    // A nearby system first; failing that, the nearest one that qualifies at any distance.
    protected static StarSystemAPI pickSystem() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Vector2f from = player == null ? new Vector2f() : player.getLocationInHyperspace();

        WeightedRandomPicker<StarSystemAPI> near = new WeightedRandomPicker<>();
        StarSystemAPI nearest = null;
        float best = Float.MAX_VALUE;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!isCandidate(system)) continue;

            float distance = Misc.getDistanceLY(from, system.getLocation());
            if (distance >= MIN_LY && distance <= MAX_LY) near.add(system, 1f);
            if (distance < best) {
                best = distance;
                nearest = system;
            }
        }

        StarSystemAPI pick = near.pick();

        return pick != null ? pick : nearest;
    }

    // The chest only comes up in lamp catches, so the system must hold a non-legendary pattern the lamps can reach.
    protected static boolean isCandidate(StarSystemAPI system) {
        if (system == null || system.getId() == null || system.getCenter() == null) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER) || system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) return false;
        if (FishRumors.isOccupied(system.getId())) return false;

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.rarity == null || spec.rarity == FishRarity.LEGENDARY) continue;
            if (spec.spawnWeight <= 0f || !spec.hasHabitat()) continue;
            if (FishRanges.matches(spec, system, CatchImplement.BREACH_LAMP)) return true;
        }

        return false;
    }

    protected static CargoAPI getPlayerCargo() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return null;

        return Global.getSector().getPlayerFleet().getCargo();
    }
}
