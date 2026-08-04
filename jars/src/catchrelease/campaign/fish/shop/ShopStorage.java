package catchrelease.campaign.fish.shop;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;

import java.util.Map;

/**
 * The shop's back room: a hold of its own, kept in the save.
 * <p>
 * What is stored here is out of the wallet - the point of stowing a fish with the shop is that it
 * cannot be spent, sold, or counted against a price by accident until it is taken back out.
 */
public class ShopStorage {

    public static final String KEY = "$catchrelease_shop_storage";

    public static CargoAPI get() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(KEY);
        if (stored instanceof CargoAPI) return (CargoAPI) stored;

        CargoAPI cargo = Global.getFactory().createCargo(true);
        data.put(KEY, cargo);

        return cargo;
    }
}
