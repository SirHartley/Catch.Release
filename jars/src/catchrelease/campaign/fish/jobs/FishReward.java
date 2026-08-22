package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.campaign.fish.colony.Backdrops;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.BackdropLoader;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.shop.ShopEntry;
import catchrelease.campaign.fish.shop.ShopGroup;
import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.campaign.fish.shop.ShopPricing;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import catchrelease.ui.FishIcons;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Collections;

public abstract class FishReward {
    public static class Credits extends FishReward {
        public final int amount;

        public Credits(int amount) {
            this.amount = amount;
        }

        @Override
        public String describe() {
            return Misc.getWithDGS(amount) + " credits";
        }

        @Override
        public void grant() {
            CargoAPI cargo = getPlayerCargo();
            if (cargo != null) cargo.getCredits().add(amount);
        }
    }

    public static class Upgrade extends FishReward {
        public final String statId;
        public final int levels;

        public Upgrade(String statId, int levels) {
            this.statId = statId;
            this.levels = levels;
        }

        @Override
        public String describe() {
            UpgradeStat stat = UpgradeManager.getInstance() == null
                    ? null : UpgradeManager.getInstance().getAll().get(statId);

            String name = stat == null ? statId : Misc.ucFirst(statId.replace('_', ' '));

            return levels == 1 ? "an upgrade to " + name : levels + " upgrades to " + name;
        }

        @Override
        public void grant() {
            if (UpgradeManager.getInstance() == null) return;

            UpgradeManager.getInstance().addLevels(statId, levels);
        }
    }

    public static class TackleReward extends FishReward {
        public final Tackle tackle;

        public TackleReward(Tackle tackle) {
            this.tackle = tackle;
        }

        @Override
        public String describe() {
            return "a " + tackle.name;
        }

        @Override
        public void grant() {
            Tackle.Fit rig = tackle.fit == Tackle.Fit.BOTH ? Tackle.Fit.DRONE : tackle.fit;

            // grants ownership, not just a fit - removing it later must not require buying it back
            TackleManager.own(tackle);
            TackleManager.fit(rig, tackle);
        }
    }

    public static class UpgradeSchematic extends FishReward {
        public final String statId;
        public final int targetLevel;

        public UpgradeSchematic(String statId, int targetLevel) {
            this.statId = statId;
            this.targetLevel = targetLevel;
        }

        @Override
        public String describe() {
            UpgradeStat stat = UpgradeManager.getInstance().getAll().get(statId);
            String name = stat == null ? statId : ShopEntry.of(stat).getName();

            return "a schematic for " + name + " tier " + targetLevel;
        }

        @Override
        public void grant() {
            ShopSchematics.unlock(statId, targetLevel);
        }

        @Override
        public boolean addOfferDetails(TooltipMakerAPI tooltip, float pad) {
            UpgradeStat stat = UpgradeManager.getInstance().getAll().get(statId);
            if (tooltip == null || stat == null) return false;

            ShopEntry entry = ShopEntry.of(stat);
            TooltipMakerAPI item = tooltip.beginImageWithText(entry.getIconName(), 48f);
            item.addPara("Fishing Outfitter schematic", Misc.getHighlightColor(), 0f);
            item.addPara("%s — Tier %s", 3f, Misc.getTextColor(), Misc.getHighlightColor(),
                    entry.getName(), String.valueOf(targetLevel));
            item.addPara(stat.description, 6f);
            item.addPara("This tier: %s → %s", 3f, Misc.getGrayColor(),
                    Misc.getPositiveHighlightColor(), entry.getValueAt(targetLevel - 1),
                    entry.getValueAt(targetLevel));
            addSchematicPurchase(item, ShopPricing.getPrice(stat, targetLevel), "upgrade tier");
            UIPanelAPI card = tooltip.addImageWithText(pad);
            CustomPanelAPI mark = Global.getSettings().createCustom(48f, 48f,
                    new SchematicMarkOverlay(statId, targetLevel));
            card.addComponent(mark).inLMid(0f);
            card.bringComponentToTop(mark);

            return true;
        }

        @Override
        public boolean hasOfferDetails() {
            return true;
        }

        @Override
        public String getSchematicKey() {
            return ShopSchematics.getKey(statId, targetLevel);
        }
    }

    protected static class SchematicMarkOverlay extends BaseCustomUIPanelPlugin {
        protected final String statId;
        protected final int targetLevel;
        protected PositionAPI pos;

