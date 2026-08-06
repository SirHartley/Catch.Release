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
 * The player's shopping list: wares - upgrades and tackle both - marked in the outfitter as
 * the thing being saved for, via the ring on their list rows. A mark follows the ware, not a
 * rung - the asks it stands for are always the current price, so buying a rung moves the mark
 * to the next one, and a finished or owned ware's mark expires on its own.
 * <p>
 * What a mark does lives elsewhere and reads through the two questions here: the route planner
 * suggests every species that could satisfy a marked ask, and every screen that shows a fish
 * asks {@link #isMarked} to know whether to hang the quest-yellow dot on it -
 * {@link #drawDot}, bottom right, the same corner everywhere.
 */
public class ShopMarks {

    public static final String KEY = "$catchrelease_shop_marks";

    /** The dot itself, relative to the icon it stands on. */
    public static final float DOT_RADIUS = 3.5f;

    @SuppressWarnings("unchecked")
    public static Set<String> getMarkedKeys() {
        if (Global.getSector() == null) return new LinkedHashSet<>();

        Object stored = Global.getSector().getPersistentData().get(KEY);

        if (!(stored instanceof Set)) {
            Set<String> fresh = new LinkedHashSet<>();
            Global.getSector().getPersistentData().put(KEY, fresh);
            return fresh;
        }

        return (Set<String>) stored;
    }

    public static boolean isMarked(String entryKey) {
        return entryKey != null && getMarkedKeys().contains(entryKey);
    }

    public static void toggle(String entryKey) {
        if (entryKey == null) return;

        Set<String> marked = getMarkedKeys();
        if (!marked.remove(entryKey)) marked.add(entryKey);
    }

    /** Whether an entry can carry a mark at all: something left to buy, and fish in its price. */
    public static boolean isMarkable(ShopEntry entry) {
        if (entry == null) return false;

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
                UpgradeStat stat = UpgradeManager.getInstance().getAll().get(key.substring(5));
                if (stat == null || ShopPricing.isMaxed(stat)) continue;

                price = ShopPricing.getPrice(stat);
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

    /** The quest-yellow dot, worn bottom right wherever a needed fish is shown. */
    public static void drawDot(float centerX, float centerY, float radius, float alphaMult) {
        //a dark seat under it, so the yellow reads on any icon
        Disc.draw(centerX, centerY, radius + 1.2f, java.awt.Color.BLACK,
                0.85f * alphaMult, 0.85f * alphaMult, false);
        Disc.draw(centerX, centerY, radius, Misc.getHighlightColor(),
                0.95f * alphaMult, 0.95f * alphaMult, false);
    }
}
