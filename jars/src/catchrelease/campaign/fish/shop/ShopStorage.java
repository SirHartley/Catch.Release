package catchrelease.campaign.fish.shop;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.Map;

public class ShopStorage {
    public static final String KEY = "$catchrelease_shop_storage";

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
