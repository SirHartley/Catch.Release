package catchrelease.campaign.fish.fisherman.chain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

// T1: the treasure rumour. The Fisherman's compass comes up as a golden chest in one system's lamp catches.
public final class TreasureRumour {

    public static final String COMPASS = "catchrelease_compass";

    private TreasureRumour() {
    }

    public static SpecialItemData compass() {
        return new SpecialItemData(COMPASS, null);
    }

    public static boolean isCarried() {
        CargoAPI cargo = getPlayerCargo();

        return cargo != null && cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, compass()) > 0f;
    }

    protected static CargoAPI getPlayerCargo() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return null;

        return Global.getSector().getPlayerFleet().getCargo();
    }
}
