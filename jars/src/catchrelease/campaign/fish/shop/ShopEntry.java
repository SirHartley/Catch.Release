package catchrelease.campaign.fish.shop;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.tackle.RigLoadout;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.function.BooleanSupplier;

public class ShopEntry {

    public enum Kind {

        UPGRADE("Upgrades", "shop_upgrade"),
        TACKLE("Equipment", "shop_modifiers"),
        CURIO("Extras", "pane_misc");

        public final String tabTitle;

        public final String iconId;

        Kind(String tabTitle, String iconId) {
            this.tabTitle = tabTitle;
            this.iconId = iconId;
        }
    }

    public final Kind kind;
    public final ShopGroup group;
    public final UpgradeStat stat;
    public final Tackle tackle;
    public final Tackle.Fit rig;
    public final CrabWares ware;

    protected ShopEntry(Kind kind, ShopGroup group, UpgradeStat stat, Tackle tackle, Tackle.Fit rig,
                        CrabWares ware) {
        this.kind = kind;
        this.group = group;
        this.stat = stat;
        this.tackle = tackle;
        this.rig = rig;
        this.ware = ware;
    }

    public static ShopEntry of(UpgradeStat stat) {
        return new ShopEntry(Kind.UPGRADE, ShopGroup.forStat(stat), stat, null, null, null);
    }

    public static ShopEntry of(Tackle tackle, Tackle.Fit rig) {
        return new ShopEntry(Kind.TACKLE, ShopGroup.forRig(rig), null, tackle, rig, null);
    }

    public static ShopEntry of(Tackle tackle) {
        Tackle.Fit rig = tackle == null ? null : tackle.fit;

        if (rig == Tackle.Fit.BOTH) {
            rig = Tackle.Fit.DRONE;

            if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null
                    && !Global.getSector().getPlayerFleet().hasAbility(StatIds.ROD_ABILITY)
                    && Global.getSector().getPlayerFleet().hasAbility(StatIds.HARPOON_ABILITY)) {
                rig = Tackle.Fit.HARPOON;
            }
        }

