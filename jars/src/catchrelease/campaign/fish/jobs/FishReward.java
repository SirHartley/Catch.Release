package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.campaign.fish.colony.Backdrops;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.helper.loading.BackdropLoader;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.shop.ShopEntry;
import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.campaign.fish.shop.ShopPricing;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
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

/**
 * A job's payout: an abstract thing that can describe and hand itself over.
 * <p>
 * Every kind here reuses an existing grant elsewhere in the mod (upgrades, tackle, shop items), so
 * a reward and a purchase put the same thing in the same place.
 */
public abstract class FishReward {

    public static final String SCHEMATIC_ICON =
            "graphics/catchrelease/icon/small_icon_catchrelease2.png";

    /** What the offer says out loud, as a noun phrase: "2,000 credits", "a Barbed Head". */
    public abstract String describe();

    /** Hands it over. Called once, when the job is paid out. */
    public abstract void grant();

    /** Adds an offer-time visual explanation when a reward needs more than its noun phrase. */
    public boolean addOfferDetails(TooltipMakerAPI tooltip, float pad) {
        return false;
    }

    public boolean hasOfferDetails() {
        return false;
    }

    /** Stable identity for quest-pool reservation; null for rewards that are not schematics. */
    public String getSchematicKey() {
        return null;
    }

    /** Shared bottom half of both schematic cards: what earning it does and what buying costs. */
    protected static void addSchematicPurchase(TooltipMakerAPI item, ShopPricing.Price price,
                                               String thing) {
        if (item == null) return;

        item.addPara("This unlocks %s for purchase at the Fishing Outfitter. It does not include"
                        + " the %s itself.", 6f, Misc.getGrayColor(), Misc.getHighlightColor(),
                thing, thing);

        if (price == null) return;

        String credits = Misc.getDGSCredits(price.credits);
        item.addPara("Purchase price after unlock: %s credits.", 6f, Misc.getGrayColor(),
                Misc.getHighlightColor(), credits);

        if (price.fish != null) {
            String ask = price.fish.describe();
            LabelAPI catchLine = item.addPara("Catch required as well: %s.", 3f,
                    Misc.getGrayColor(), Misc.getHighlightColor(), ask);
            FishRequirement.highlight(catchLine, Collections.singletonList(price.fish), ask);
        }
    }

    /** Player-facing name for the hardware a tackle schematic can be fitted to. */
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

    /** Money, for the ones who only have money. */
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

    /** An upgrade-stat level grant, clamped to the sheet's ceiling on the way in; levels (not a flat unlock) let small and large favours reuse the same reward type. */
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

    /** A rig module: grants ownership and fits it, displacing whatever was in that slot (same as a purchase) - free to undo since the displaced module stays owned. */
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
            //BOTH fits either rig; default to drones, since everything fits there
            Tackle.Fit rig = tackle.fit == Tackle.Fit.BOTH ? Tackle.Fit.DRONE : tackle.fit;

            //grants ownership, not just a fit - removing it later must not require buying it back
            TackleManager.own(tackle);
            TackleManager.fit(rig, tackle);
        }
    }

    /** Permission to buy one of the final two rungs on an outfitter upgrade ladder. */
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

            return "a schematic for " + name + " level " + targetLevel;
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
            TooltipMakerAPI item = tooltip.beginImageWithText(SCHEMATIC_ICON, 48f);
            item.addPara("Fishing Outfitter schematic", Misc.getHighlightColor(), 0f);
            item.addPara("%s — level %s", 3f, Misc.getTextColor(), Misc.getHighlightColor(),
                    entry.getName(), String.valueOf(targetLevel));
            item.addPara(stat.description, 6f);
            item.addPara("This rung: %s → %s", 3f, Misc.getGrayColor(),
                    Misc.getPositiveHighlightColor(), entry.getValueAt(targetLevel - 1),
                    entry.getValueAt(targetLevel));
            addSchematicPurchase(item, ShopPricing.getPrice(stat, targetLevel), "upgrade rung");
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

    /** Live overlay on a job's schematic image; it shares the exact key used by the shop ring. */
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

    /** A purchase permission for one outfitter modifier; the hardware itself is still bought. */
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

            TooltipMakerAPI item = tooltip.beginImageWithText(SCHEMATIC_ICON, 48f);
            item.addPara("Fishing Outfitter schematic", Misc.getHighlightColor(), 0f);
            item.addPara(tackle.name, Misc.getTextColor(), 3f);
            item.addPara("Fits: %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(),
                    describeFit(tackle.fit));
            item.addPara(tackle.description, 6f);
            addSchematicPurchase(item, ShopPricing.getPrice(tackle), "tackle");
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

    /**
     * A scene for the back of an aquarium: the one payment that is worth nothing whatsoever.
     * <p>
     * Deliberately. Every other reward here makes the rig better, the hold fuller or the map more
     * legible, and a job that pays in one is a job that has moved the campaign along. A backdrop
     * moves nothing: it is a picture, and the only thing it is good for is that somebody who has
     * been fishing for a hundred hours has somewhere to put it. That is exactly why it can be
     * handed out freely - there is no ladder for it to unbalance.
     * <p>
     * Granted to the player rather than to a colony, on {@link Backdrops}' split: which of your
     * conservatories ends up hanging it is a decision for later, and possibly for a colony that
     * does not exist yet.
     */
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

    /** A word about where something lives, which is the reward only a fisherman would want. */
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
        public void grant() {
            if (isRedundant()) {
                new Credits(getFallbackCredits()).grant();
            } else {
                FishLog.unlockLocationData(speciesId);
            }
        }

        /** A landed specimen unlocks this data immediately, as does obtaining the chart elsewhere. */
        protected boolean isRedundant() {
            return FishLog.isLocationDataUnlocked(speciesId);
        }

        /** Saves from before the fallback was stored deserialize it as zero. */
        protected int getFallbackCredits() {
            int value = fallbackCredits > 0 ? fallbackCredits : FishRewardRoller.VALUE_PER_FISH;

            return FishRewardRoller.creditPayout(value);
        }
    }

    /** Any special item, blueprints included - a blueprint is a special item where id says the kind and data says which one. */
    public static class Blueprint extends FishReward {
        public final String itemId;
        public final String data;

        public Blueprint(String itemId, String data) {
            this.itemId = itemId;
            this.data = data;
        }

        @Override
        public String describe() {
            //name the specific item when possible - better offer text for deciding whether to take the job
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

    /**
     * Old-save shell for jobs rolled before commodity rewards were removed. Kept under the same
     * class name so XStream can load them, but paid out in the credit value the old roller used.
     */
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
}