        protected SchematicMarkOverlay(String statId, int targetLevel) {
            this.statId = statId;
            this.targetLevel = targetLevel;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;
            if (!ShopMarks.isMarkedUpgrade(statId, targetLevel)) return;

            ShopMarks.drawDot(pos.getX() + pos.getWidth() - ShopMarks.DOT_INSET,
                    pos.getY() + ShopMarks.DOT_INSET, ShopMarks.DOT_RADIUS, alphaMult);
        }
    }

    public static class TackleSchematic extends FishReward {
        public final Tackle tackle;

        public TackleSchematic(Tackle tackle) {
            this.tackle = tackle;
        }

        @Override
        public String describe() {
            return "a schematic for the " + tackle.name;
        }

        @Override
        public void grant() {
            ShopSchematics.unlock(tackle);
        }

        @Override
        public boolean addOfferDetails(TooltipMakerAPI tooltip, float pad) {
            if (tooltip == null) return false;

            ShopEntry entry = ShopEntry.of(tackle);
            TooltipMakerAPI item = tooltip.beginImageWithText(entry.getIconName(), 48f);
            item.addPara("Fishing Outfitter schematic", Misc.getHighlightColor(), 0f);
            item.addPara(tackle.name, Misc.getTextColor(), 3f);
            item.addPara("Fits: %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(),
                    describeFit(tackle.fit));
            item.addPara(tackle.description, 6f);
            addSchematicPurchase(item, ShopPricing.getPrice(tackle),
                    ShopGroup.getModuleType(tackle.fit));
            tooltip.addImageWithText(pad);

            return true;
        }

        @Override
        public boolean hasOfferDetails() {
            return true;
        }

        @Override
        public String getSchematicKey() {
            return ShopSchematics.getKey(tackle);
        }
    }

    public static class BackdropReward extends FishReward {
        public final String backdropId;

        public BackdropReward(String backdropId) {
            this.backdropId = backdropId;
        }

        @Override
        public String describe() {
            Backdrop backdrop = BackdropLoader.get(backdropId);

            return backdrop == null ? "a rolled-up backdrop"
                    : "an aquarium backdrop - " + backdrop.getDisplayName();
        }

        @Override
        public void grant() {
            Backdrops.own(backdropId);
        }
    }

    public static class LocationData extends FishReward {
        public final String speciesId;
        public final int fallbackCredits;

        public LocationData(String speciesId, int fallbackCredits) {
            this.speciesId = speciesId;
            this.fallbackCredits = fallbackCredits;
        }

        @Override
        public String describe() {
            if (isRedundant()) return new Credits(getFallbackCredits()).describe();

            String name = FishSpecLoader.getFishSpec(speciesId) == null
                    ? speciesId : FishSpecLoader.getFishSpec(speciesId).getDisplayName();

            return "range data on the " + name;
        }

        @Override
        public boolean addOfferDetails(TooltipMakerAPI tooltip, float pad) {
            FishSpec spec = FishSpecLoader.getFishSpec(speciesId);
            if (tooltip == null || spec == null || isRedundant()) return false;

            TooltipMakerAPI item = tooltip.beginImageWithText(
                    "graphics/catchrelease/icon/blank.png", 48f);
            item.addPara("Range data", Misc.getHighlightColor(), 0f);
            item.addPara(spec.getDisplayName(), spec.rarity.color, 3f);
            item.addPara("Range data unlocks the habitat of the pattern on your map, allowing you"
                    + " to see its range and plot a course to catch it.", 6f);
            UIPanelAPI card = tooltip.addImageWithText(pad);

            CustomPanelAPI silhouette = Global.getSettings().createCustom(48f, 48f,
                    new RangeDataSilhouetteOverlay(spec));
            card.addComponent(silhouette).inLMid(0f);
            card.bringComponentToTop(silhouette);

            return true;
        }

        @Override
        public boolean hasOfferDetails() {
            return !isRedundant();
        }

        @Override
        public void grant() {
            if (isRedundant()) {
                new Credits(getFallbackCredits()).grant();
            } else {
                FishLog.unlockLocationData(speciesId);
            }
        }

        protected boolean isRedundant() {
            return FishLog.isLocationDataUnlocked(speciesId);
        }

        protected int getFallbackCredits() {
            int value = fallbackCredits > 0 ? fallbackCredits : FishRewardRoller.VALUE_PER_FISH;

            return FishRewardRoller.creditPayout(value);
        }
    }

    protected static class RangeDataSilhouetteOverlay extends BaseCustomUIPanelPlugin {
        protected final FishSpec spec;
        protected PositionAPI pos;

