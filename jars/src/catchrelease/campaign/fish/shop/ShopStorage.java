package catchrelease.campaign.fish.shop;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.Map;

/**
 * The shop's back room, which no longer exists, and the one thing still owed to it.
 * <p>
 * There used to be a counter for stowing a catch with the shop, taking it back and selling it. The
 * counter is gone. What could not go with it is anybody's fish: a save made while it existed can be
 * holding specimens in here, and removing the button that took them out would have left them
 * unreachable forever rather than merely inconvenient.
 * <p>
 * So the room is emptied into the player's hold the next time the shop is opened, and the key is
 * dropped. It runs once per save and costs a map lookup after that. Removable when no save can
 * predate the counter going away, which is not a date anybody can name.
 */
public class ShopStorage {

    public static final String KEY = "$catchrelease_shop_storage";

    /**
     * Hands back anything the old counter is still holding.
     * <p>
     * Straight into the hold rather than through a picker, and without asking about capacity. An
     * overfull hold is a problem the player can see and solve; a fish behind a button that is no
     * longer there is one they cannot.
     *
     * @return how many stacks came back, for a caller that wants to mention it
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
