package catchrelease.campaign.fish.items;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.SubmarketUpdateListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;

import java.util.ArrayList;
import java.util.List;

// Vanilla restocks never remove special items, so sold fish would otherwise stay in a submarket forever.
public class SoldFishRestock implements SubmarketUpdateListener {

    private static final String PURGED_AT_KEY = "$catchrelease_soldFishPurgedAt_";

    public static void register() {
        Global.getSector().getListenerManager().removeListenerOfClass(SoldFishRestock.class);
        Global.getSector().getListenerManager().addListener(new SoldFishRestock(), true);
    }

    @Override
    public void reportSubmarketCargoAndShipsUpdated(SubmarketAPI submarket) {
        if (submarket == null || submarket.getMarket() == null) return;

        SubmarketPlugin plugin = submarket.getPlugin();
        if (plugin == null || plugin.isFreeTransfer()) return;

        // The engine reports right after updateCargoPrePlayerInteraction(), so 0 means this update restocked.
        if (!(plugin instanceof BaseSubmarketPlugin base) || base.getSinceSWUpdate() != 0f) return;

        // sinceSWUpdate stays 0 until time passes; reopening the market while paused must not purge again.
        MarketAPI market = submarket.getMarket();
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String key = PURGED_AT_KEY + submarket.getSpecId();
        long now = Global.getSector().getClock().getTimestamp();
        Object purgedAt = memory.get(key);
        if (purgedAt instanceof Long && (Long) purgedAt == now) return;
        memory.set(key, now);

        CargoAPI cargo = submarket.getCargo();
        if (cargo == null) return;

        List<CargoStackAPI> sold = new ArrayList<>();
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (FishItems.isCatch(stack.getSpecialDataIfSpecial())) sold.add(stack);
        }
        for (CargoStackAPI stack : sold) {
            cargo.removeStack(stack);
        }
    }
}
