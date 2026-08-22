package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ShopSchematics {

    public static final String KEY = "$catchrelease_shop_schematics";
    public static final String FRESH_KEY = "$catchrelease_shop_schematics_fresh";

    public static String getKey(Tackle tackle) {
        return tackle == null ? null : "tackle:" + tackle.name();
    }

    public static String getKey(String statId, int targetLevel) {
        return statId == null || targetLevel <= 0
                ? null : "upgrade:" + statId + ":" + targetLevel;
    }

    public static boolean has(Tackle tackle) {
        if (tackle == null) return false;
        if (tackle == Tackle.NONE || TackleManager.isOwned(tackle)) return true;

        return getKnown().contains(getKey(tackle));
    }

    public static void unlock(Tackle tackle) {
        String key = getKey(tackle);
        if (key != null && tackle != Tackle.NONE && getKnown().add(key)) getFresh().add(key);
    }

    public static boolean requires(UpgradeStat stat, int targetLevel) {
        if (stat == null || stat.maxLevel <= 0) return false;

        return targetLevel > 0 && targetLevel <= stat.maxLevel
                && targetLevel >= Math.max(1, stat.maxLevel - 1);
    }

    public static boolean has(UpgradeStat stat, int targetLevel) {
        if (stat == null || targetLevel <= 0) return false;
        if (stat.level >= targetLevel || !requires(stat, targetLevel)) return true;

        return getKnown().contains(getKey(stat.id, targetLevel));
    }

    public static void unlock(String statId, int targetLevel) {
        if (statId == null || Global.getSector() == null) return;

        UpgradeStat stat = UpgradeManager.getInstance().getAll().get(statId);
        String key = getKey(statId, targetLevel);
        if (key != null && requires(stat, targetLevel) && getKnown().add(key)) {
            getFresh().add(key);
        }
    }

    public static void unlockAll() {
        if (Global.getSector() == null) return;

        for (Tackle tackle : Tackle.values()) {
            if (tackle.stocked) unlock(tackle);
        }

        for (UpgradeStat stat : UpgradeManager.getInstance().getAll().values()) {
            if (stat == null || stat.id == null || stat.id.equalsIgnoreCase("example")) continue;

            for (int targetLevel = 1; targetLevel <= stat.maxLevel; targetLevel++) {
                unlock(stat.id, targetLevel);
            }
        }

        // a dev grant of everything is setup, not news
        clearAllFresh();
    }

    public static boolean isFresh(ShopEntry entry) {
        if (entry == null) return false;

        String key = entry.isUpgrade()
                ? getKey(entry.stat.id, entry.getLevel() + 1)
                : entry.kind == ShopEntry.Kind.TACKLE ? getKey(entry.tackle) : null;

        return key != null && getFresh().contains(key);
    }

    public static void clearFresh(ShopEntry entry) {
        if (entry == null) return;

        if (entry.isUpgrade()) {
            getFresh().remove(getKey(entry.stat.id, entry.getLevel() + 1));
        } else if (entry.kind == ShopEntry.Kind.TACKLE) {
            getFresh().remove(getKey(entry.tackle));
        }
    }

    public static void clearAllFresh() {
        getFresh().clear();
    }

    public static int getNextRequiredLevel(UpgradeStat stat) {
        if (stat == null) return -1;

        int target = Math.max(0, stat.level) + 1;

        return requires(stat, target) ? target : -1;
    }

    @SuppressWarnings("unchecked")
    protected static Set<String> getFresh() {
        if (Global.getSector() == null) return new LinkedHashSet<>();

        Map<String, Object> data = Global.getSector().getPersistentData();
        Object stored = data.get(FRESH_KEY);
        if (stored instanceof Set) return (Set<String>) stored;

        Set<String> fresh = new LinkedHashSet<>();
        data.put(FRESH_KEY, fresh);

        return fresh;
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
