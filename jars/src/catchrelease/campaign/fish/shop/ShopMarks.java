package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ShopMarks {
    public static final String KEY = "$catchrelease_shop_marks";
    public static final float DOT_RADIUS = 3.5f;
    public static final float DOT_INSET = 8f;
    protected static final long WANTED_CACHE_MS = 250L;
    protected static final catchrelease.helper.cache.TimedValue<List<Ask>> wantedAsks =
            new catchrelease.helper.cache.TimedValue<>(WANTED_CACHE_MS);

    public static class Ask {
        public final String name;
        public final FishRequirement requirement;

        public Ask(String name, FishRequirement requirement) {
            this.name = name;
            this.requirement = requirement;
        }
    }

    protected static class UpgradeMark {
        final UpgradeStat stat;
        final int targetLevel;

        UpgradeMark(UpgradeStat stat, int targetLevel) {
            this.stat = stat;
            this.targetLevel = targetLevel;
        }
    }

    @SuppressWarnings("unchecked")
    public static Set<String> getMarkedKeys() {
        if (Global.getSector() == null) return new LinkedHashSet<>();

        Object stored = Global.getSector().getPersistentData().get(KEY);

        if (!(stored instanceof Set)) {
            Set<String> fresh = new LinkedHashSet<>();
            Global.getSector().getPersistentData().put(KEY, fresh);
            return fresh;
        }

        Set<String> marked = (Set<String>) stored;
        migrateLegacyUpgradeKeys(marked);

        return marked;
    }

    public static String getMarkKey(ShopEntry entry) {
        if (entry == null) return null;

        return entry.isUpgrade()
                ? getUpgradeMarkKey(entry.stat.id, entry.getLevel() + 1)
                : entry.getKey();
    }

    public static String getUpgradeMarkKey(String statId, int targetLevel) {
        return statId == null || targetLevel <= 0
                ? null : "stat:" + statId + ":" + targetLevel;
    }

    protected static void migrateLegacyUpgradeKeys(Set<String> marked) {
        if (marked == null || marked.isEmpty() || UpgradeManager.getInstance() == null) return;

        boolean changed = false;

        for (String key : new ArrayList<>(marked)) {
            if (key == null || !key.startsWith("stat:")) continue;

            String[] parts = key.split(":", 3);
            if (parts.length != 2) continue;

            UpgradeStat stat = UpgradeManager.getInstance().getAll().get(parts[1]);
            if (stat == null) continue;

            marked.remove(key);
            if (!ShopPricing.isMaxed(stat)) {
                marked.add(getUpgradeMarkKey(stat.id, Math.max(0, stat.level) + 1));
            }
            changed = true;
        }

        if (changed) invalidateWantedCache();
    }

    public static boolean isMarked(String entryKey) {
        return entryKey != null && getMarkedKeys().contains(entryKey);
    }

    public static boolean isMarked(ShopEntry entry) {
        return isMarked(getMarkKey(entry));
    }

    public static boolean isMarkedUpgrade(String statId, int targetLevel) {
        return isMarked(getUpgradeMarkKey(statId, targetLevel));
    }

    public static boolean mark(String entryKey) {
        if (entryKey == null) return false;

        boolean changed = getMarkedKeys().add(entryKey);
        if (changed) invalidateWantedCache();
        return changed;
    }

    public static boolean unmark(String entryKey) {
        if (entryKey == null) return false;

        boolean changed = getMarkedKeys().remove(entryKey);
        if (changed) invalidateWantedCache();
        return changed;
    }

    public static void toggle(String entryKey) {
        if (entryKey == null) return;

        if (!unmark(entryKey)) mark(entryKey);
    }

    public static void toggle(ShopEntry entry) {
        ShopSchematics.clearFresh(entry);

        toggle(getMarkKey(entry));
    }

    public static boolean isMarkable(ShopEntry entry) {
        if (entry == null) return false;
        if (entry.isPurchaseLocked() && !entry.isUpgrade()) return false;

        ShopPricing.Price price = entry.getPrice();

        return price != null && price.fish != null;
    }

    public static List<FishRequirement> getMarkedRequirements() {
        List<FishRequirement> out = new ArrayList<>();

        for (String key : getMarkedKeys()) {
            ShopPricing.Price price = null;

            if (key.startsWith("stat:")) {
                UpgradeMark mark = parseUpgradeMark(key);
                if (mark == null || mark.targetLevel != mark.stat.level + 1) continue;
                if (!ShopSchematics.has(mark.stat, mark.targetLevel)) continue;

                price = ShopPricing.getPrice(mark.stat);
            } else if (key.startsWith("tackle:")) {
                String[] parts = key.split(":", 3);
                if (parts.length < 3) continue;

                Tackle tackle;
                try {
                    tackle = Tackle.valueOf(parts[2]);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                if (TackleManager.isOwned(tackle)) continue;
                if (!ShopSchematics.has(tackle)) continue;

                price = ShopPricing.getPrice(tackle);
            }

            if (price != null && price.fish != null) out.add(price.fish);
        }

        return out;
    }

    public static boolean isMarked(FishCatch entry) {
        if (entry == null) return false;

        for (FishRequirement ask : getMarkedRequirements()) {
            if (ask.matches(entry)) return true;
        }

        return false;
    }

    public static boolean isMarked(FishSpec spec) {
        if (spec == null) return false;

        for (FishRequirement ask : getMarkedRequirements()) {
            if (ask.couldBeSatisfiedBy(spec)) return true;
        }

        return false;
    }

    protected static void invalidateWantedCache() {
        wantedAsks.invalidate();
    }

    protected static List<Ask> getWantedAsks() {
        return wantedAsks.get(System.currentTimeMillis(), () -> {
            List<Ask> out = new ArrayList<>(getMarkedAsks());

            if (Global.getSector() != null) {
                for (com.fs.starfarer.api.campaign.comm.IntelInfoPlugin intel
                        : Global.getSector().getIntelManager().getIntel()) {
                    if (intel.isEnding() || intel.isEnded()) continue;
                    if (!(intel instanceof FishAsker asker)) continue;
                    String name = asker.getAskerName();
                    if (name == null || name.isEmpty()) continue;

                    for (FishRequirement requirement : asker.getAsks()) {
                        if (requirement != null) out.add(new Ask(name, requirement));
                    }
                }
            }

            return out;
        });
    }

    public static boolean isWanted(FishCatch entry) {
        if (entry == null) return false;

        for (Ask ask : getWantedAsks()) {
            if (ask.requirement.matches(entry)) return true;
        }

        return false;
    }

    public static boolean isWanted(FishSpec spec) {
        if (spec == null) return false;

        for (Ask ask : getWantedAsks()) {
            if (ask.requirement.couldBeSatisfiedBy(spec)) return true;
        }

        return false;
    }

    public static List<Ask> getMarkedAsks() {
        List<Ask> out = new ArrayList<>();

        for (String key : getMarkedKeys()) {
            if (key.startsWith("stat:")) {
                UpgradeMark mark = parseUpgradeMark(key);
                if (mark == null || mark.targetLevel != mark.stat.level + 1) continue;
                if (!ShopSchematics.has(mark.stat, mark.targetLevel)) continue;

                ShopPricing.Price price = ShopPricing.getPrice(mark.stat);
                if (price != null && price.fish != null) {
                    out.add(new Ask(ShopEntry.of(mark.stat).getName() + " tier "
                            + mark.targetLevel, price.fish));
                }
            } else if (key.startsWith("tackle:")) {
                String[] parts = key.split(":", 3);
                if (parts.length < 3) continue;

                Tackle tackle;
                try {
                    tackle = Tackle.valueOf(parts[2]);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                if (TackleManager.isOwned(tackle)) continue;
                if (!ShopSchematics.has(tackle)) continue;

                ShopPricing.Price price = ShopPricing.getPrice(tackle);
                if (price != null && price.fish != null) {
                    out.add(new Ask(tackle.name, price.fish));
                }
            }
        }

        return out;
    }

    protected static UpgradeMark parseUpgradeMark(String key) {
        if (key == null || UpgradeManager.getInstance() == null) return null;

        String[] parts = key.split(":", 3);
        if (parts.length != 3 || !"stat".equals(parts[0])) return null;

        UpgradeStat stat = UpgradeManager.getInstance().getAll().get(parts[1]);
        if (stat == null) return null;

        try {
            int targetLevel = Integer.parseInt(parts[2]);
            if (targetLevel <= 0 || targetLevel > stat.maxLevel) return null;

            return new UpgradeMark(stat, targetLevel);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<String> getRequiredBy(FishSpec spec) {
        List<String> out = new ArrayList<>();
        if (spec == null) return out;

        for (Ask ask : getWantedAsks()) {
            if (ask.requirement.couldBeSatisfiedBy(spec) && !out.contains(ask.name)) {
                out.add(ask.name);
            }
        }

        return out;
    }

    public static List<String> getRequiredBy(FishCatch entry) {
        List<String> out = new ArrayList<>();
        if (entry == null) return out;

        for (Ask ask : getWantedAsks()) {
            if (ask.requirement.matches(entry) && !out.contains(ask.name)) {
                out.add(ask.name);
            }
        }

        return out;
    }

    public static void drawDot(float centerX, float centerY, float radius, float alphaMult) {
        Disc.draw(centerX, centerY, radius + 1.2f, java.awt.Color.BLACK,
                0.85f * alphaMult, 0.85f * alphaMult, false);
        Disc.draw(centerX, centerY, radius, Misc.getHighlightColor(),
                0.95f * alphaMult, 0.95f * alphaMult, false);
    }
}
