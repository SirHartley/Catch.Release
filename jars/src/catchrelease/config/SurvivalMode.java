package catchrelease.config;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

// Misc.isExtended depends on the game state and the loaded save, so callers never cache it
public final class SurvivalMode {

    private SurvivalMode() {
    }

    public static boolean isOn() {
        return Misc.isExtended();
    }

    // the normal-mode value, a friend's price against ext_shipBuyPriceMult (5x to 8x) at a market
    public static int getShipPrice(String hullId) {
        return Math.round(Global.getSettings().getHullSpec(hullId).getBaseValue());
    }

    // what a market asks in survival; ext_shipBuyPriceMult starts at the frigate, HullSize ordinal 2, as vanilla reads it
    public static int getMarketShipPrice(String hullId) {
        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
        float mult = isOn() ? Global.getSettings().optFloatFromArray("ext_shipBuyPriceMult",
                spec.getHullSize().ordinal() - 2, 1f) : 1f;

        return Math.round(spec.getBaseValue() * mult);
    }

    // Misc.getModifiedMissionReward's distance bonus as a multiplier, so it also covers pay that follows the fish
    public static float getMissionRewardMult(SectorEntityToken location) {
        if (!isOn() || location == null) return 1f;

        StarSystemAPI askonia = Global.getSector().getStarSystem("askonia");
        Vector2f origin = askonia == null ? new Vector2f() : askonia.getLocation();
        float distance = Misc.getDistanceLY(location.getLocationInHyperspace(), origin);

        SettingsAPI settings = Global.getSettings();
        float min = settings.getFloat("ext_missionRewardMinDist");
        float max = settings.getFloat("ext_missionRewardMaxDist");
        if (distance <= min || max <= min) return 1f;

        float share = (Math.min(distance, max) - min) / (max - min);

        return 1f + settings.getFloat("ext_missionRewardMaxBonusFraction") * share;
    }
}
