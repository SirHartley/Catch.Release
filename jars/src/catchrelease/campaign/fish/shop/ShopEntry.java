package catchrelease.campaign.fish.shop;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;

public class ShopEntry {

    public enum Kind {

        UPGRADE("Upgrades", "shop_upgrade"),
        TACKLE("Modifiers", "shop_modifiers"),
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

        // ids stay "searchlight" (renaming ids needs a save migration); display follows the rig's new name "lamp"
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

    public boolean isFitted() {
        return kind == Kind.TACKLE && TackleManager.get(rig) == tackle;
    }

    public boolean isOwned() {
        return kind == Kind.TACKLE && TackleManager.isOwned(tackle);
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
        if (isOwned()) return null;

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

        if (getPlayerCredits() < price.credits) return false;

        return price.fish == null || FishCurrency.count(price.fish) >= price.fish.count;
    }

    protected static float getPlayerCredits() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return 0f;

        return Global.getSector().getPlayerFleet().getCargo().getCredits().get();
    }

    public boolean isDone() {
        return isMaxed() || isFitted();
    }

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
        Global.getSector().getCampaignUI().getMessageDisplay().addMessage(getBoughtMessage());

        return true;
    }

    protected String getBoughtMessage() {
        if (isCurio()) return (ware.isOn() ? "Switched on " : "Switched off ") + getName();
        if (isUpgrade()) return "Unlocked " + getName();

        return "Fitted " + getName();
    }

    public boolean devBuy() {
        if (isDone()) return false;

        grant();

        return true;
    }

    public void grant() {
        ShopMarks.unmark(ShopMarks.getMarkKey(this));
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
        TackleManager.own(tackle);
        TackleManager.fit(rig, tackle);

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