        return of(tackle, rig);
    }

    public static ShopEntry of(CrabWares ware) {
        return new ShopEntry(Kind.CURIO, ShopGroup.forWare(ware), null, null, null, ware);
    }

    public String getName() {
        if (kind == Kind.CURIO) return ware.name;
        if (kind == Kind.TACKLE) return tackle.name;

        String id = stat.id.startsWith("searchlight")
                ? stat.id.replaceFirst("^searchlight", "lamp") : stat.id;

        return Misc.ucFirst(id.replace('_', ' '));
    }

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

    public SpriteAPI getIcon() {
        String path = null;

        if (kind == Kind.UPGRADE) path = stat.icon;
        else if (kind == Kind.TACKLE) path = tackle.icon;

        if (path != null && !path.isBlank()) return SpriteLoader.loadSprite(path);
        if (group == null || group.iconId == null || group.iconId.isEmpty()) return null;

        return SpriteLoader.getSprite(group.iconId);
    }

    public String getIconName() {
        String path = null;

        if (kind == Kind.UPGRADE) path = stat.icon;
        else if (kind == Kind.TACKLE) path = tackle.icon;

        if (path != null && !path.isBlank()) return path;
        if (group == null || group.iconId == null || group.iconId.isEmpty()) return null;

        return Global.getSettings().getSpriteName(ModPlugin.MOD_ID, group.iconId);
    }

    public boolean isCurio() {
        return kind == Kind.CURIO;
    }

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

    // For the None entry, "fitted" means that slot is empty.
    public boolean isFittedIn(int slot) {
        return kind == Kind.TACKLE && TackleManager.getSlot(rig, slot) == tackle;
    }

    // Fitted to the rig in any slot; the None entry counts only the first slot, which every rig has.
    public boolean isFitted() {
        if (kind != Kind.TACKLE) return false;
        if (tackle == Tackle.NONE) return isFittedIn(0);

        return getFittedSlot() >= 0;
    }

    public int getFittedSlot() {
        if (kind != Kind.TACKLE || tackle == Tackle.NONE) return -1;

        for (int slot = 0; slot < RigLoadout.SLOTS; slot++) {
            if (isFittedIn(slot)) return slot;
        }

        return -1;
    }

    public boolean canFitIn(int slot) {
        return kind != Kind.TACKLE || TackleManager.canFit(rig, slot, tackle);
    }

    // The module in the rig's other slot that keeps this one out of the slot: itself (already fitted there) or one
    // of its exclusion group. Null when nothing blocks it.
    public Tackle getBlocker(int slot) {
        if (kind != Kind.TACKLE || tackle == Tackle.NONE || !FishingShips.hasSecondSlot(rig)) return null;

        Tackle other = TackleManager.getSlot(rig, 1 - slot);

        return tackle.conflictsWith(other) ? other : null;
    }

    public boolean isOwned() {
        return kind == Kind.TACKLE && TackleManager.isOwned(tackle);
    }

    // Fitted for free while its ship is in the fleet, never bought or owned. An owned copy is an ordinary module.
    public boolean isShipModule() {
        return kind == Kind.TACKLE && !isOwned() && FishingShips.isShipModuleAvailable(tackle);
    }

    public boolean isLocked() {
        if (!isUpgrade()) return false;

        int targetLevel = getLevel() + 1;

        return ShopSchematics.requires(stat, targetLevel)
                && !ShopSchematics.has(stat, targetLevel);
    }

    public boolean isPurchaseLocked() {
        return kind == Kind.TACKLE ? !ShopSchematics.has(tackle) : isLocked();
    }

    public ShopPricing.Price getPrice() {
        if (isOwned() || isShipModule()) return null;

        // a curio was paid for in a bar; the shop is only where the switch on it lives
        if (isCurio()) return null;

        return isUpgrade() ? ShopPricing.getPrice(stat) : ShopPricing.getPrice(tackle);
    }

    public FishRarity getPriceRarity() {
        ShopPricing.Price price = getPrice();

        return price == null || price.fish == null ? null : price.fish.getDisplayRarity();
    }

    public boolean canAfford() {
        if (isPurchaseLocked()) return false;

        ShopPricing.Price price = getPrice();
        if (price == null) return true;
        if (price.fish == null) return false;

        if (getPlayerCredits() < price.credits) return false;

        return FishCurrency.count(price.fish) >= price.fish.count;
    }

    protected static float getPlayerCredits() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return 0f;

        return Global.getSector().getPlayerFleet().getCargo().getCredits().get();
    }

    public boolean isDone() {
        return isMaxed() || isFitted();
    }

    public boolean isDone(int slot) {
        return isMaxed() || isFittedIn(slot);
    }

    public boolean buy() {
        return buy(0, null);
    }

    public boolean buy(int slot) {
        return buy(slot, null);
    }

    public boolean buyWithFishPayment(int slot, BooleanSupplier spendFish) {
        ShopPricing.Price price = getPrice();
        if (spendFish == null || price == null || price.fish == null) return false;

        return buy(slot, spendFish);
    }

    // The slot is checked before anything is spent, so a refused fit never leaves the player paid but not fitted.
    protected boolean buy(int slot, BooleanSupplier spendFish) {
        if (isDone(slot) || isPurchaseLocked() || !canAfford() || !canFitIn(slot)) return false;

        ShopPricing.Price price = getPrice();
        if (price != null) {
            if (price.fish != null) {
                boolean paid = spendFish == null
                        ? FishCurrency.spend(price.fish) : spendFish.getAsBoolean();
                if (!paid) return false;
            }
            if (price.credits > 0) {
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(price.credits);
            }
        }

        grant(slot);
        Global.getSector().getCampaignUI().getMessageDisplay().addMessage(getBoughtMessage());

        return true;
    }

    protected String getBoughtMessage() {
        if (isCurio()) return (ware.isOn() ? "Switched on " : "Switched off ") + getName();
        if (isUpgrade()) return "Unlocked " + getName();

        return "Fitted " + getName();
    }

    public boolean devBuy(int slot) {
        if (isDone(slot) || !canFitIn(slot)) return false;

        grant(slot);

        return true;
    }

    // Grants from outside the Outfitter: the first slot, or the second when a module there keeps it out of the first.
    public void grant() {
        grant(canFitIn(0) || !canFitIn(1) ? 0 : 1);
    }

    public void grant(int slot) {
        ShopMarks.unmark(ShopMarks.getMarkKey(this));
        if (kind == Kind.TACKLE) ShopMarks.unmarkTackle(tackle);
        ShopSchematics.clearFresh(this);

        // a curio is not handed over, it is flipped - it was already bought before the shop saw it
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
        if (!isShipModule()) TackleManager.own(tackle);
        TackleManager.fit(rig, slot, tackle);

        if (tackle == Tackle.NEUTRINO_LENS) {
            FishingIntro.grant(Abilities.GRAVITIC_SCAN, null);
        }

        // same reason - deactivate so the rig picks up the new module
        stopAbility(getRigAbilityId());
    }

    protected String getRigAbilityId() {
        if (rig == null) return null;

        switch (rig) {
            case SEARCHLIGHT: return StatIds.LAMPS_ABILITY;
            case DRONE: return StatIds.ROD_ABILITY;
            case HARPOON: return StatIds.HARPOON_ABILITY;
            default: return null;
        }
    }

    protected static void stopAbility(String abilityId) {
        if (abilityId == null) return;
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return;

        AbilityPlugin ability = Global.getSector().getPlayerFleet().getAbility(abilityId);
        if (ability == null || !ability.isActiveOrInProgress()) return;

        ability.deactivate();
    }

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

    public String getKey() {
        switch (kind) {
            case CURIO: return "ware:" + ware.name();
            case TACKLE: return "tackle:" + rig.name() + ":" + tackle.name();
            default: return "stat:" + stat.id;
        }
    }
}
