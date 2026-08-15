package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.crab.CrabWares;
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
 * One thing on a shelf - an upgrade (a ladder), a tackle (a slot), or a curio (a switch) - wrapped
 * so the list, rows, and detail pane are written once instead of once per kind.
 * <p>
 * Holds no state of its own: level, fit, switch and price are read fresh from what it wraps on every
 * call. A curio is the odd one out in that nothing about it is for sale - it was bought elsewhere,
 * and what the shop offers is the only thing left to do with it, which is turn it off.
 */
public class ShopEntry {

    public enum Kind {
        UPGRADE("Upgrades"),
        TACKLE("Modifiers"),
        CURIO("Extras");

        /** What the main tab row calls this. Held here so a fourth kind is one line, not two. */
        public final String tabTitle;

        Kind(String tabTitle) {
            this.tabTitle = tabTitle;
        }
    }

    public final Kind kind;
    public final ShopGroup group;

    public final UpgradeStat stat;
    public final Tackle tackle;
    public final Tackle.Fit rig;
    public final CrabWares ware;

    public static ShopEntry of(UpgradeStat stat) {
        return new ShopEntry(Kind.UPGRADE, ShopGroup.forStat(stat), stat, null, null, null);
    }

    public static ShopEntry of(Tackle tackle, Tackle.Fit rig) {
        return new ShopEntry(Kind.TACKLE, ShopGroup.forRig(rig), null, tackle, rig, null);
    }

    public static ShopEntry of(CrabWares ware) {
        return new ShopEntry(Kind.CURIO, ShopGroup.forWare(ware), null, null, null, ware);
    }

    protected ShopEntry(Kind kind, ShopGroup group, UpgradeStat stat, Tackle tackle, Tackle.Fit rig,
                        CrabWares ware) {
        this.kind = kind;
        this.group = group;
        this.stat = stat;
        this.tackle = tackle;
        this.rig = rig;
        this.ware = ware;
    }

    public String getName() {
        if (kind == Kind.CURIO) return ware.name;
        if (kind == Kind.TACKLE) return tackle.name;

        // ids stay "searchlight" (renaming ids needs a save migration); display follows the rig's new name "lamp"
        String id = stat.id.startsWith("searchlight")
                ? stat.id.replaceFirst("^searchlight", "lamp") : stat.id;

        return Misc.ucFirst(id.replace('_', ' '));
    }

    /** List name with the gear prefix cut, since the shelf tab already says which gear it is. Detail pane keeps the full name. */
    public String getListName() {
        if (kind == Kind.CURIO) return ware.name;
        if (kind == Kind.TACKLE) return tackle.name;

        String id = stat.id;
        for (String prefix : new String[]{"searchlight_", "fishing_drone_", "drone_",
                "harpoon_", "fishing_", "minigame_"}) {
            if (id.startsWith(prefix) && id.length() > prefix.length()) {
                id = id.substring(prefix.length());
                break;
            }
        }

        return Misc.ucFirst(id.replace('_', ' '));
    }

    public String getDescription() {
        switch (kind) {
            case CURIO: return ware.description;
            case TACKLE: return tackle.description;
            default: return stat.description;
        }
    }

    public boolean isCurio() {
        return kind == Kind.CURIO;
    }

