package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import com.fs.starfarer.api.Global;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Purchase permissions earned from fishing jobs. A schematic is knowledge, not cargo: receiving
 * one adds a stable string key to the campaign, and the outfitter reads that key before selling
 * the corresponding hardware. Ownership implies knowledge for migrated saves and for equipment
 * obtained through a route outside the outfitter.
 */
public class ShopSchematics {

    public static final String KEY = "$catchrelease_shop_schematics";

    public static String getKey(Tackle tackle) {
        return tackle == null ? null : "tackle:" + tackle.name();
    }

    /** Empty slots and already-owned modules never need a plan shown to the player again. */
    public static boolean has(Tackle tackle) {
        if (tackle == null) return false;
        if (tackle == Tackle.NONE || TackleManager.isOwned(tackle)) return true;

        return getKnown().contains(getKey(tackle));
    }

    public static void unlock(Tackle tackle) {
        String key = getKey(tackle);
        if (key != null && tackle != Tackle.NONE) getKnown().add(key);
    }

    @SuppressWarnings("unchecked")
    protected static Set<String> getKnown() {
        if (Global.getSector() == null) return new LinkedHashSet<>();

        Map<String, Object> data = Global.getSector().getPersistentData();
        Object stored = data.get(KEY);
        if (stored instanceof Set) return (Set<String>) stored;

        Set<String> known = new LinkedHashSet<>();
        data.put(KEY, known);

        return known;
    }
}
