package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.config.SurvivalMode;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

public final class ShipReplacement {

    public static final String ANY = "$catchreleaseReplaceAny";

    public static final String HARPOON = "$catchreleaseReplaceHarpoon";
    public static final String HARPOON_PRICE = "$catchreleaseReplaceHarpoonPrice";
    public static final String HARPOON_CAN_BUY = "$catchreleaseReplaceHarpoonCanBuy";
    public static final String ROD = "$catchreleaseReplaceRod";
    public static final String ROD_PRICE = "$catchreleaseReplaceRodPrice";
    public static final String ROD_CAN_BUY = "$catchreleaseReplaceRodCanBuy";
    public static final String LAMP = "$catchreleaseReplaceLamp";
    public static final String LAMP_PRICE = "$catchreleaseReplaceLampPrice";
    public static final String LAMP_CAN_BUY = "$catchreleaseReplaceLampCanBuy";

    private ShipReplacement() {
    }

    // expiry 0, as every CatchReleaseCMD token
    public static void writeTokens(MemoryAPI local) {
        boolean any = false;
        for (FishingShips.Ship ship : FishingShips.Ship.values()) any |= isOffered(ship);
        local.set(ANY, any, 0);

        writeShip(local, FishingShips.Ship.HARPOON, HARPOON, HARPOON_PRICE, HARPOON_CAN_BUY);
        writeShip(local, FishingShips.Ship.ROD, ROD, ROD_PRICE, ROD_CAN_BUY);
        writeShip(local, FishingShips.Ship.LAMP, LAMP, LAMP_PRICE, LAMP_CAN_BUY);
    }

    public static boolean isOffered(FishingShips.Ship ship) {
        return SurvivalMode.isOn() && FishingShips.isLost(ship);
    }

    public static int getPrice(FishingShips.Ship ship) {
        return SurvivalMode.getMarketShipPrice(ship.hullId);
    }

    public static boolean canBuy(FishingShips.Ship ship) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        return player != null && player.getCargo().getCredits().get() >= getPrice(ship);
    }

    public static boolean buy(String shipName, TextPanelAPI text) {
        FishingShips.Ship ship = parse(shipName);
        if (ship == null || !isOffered(ship) || !canBuy(ship)) return false;

        int price = getPrice(ship);
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(price);
        if (text != null) AddRemoveCommodity.addCreditsLossText(price, text);

        FishReward.showReceipts(text, List.of(FishReward.ship(ship.hullId).grantWithReceipt()));

        return true;
    }

    protected static void writeShip(MemoryAPI local, FishingShips.Ship ship, String offered, String price,
                                    String canBuy) {
        boolean offer = isOffered(ship);
        local.set(offered, offer, 0);
        local.set(price, offer ? Misc.getWithDGS(getPrice(ship)) : "", 0);
        local.set(canBuy, offer && canBuy(ship), 0);
    }

    protected static FishingShips.Ship parse(String name) {
        for (FishingShips.Ship ship : FishingShips.Ship.values()) {
            if (ship.name().equalsIgnoreCase(name)) return ship;
        }

        return null;
    }
}
