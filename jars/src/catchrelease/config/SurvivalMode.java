package catchrelease.config;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;

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
}
