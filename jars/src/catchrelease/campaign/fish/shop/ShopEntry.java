package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.util.Misc;

/**
 * One thing the shop sells, whichever kind of thing it is.
 * <p>
 * An upgrade is a ladder and a tackle is a slot, but a shelf does not care: everything on it has a
 * name, a state, a price, and something that happens when it is paid for. Wrapping both here means
 * the list, the rows, and the detail pane are written once rather than once per kind.
 * <p>
 * Holds no state of its own beyond what it wraps - level, fit, and price are read fresh on every
 * ask, so a row on screen is always telling the truth without anyone rebuilding it.
 */
public class ShopEntry {

    public enum Kind {
        UPGRADE,
        TACKLE
    }

    public final Kind kind;
    public final ShopGroup group;

    public final UpgradeStat stat;
    public final Tackle tackle;
    public final Tackle.Fit rig;

    public static ShopEntry of(UpgradeStat stat) {
        return new ShopEntry(Kind.UPGRADE, ShopGroup.forStat(stat), stat, null, null);
    }

    public static ShopEntry of(Tackle tackle, Tackle.Fit rig) {
        ShopGroup group = rig == Tackle.Fit.DRONE ? ShopGroup.DRONE_TACKLE : ShopGroup.HARPOON_TIPS;

        return new ShopEntry(Kind.TACKLE, group, null, tackle, rig);
    }

    protected ShopEntry(Kind kind, ShopGroup group, UpgradeStat stat, Tackle tackle, Tackle.Fit rig) {
        this.kind = kind;
        this.group = group;
        this.stat = stat;
        this.tackle = tackle;
        this.rig = rig;
    }

    public String getName() {
        if (kind == Kind.TACKLE) return tackle.name;

        return Misc.ucFirst(stat.id.replace('_', ' '));
    }

    public String getDescription() {
        return kind == Kind.TACKLE ? tackle.description : stat.description;
    }

    public boolean isUpgrade() {
        return kind == Kind.UPGRADE;
    }

    public int getLevel() {
        return isUpgrade() ? Math.max(0, Math.min(stat.level, getMaxLevel())) : 0;
    }

    public int getMaxLevel() {
        return isUpgrade() ? Math.max(1, stat.maxLevel) : 0;
    }

    public boolean isMaxed() {
        return isUpgrade() && ShopPricing.isMaxed(stat);
    }

    public boolean isFitted() {
        return kind == Kind.TACKLE && TackleManager.get(rig) == tackle;
    }

    /** What the next purchase is paid in. Null when it is free or there is nothing left to buy. */
    public FishRarity getPriceRarity() {
        return isUpgrade() ? ShopPricing.getRarity(stat) : ShopPricing.getRarity(tackle);
    }

    public int getPriceCost() {
        return isUpgrade() ? ShopPricing.getCost(stat) : ShopPricing.getCost(tackle);
    }

    public boolean canAfford() {
        return ShopPricing.canAfford(getPriceRarity(), getPriceCost());
    }

    /** Nothing left to sell here: an upgrade at its ceiling, or a tackle already in its slot. */
    public boolean isDone() {
        return isMaxed() || isFitted();
    }

    /**
     * Takes the money and hands the thing over.
     *
     * @return false if it could not be paid for, in which case nothing changed
     */
    public boolean buy() {
        if (isDone()) return false;
        if (!FishCurrency.spend(getPriceRarity(), getPriceCost())) return false;

        if (isUpgrade()) {
            UpgradeManager.getInstance().addLevels(stat.id, 1);
        } else {
            TackleManager.fit(rig, tackle);
        }

        return true;
    }

    /**
     * The stat's value as it would read at a given level, for the "now against next" line - the
     * same arithmetic {@link UpgradeStat#getCurrentValue()} runs, at a level it is not at.
     */
    public String getValueAt(int level) {
        if (!isUpgrade()) return "";

        int clamped = Math.max(0, Math.min(level, getMaxLevel()));

        double value = switch (stat.upgradeType) {
            case FLAT -> stat.baseValue + stat.increasePerLevel * clamped;
            case MULT -> stat.baseValue * (1.0 + stat.increasePerLevel * clamped);
        };

        if (stat.baseType == UpgradeStat.BaseType.INT) return String.valueOf(Math.round(value));

        //two places, with the noise trimmed off - "8" rather than "8.00", "0.45" as it is
        String text = String.format("%.2f", value);
        if (text.contains(".")) text = text.replaceAll("0+$", "").replaceAll("\\.$", "");

        return text;
    }

    /** One string that survives a rebuild, for remembering what was selected. */
    public String getKey() {
        return isUpgrade() ? "stat:" + stat.id : "tackle:" + rig.name() + ":" + tackle.name();
    }
}
