package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.AbilityPlugin;
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
        return new ShopEntry(Kind.TACKLE, ShopGroup.forRig(rig), null, tackle, rig);
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

        //the ids still say searchlight, because ids live in saves and renaming those is a
        //migration; the rig was renamed to breach lamps, and the display follows without them
        String id = stat.id.startsWith("searchlight")
                ? stat.id.replaceFirst("^searchlight", "lamp") : stat.id;

        return Misc.ucFirst(id.replace('_', ' '));
    }

    /**
     * The name as the list says it, with the gear prefix cut off - the shelf already says which
     * gear this is, and "Searchlight area" under a searchlight tab is the word searchlight three
     * times. The detail pane keeps the full name, since it stands alone over there.
     */
    public String getListName() {
        if (kind == Kind.TACKLE) return tackle.name;

        String id = stat.id;
        for (String prefix : new String[]{"searchlight_", "fishing_drone_", "drone_",
                "harpoon_", "bomb_", "fishing_", "minigame_"}) {
            if (id.startsWith(prefix) && id.length() > prefix.length()) {
                id = id.substring(prefix.length());
                break;
            }
        }

        return Misc.ucFirst(id.replace('_', ' '));
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

    /** Whether this module is already the player's, so moving it into a slot costs nothing. */
    public boolean isOwned() {
        return kind == Kind.TACKLE && TackleManager.isOwned(tackle);
    }

    /**
     * The next purchase's price. Null when it is free or there is nothing left to buy.
     * <p>
     * A module already owned is free, and that is the whole of the difference between buying one and
     * putting one back on. The shop used to price the slot rather than the module, so a player who
     * swapped to something else and changed their mind paid for the first one twice.
     */
    public ShopPricing.Price getPrice() {
        if (isOwned()) return null;

        return isUpgrade() ? ShopPricing.getPrice(stat) : ShopPricing.getPrice(tackle);
    }

    /** The colour the ask wears in the UI. Null when the catch half has no rarity to speak of. */
    public FishRarity getPriceRarity() {
        ShopPricing.Price price = getPrice();

        return price == null || price.fish == null ? null : price.fish.getDisplayRarity();
    }

    public boolean canAfford() {
        ShopPricing.Price price = getPrice();
        if (price == null) return true;

        if (getPlayerCredits() < price.credits) return false;

        return price.fish == null || FishCurrency.count(price.fish) >= price.fish.count;
    }

    protected static float getPlayerCredits() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return 0f;

        return Global.getSector().getPlayerFleet().getCargo().getCredits().get();
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
        if (isDone() || !canAfford()) return false;

        ShopPricing.Price price = getPrice();
        if (price != null) {
            if (price.fish != null && !FishCurrency.spend(price.fish)) return false;
            if (price.credits > 0) {
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(price.credits);
            }
        }

        grant();

        return true;
    }

    /**
     * Dev mode's buy: the grant without the till. Skips the price and the affordability check
     * entirely, but not {@link #isDone()} - a maxed ladder or a fitted tackle has nothing left to
     * hand over, dev mode or not.
     */
    public boolean devBuy() {
        if (isDone()) return false;

        grant();

        return true;
    }

    /**
     * Hands the thing over, and stops whatever it changes.
     * <p>
     * An ability reads its numbers when it starts and keeps them - a running rig rebuilt from the
     * sheet mid-sweep would be lamps changing size in the middle of a pass. So the ability is turned
     * off rather than reconfigured, which is both cheaper and more honest: the player sees it stop,
     * and what comes back on is built from what they just bought.
     */
    protected void grant() {
        if (isUpgrade()) {
            UpgradeManager.getInstance().addLevels(stat.id, 1);
            stopAbility(StatIds.getAbilityId(stat.id));

            return;
        }

        //owned before fitted, since owning it is what was paid for - the slot is only where it is
        //being kept, and it can be moved out and back again for nothing from here on
        TackleManager.own(tackle);
        TackleManager.fit(rig, tackle);

        //a module is read at the same moment for the same reason, so a rig wearing a new one has to
        //come back up as well
        stopAbility(getRigAbilityId());
    }

    /** Which ability this entry's rig is, or null for a rig with no ability of its own. */
    protected String getRigAbilityId() {
        if (rig == null) return null;

        switch (rig) {
            case SEARCHLIGHT: return StatIds.LAMPS_ABILITY;
            case DRONE: return StatIds.ROD_ABILITY;
            case HARPOON: return StatIds.HARPOON_ABILITY;
            default: return null;
        }
    }

    /** Turns an ability off if it is running, and says nothing if it is not. */
    protected static void stopAbility(String abilityId) {
        if (abilityId == null) return;
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return;

        AbilityPlugin ability = Global.getSector().getPlayerFleet().getAbility(abilityId);
        if (ability == null || !ability.isActiveOrInProgress()) return;

        ability.deactivate();
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
