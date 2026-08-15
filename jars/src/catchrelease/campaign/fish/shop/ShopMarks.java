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

/**
 * The player's shopping list: purchases - exact upgrade rungs and tackle both - marked in the
 * outfitter as the thing being saved for, via the ring on their list rows. Upgrade keys include
 * the target level, so a schematic reward and its eventual fish price can point at the same rung.
 * <p>
 * What a mark does lives elsewhere and reads through the questions here, and there are two of
 * them, asked by different screens for different reasons. {@link #isMarked} is the shopping list
 * alone - the outfitter asks it, because a ring on a shop row means "this is the ware I picked".
 * {@link #isWanted} is the shopping list <i>and</i> every open errand, which is what the
 * quest-yellow dot means everywhere it is drawn: something is asking for this, do not sell it.
 * <p>
 * Every screen outside the outfitter wants the second one. They did not all get it - the hold
 * asked {@code isWanted} while the sector map, its sidebar and the route planner asked
 * {@code isMarked}, so a specimen an errand had sent the player after wore a dot in the cargo bay
 * and none at all on the chart that was supposed to help find it. If a new screen has to choose,
 * the question is whether it is the shop; if not, it is {@link #isWanted}.
 * <p>
 * {@link #drawDot} draws it where the caller puts it: bottom right on cargo icons, on the ring's
 * lower right for the map holder, at the row's right end on the map pane and route popup.
 */
public class ShopMarks {

    public static final String KEY = "$catchrelease_shop_marks";

    /** The dot itself, relative to the icon it stands on. */
    public static final float DOT_RADIUS = 3.5f;

    /** How far the dot's centre sits in from the cargo cell's corner - the one number all
     *  three cargo icon plugins place it by. */
    public static final float DOT_INSET = 8f;

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

    /** Exact shopping-list identity; the shelf identity deliberately remains ladder-wide. */
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

    /** Old saves marked whole ladders; preserve their intent by pinning the then-current rung. */
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
        //marking is engaging with the ware, which is all the New! tag was waiting for
        ShopSchematics.clearFresh(entry);

        toggle(getMarkKey(entry));
    }

    /** Whether an entry can carry a mark at all: something left to buy, and fish in its price. */
    public static boolean isMarkable(ShopEntry entry) {
        if (entry == null) return false;
        if (entry.isPurchaseLocked() && !entry.isUpgrade()) return false;

        ShopPricing.Price price = entry.getPrice();

        return price != null && price.fish != null;
    }

    /**
     * The marked wares' current asks - upgrades and tackle both - keys resolved against the
     * live sheet each time, so a bought rung is priced as its next and a finished, owned, or
     * vanished ware contributes nothing.
     */
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
                    continue; //a mark from a version that no longer makes this module
                }

                if (TackleManager.isOwned(tackle)) continue;
                if (!ShopSchematics.has(tackle)) continue;

                price = ShopPricing.getPrice(tackle);
            }

            if (price != null && price.fish != null) out.add(price.fish);
        }

        return out;
    }

    /** Whether this specimen would go towards something marked - the inventory's question. */
    public static boolean isMarked(FishCatch entry) {
        if (entry == null) return false;

        for (FishRequirement ask : getMarkedRequirements()) {
            if (ask.matches(entry)) return true;
        }

        return false;
    }

    /** Whether this species could go towards something marked - every other screen's question. */
    public static boolean isMarked(FishSpec spec) {
        if (spec == null) return false;

        for (FishRequirement ask : getMarkedRequirements()) {
            if (ask.couldBeSatisfiedBy(spec)) return true;
        }

        return false;
    }

    /** How long the wanted-ask cache is trusted before {@link #getWantedAsks} rebuilds it. */
    protected static final long WANTED_CACHE_MS = 250L;

    protected static final catchrelease.helper.cache.TimedValue<List<Ask>> wantedAsks =
            new catchrelease.helper.cache.TimedValue<>(WANTED_CACHE_MS);

    protected static void invalidateWantedCache() {
        wantedAsks.invalidate();
    }

    /**
     * Every current ask - marked wares and everything in the log that is waiting on a fish - with
     * the name the cargo tooltip gives as its reason. Cached for
     * {@link #WANTED_CACHE_MS}: {@link #isWanted} is asked per cargo cell per frame, and
     * collecting the asks walks the whole intel manager, which a full hold would otherwise do
     * dozens of times a frame. The dot and its explanation deliberately consume this same list,
     * so one can never outlive the other during the cache window.
     */
    protected static List<Ask> getWantedAsks() {
        return wantedAsks.get(System.currentTimeMillis(), () -> {
            List<Ask> out = new ArrayList<>(getMarkedAsks());

            if (Global.getSector() != null) {
                for (com.fs.starfarer.api.campaign.comm.IntelInfoPlugin intel
                        : Global.getSector().getIntelManager().getIntel()) {

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

    /** Whether anything at all - a marked ware or an open job - would take this specimen. */
    public static boolean isWanted(FishCatch entry) {
        if (entry == null) return false;

        for (Ask ask : getWantedAsks()) {
            if (ask.requirement.matches(entry)) return true;
        }

        return false;
    }

    /** Whether anything at all - a marked ware or an open job - asks for this species. */
    public static boolean isWanted(FishSpec spec) {
        if (spec == null) return false;

        for (Ask ask : getWantedAsks()) {
            if (ask.requirement.couldBeSatisfiedBy(spec)) return true;
        }

        return false;
    }

    /** One marked ware with the name it goes by, for saying who is asking. */
    public static class Ask {
        public final String name;
        public final FishRequirement requirement;

        public Ask(String name, FishRequirement requirement) {
            this.name = name;
            this.requirement = requirement;
        }
    }

    /** The marked asks with their wares' names attached, same resolution rules as the plain list. */
    public static List<Ask> getMarkedAsks() {
        List<Ask> out = new ArrayList<>();

        for (String key : getMarkedKeys()) {
            if (key.startsWith("stat:")) {
                UpgradeMark mark = parseUpgradeMark(key);
                if (mark == null || mark.targetLevel != mark.stat.level + 1) continue;
                if (!ShopSchematics.has(mark.stat, mark.targetLevel)) continue;

                ShopPricing.Price price = ShopPricing.getPrice(mark.stat);
                if (price != null && price.fish != null) {
                    out.add(new Ask(ShopEntry.of(mark.stat).getName() + " level "
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

    protected static class UpgradeMark {
        final UpgradeStat stat;
        final int targetLevel;

        UpgradeMark(UpgradeStat stat, int targetLevel) {
            this.stat = stat;
            this.targetLevel = targetLevel;
        }
    }

    /**
     * Everything asking for this species, by name: marked wares off the shopping list, and every
     * open job whose ask a specimen of it could pay. What the tooltips' "Required by" line says.
     */
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

    /** The same roll call for one particular specimen, tested against the actual asks. */
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

    /** The quest-yellow dot a needed fish wears - bottom right on cargo icons, placed to fit the
     *  layout on the map screens. */
    public static void drawDot(float centerX, float centerY, float radius, float alphaMult) {
        //a dark seat under it, so the yellow reads on any icon
        Disc.draw(centerX, centerY, radius + 1.2f, java.awt.Color.BLACK,
                0.85f * alphaMult, 0.85f * alphaMult, false);
        Disc.draw(centerX, centerY, radius, Misc.getHighlightColor(),
                0.95f * alphaMult, 0.95f * alphaMult, false);
    }
}
