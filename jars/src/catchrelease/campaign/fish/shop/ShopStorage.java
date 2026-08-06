package catchrelease.campaign.fish.shop;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.Map;

/**
 * Migration shim for the removed shop-storage counter: old saves may still hold fish under
 * {@link #KEY}. {@link #reclaim()} returns them to the player's hold once, then drops the key.
 * Safe to remove only once no save can predate the counter's removal.
 */
public class ShopStorage {

    public static final String KEY = "$catchrelease_shop_storage";

    /**
     * Moves any stored specimens straight into the hold, ignoring capacity - an overfull hold is
     * visible and fixable, an unreachable fish is not.
     *
     * @return number of stacks returned
     */
    public static int reclaim() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return 0;

        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.remove(KEY);
        if (!(stored instanceof CargoAPI)) return 0;

        CargoAPI player = Global.getSector().getPlayerFleet().getCargo();

        int returned = 0;

        for (CargoStackAPI stack : ((CargoAPI) stored).getStacksCopy()) {
            SpecialItemData item = stack.getSpecialDataIfSpecial();
            if (item == null) continue;

            player.addItems(CargoAPI.CargoItemType.SPECIAL, item, stack.getSize());
            returned++;
        }

        return returned;
    }
}