    /** Whether a curio is switched on. Meaningless, and false, for anything else. */
    public boolean isOn() {
        return isCurio() && ware.isOn();
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

    /** Visible upgrade rung whose quest-earned purchase schematic is not known yet. */
    public boolean isLocked() {
        if (!isUpgrade()) return false;

        int targetLevel = getLevel() + 1;

        return ShopSchematics.requires(stat, targetLevel)
                && !ShopSchematics.has(stat, targetLevel);
    }

    /** Purchase guard also covering tackle, which the outfitter hides until this becomes false. */
    public boolean isPurchaseLocked() {
        return kind == Kind.TACKLE ? !ShopSchematics.has(tackle) : isLocked();
    }

    /** Next purchase's price, or null if free / nothing left to buy. An owned module is free to re-fit. */
    public ShopPricing.Price getPrice() {
        if (isOwned()) return null;

        //a curio was paid for in a bar; the shop is only where the switch on it lives
        if (isCurio()) return null;

        return isUpgrade() ? ShopPricing.getPrice(stat) : ShopPricing.getPrice(tackle);
    }

    /** The colour the ask wears in the UI. Null when the catch half has no rarity to speak of. */
    public FishRarity getPriceRarity() {
        ShopPricing.Price price = getPrice();

        return price == null || price.fish == null ? null : price.fish.getDisplayRarity();
    }

    public boolean canAfford() {
        if (isPurchaseLocked()) return false;

        ShopPricing.Price price = getPrice();
        if (price == null) return true;

        if (getPlayerCredits() < price.credits) return false;

        return price.fish == null || FishCurrency.count(price.fish) >= price.fish.count;
    }

    protected static float getPlayerCredits() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return 0f;

        return Global.getSector().getPlayerFleet().getCargo().getCredits().get();
    }

    /**
     * Nothing left to sell here: an upgrade at its ceiling, or a tackle already in its slot. Never a
     * curio - its button is a switch, and a switch is never finished with.
     */
    public boolean isDone() {
        return isMaxed() || isFitted();
    }

    /**
     * Takes the money and hands the thing over.
     *
     * @return false if it could not be paid for, in which case nothing changed
     */
    public boolean buy() {
        if (isDone() || isPurchaseLocked() || !canAfford()) return false;

        ShopPricing.Price price = getPrice();
        if (price != null) {
            if (price.fish != null && !FishCurrency.spend(price.fish)) return false;
            if (price.credits > 0) {
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(price.credits);
            }
        }

        grant();
        Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                "Unlocked " + getName());

        return true;
    }

    /**
     * Dev mode's buy: grant without paying. Skips price/affordability but not {@link #isDone()} -
     * a maxed or fitted entry has nothing to hand over regardless.
     */
    public boolean devBuy() {
        if (isDone()) return false;

        grant();

        return true;
    }

    /**
     * Hands the thing over and stops whatever it changes. Abilities read their numbers once at
     * activation, so a running rig has to be deactivated rather than reconfigured mid-flight.
     * <p>
     * Public because it is the only place that knows that, and not everything sold is sold here -
     * anything granting a module or a rung from outside the shop still has to come through it.
     */
    public void grant() {
        //a curio is not handed over, it is flipped - it was already bought before the shop saw it
        if (isCurio()) {
            ware.setOn(!ware.isOn());

            return;
        }

        if (isUpgrade()) {
            UpgradeManager.getInstance().addLevels(stat.id, 1);
            stopAbility(StatIds.getAbilityId(stat.id));

            return;
        }

        // own before fit - ownership is what was paid for; the slot can be swapped freely afterward
        TackleManager.own(tackle);
        TackleManager.fit(rig, tackle);

        // same reason - deactivate so the rig picks up the new module
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
     * Stat's value at a hypothetical level, for a "now vs next" display - same arithmetic as
     * {@link UpgradeStat#getCurrentValue()}, at a level it isn't at.
     */
    public String getValueAt(int level) {
        if (!isUpgrade()) return "";

        int clamped = Math.max(0, Math.min(level, getMaxLevel()));

        double value = switch (stat.upgradeType) {
            case FLAT -> stat.baseValue + stat.increasePerLevel * clamped;
            case MULT -> stat.baseValue * (1.0 + stat.increasePerLevel * clamped);
        };

        if (stat.baseType == UpgradeStat.BaseType.INT) return String.valueOf(Math.round(value));

        // trim trailing zeros: "8" not "8.00"
        String text = String.format("%.2f", value);
        if (text.contains(".")) text = text.replaceAll("0+$", "").replaceAll("\\.$", "");

        return text;
    }

    /** One string that survives a rebuild, for remembering what was selected. */
    public String getKey() {
        switch (kind) {
            case CURIO: return "ware:" + ware.name();
            case TACKLE: return "tackle:" + rig.name() + ":" + tackle.name();
            default: return "stat:" + stat.id;
        }
    }
}
