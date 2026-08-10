package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
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

    public static String getKey(String statId, int targetLevel) {
        return statId == null || targetLevel <= 0
                ? null : "upgrade:" + statId + ":" + targetLevel;
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

    /** The final two purchases on every upgrade ladder require their own sequential plans. */
    public static boolean requires(UpgradeStat stat, int targetLevel) {
        if (stat == null || stat.maxLevel <= 0) return false;

        return targetLevel > 0 && targetLevel <= stat.maxLevel
                && targetLevel >= Math.max(1, stat.maxLevel - 1);
    }

    /** A rung already bought implies its plan for migrated campaigns. Earlier rungs stay open. */
    public static boolean has(UpgradeStat stat, int targetLevel) {
        if (stat == null || targetLevel <= 0) return false;
        if (stat.level >= targetLevel || !requires(stat, targetLevel)) return true;

        return getKnown().contains(getKey(stat.id, targetLevel));
    }

    public static void unlock(String statId, int targetLevel) {
        if (statId == null || Global.getSector() == null) return;

        UpgradeStat stat = UpgradeManager.getInstance().getAll().get(statId);
        String key = getKey(statId, targetLevel);
        if (key != null && requires(stat, targetLevel)) getKnown().add(key);
    }

    /** The next rung, but only when the player has reached the schematic-gated end of the ladder. */
    public static int getNextRequiredLevel(UpgradeStat stat) {
        if (stat == null) return -1;

        int target = Math.max(0, stat.level) + 1;

        return requires(stat, target) ? target : -1;
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