        protected RangeDataSilhouetteOverlay(FishSpec spec) {
            this.spec = spec;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;

            float size = Math.min(pos.getWidth(), pos.getHeight());
            FishIcons.drawBacklit(spec, pos.getCenterX(), pos.getCenterY(),
                    size * 0.5f, size * 0.7f, alphaMult);
        }
    }

    public static class Blueprint extends FishReward {
        public final String itemId;
        public final String data;

        public Blueprint(String itemId, String data) {
            this.itemId = itemId;
            this.data = data;
        }

        @Override
        public String describe() {
            if (Items.SHIP_BP.equals(itemId)) return named("blueprint", hullName());
            if (Items.WEAPON_BP.equals(itemId)) return named("weapon blueprint", weaponName());
            if (Items.FIGHTER_BP.equals(itemId)) return named("fighter blueprint", wingName());

            return "something out of a crate";
        }

        protected String named(String kind, String what) {
            return what == null ? "a " + kind : "a " + what + " " + kind;
        }

        protected String hullName() {
            ShipHullSpecAPI spec = data == null ? null : Global.getSettings().getHullSpec(data);

            return spec == null ? null : spec.getHullName();
        }

        protected String weaponName() {
            WeaponSpecAPI spec = data == null ? null : Global.getSettings().getWeaponSpec(data);

            return spec == null ? null : spec.getWeaponName();
        }

        protected String wingName() {
            FighterWingSpecAPI spec = data == null ? null : Global.getSettings().getFighterWingSpec(data);

            return spec == null ? null : spec.getWingName();
        }

        @Override
        public void grant() {
            CargoAPI cargo = getPlayerCargo();
            if (cargo == null) return;

            cargo.addSpecial(new SpecialItemData(itemId, data), 1);
        }
    }

    public static class Commodity extends FishReward {
        public final String commodityId;
        public final int quantity;

        public Commodity(String commodityId, int quantity) {
            this.commodityId = commodityId;
            this.quantity = quantity;
        }

        @Override
        public String describe() {
            return new Credits(getCreditValue()).describe();
        }

        @Override
        public void grant() {
            new Credits(getCreditValue()).grant();
        }

        protected int getCreditValue() {
            return FishRewardRoller.creditPayout(quantity * 120);
        }
    }

    public abstract String describe();

    public abstract void grant();

    public boolean addOfferDetails(TooltipMakerAPI tooltip, float pad) {
        return false;
    }

    public boolean hasOfferDetails() {
        return false;
    }

    public String getSchematicKey() {
        return null;
    }

    protected static void addSchematicPurchase(TooltipMakerAPI item, ShopPricing.Price price,
                                               String thing) {
        if (item == null) return;

        item.addPara("This unlocks %s for purchase at the Fishing Outfitter. It does not include"
                        + " the %s itself.", 6f, Misc.getGrayColor(), Misc.getHighlightColor(),
                thing, thing);
    }

    protected static String describeFit(Tackle.Fit fit) {
        if (fit == null) return "fishing rig";

        switch (fit) {
            case DRONE: return "LYNE drone rig";
            case HARPOON: return "harpoon line";
            case SEARCHLIGHT: return "breach lamp rig";
            case BOTH: return "LYNE drone rig or harpoon line";
            default: return "fishing rig";
        }
    }

    public static FishReward credits(int amount) {
        return new Credits(amount);
    }

    public static FishReward upgrade(String statId, int levels) {
        return new Upgrade(statId, levels);
    }

    public static FishReward upgradeSchematic(String statId, int targetLevel) {
        return new UpgradeSchematic(statId, targetLevel);
    }

    public static FishReward tackle(Tackle tackle) {
        return new TackleReward(tackle);
    }

    public static FishReward tackleSchematic(Tackle tackle) {
        return new TackleSchematic(tackle);
    }

    public static FishReward locationData(String speciesId, int fallbackCredits) {
        return new LocationData(speciesId, fallbackCredits);
    }

    public static FishReward backdrop(String backdropId) {
        return new BackdropReward(backdropId);
    }

    public static FishReward blueprint(String itemId, String data) {
        return new Blueprint(itemId, data);
    }

    public static FishReward shipBlueprint(String hullId) {
        return new Blueprint(Items.SHIP_BP, hullId);
    }

    public static FishReward specialItem(String itemId, String data) {
        return new Blueprint(itemId, data);
    }

    protected static CargoAPI getPlayerCargo() {
        return Global.getSector().getPlayerFleet() == null
                ? null : Global.getSector().getPlayerFleet().getCargo();
    }
}
