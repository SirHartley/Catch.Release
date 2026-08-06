package catchrelease.campaign.fish.treasure;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rolls treasure contents. The two rarer tiers roll the game's own "blueprints"/"rare_tech" drop
 * groups (so they track vanilla/mod changes); the two commoner tiers pick directly from loaded
 * specs. Ship hulls only come up when {@code hasShipTackle}, capped at cruiser (uncommon) or
 * capital (rare). Awards go straight into the player's cargo; the returned {@link TreasureAward} is
 * just the receipt.
 */
public class TreasureRoller {

    public static boolean rollForTreasure(float chanceMult) {
        return MathUtils.getRandomNumberInRange(0f, 1f)
                < FishConstants.TREASURE_CHANCE * Math.max(0f, chanceMult);
    }

    /** 0 to {@link FishConstants#TREASURE_MAX_PER_CATCH}; later pieces may go unspawned if the catch ends first. */
    public static int rollCount(float chanceMult) {
        if (!rollForTreasure(chanceMult)) return 0;

        WeightedRandomPicker<Integer> picker = new WeightedRandomPicker<>();
        picker.add(1, FishConstants.TREASURE_COUNT_WEIGHT_1);
        picker.add(2, FishConstants.TREASURE_COUNT_WEIGHT_2);
        picker.add(3, FishConstants.TREASURE_COUNT_WEIGHT_3);

        return Math.min(picker.pick(), FishConstants.TREASURE_MAX_PER_CATCH);
    }

    public static TreasureRarity rollRarity() {
        WeightedRandomPicker<TreasureRarity> picker = new WeightedRandomPicker<>();

        for (TreasureRarity rarity : TreasureRarity.values()) picker.add(rarity, rarity.weight);

        return picker.pick();
    }

    /**
     * Rolls the contents and puts them somewhere the player can get at them.
     *
     * @param hasShipTackle whether a hull can be awarded; without it, those tiers fall back to their usual contents
     * @return never null, never empty
     */
    public static TreasureAward award(TreasureRarity rarity, boolean hasShipTackle) {
        TreasureAward award = new TreasureAward(rarity);

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo == null) return award;

        switch (rarity) {
            case UNCOMMON:
                if (hasShipTackle) awardHull(award, HullSize.CRUISER);
                else awardCommon(award, cargo);
                break;
            case RARE:
                if (hasShipTackle) awardHull(award, HullSize.CAPITAL_SHIP);
                else awardFromDropGroup(award, cargo, FishConstants.TREASURE_GROUP_BLUEPRINTS);
                break;
            case LEGENDARY:
                awardFromDropGroup(award, cargo, FishConstants.TREASURE_GROUP_RARE_TECH);
                break;
            default:
                awardCommon(award, cargo);
        }

        return award;
    }

    /** A commodity pile, a weapon, or a fighter chip. */
    protected static void awardCommon(TreasureAward award, CargoAPI cargo) {
        float roll = MathUtils.getRandomNumberInRange(0f, 1f);

        if (roll < 0.5f) awardCommodity(award, cargo);
        else if (roll < 0.8f) awardWeapon(award, cargo);
        else awardFighter(award, cargo);
    }

    protected static void awardCommodity(TreasureAward award, CargoAPI cargo) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {

            if (spec.isNonEcon() || spec.isMeta() || spec.isPersonnel()) continue;
            if (spec.getBasePrice() <= 0f) continue;

            //weighted inversely by price: cheap commodities come up in bulk, expensive ones rarely
            picker.add(spec.getId(), 1f / Math.max(1f, spec.getBasePrice()));
        }

        String id = picker.isEmpty() ? Commodities.SUPPLIES : picker.pick();
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(id);

        float unit = Math.max(1f, spec.getBasePrice());
        int amount = Math.max(1, Math.round(FishConstants.TREASURE_COMMODITY_VALUE / unit));

        cargo.addCommodity(id, amount);

        award.items.add(new TreasureAward.Item(spec.getName(), spec.getIconName(), amount));
    }

    protected static void awardWeapon(TreasureAward award, CargoAPI cargo) {
        WeightedRandomPicker<WeaponSpecAPI> picker = new WeightedRandomPicker<>();

        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
            if (spec.getWeaponId() == null) continue;
            if (spec.hasTag("restricted") || spec.hasTag("no_drop")) continue;

            picker.add(spec, 1f);
        }

        if (picker.isEmpty()) {
            awardCommodity(award, cargo);
            return;
        }

        WeaponSpecAPI spec = picker.pick();
        cargo.addWeapons(spec.getWeaponId(), 1);

        award.items.add(new TreasureAward.Item(spec.getWeaponName(), spec.getTurretSpriteName(), 1));
    }

    protected static void awardFighter(TreasureAward award, CargoAPI cargo) {
        WeightedRandomPicker<FighterWingSpecAPI> picker = new WeightedRandomPicker<>();

        for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
            if (spec.getId() == null) continue;
            if (spec.hasTag("restricted") || spec.hasTag("no_drop")) continue;

            picker.add(spec, 1f);
        }

        if (picker.isEmpty()) {
            awardCommodity(award, cargo);
            return;
        }

        FighterWingSpecAPI spec = picker.pick();
        cargo.addFighters(spec.getId(), 1);

        award.items.add(new TreasureAward.Item(wingName(spec), getWingSprite(spec), 1));
    }

    /** Appends "wing" only if the spec's own name doesn't already end with it (case-insensitive). */
    protected static String wingName(FighterWingSpecAPI spec) {
        String name = spec.getWingName();
        if (name == null || name.isEmpty()) return "fighter wing";

        return name.toLowerCase().endsWith("wing") ? name : name + " wing";
    }

    /** Null on failure. */
    protected static String getWingSprite(FighterWingSpecAPI spec) {
        try {
            return Global.getSettings().getVariant(spec.getVariantId()).getHullSpec().getSpriteName();
        } catch (Exception e) {
            return null;
        }
    }

    /** Adds a mothballed hull (uncrewed) no bigger than {@code maxSize}. */
    protected static void awardHull(TreasureAward award, HullSize maxSize) {
        WeightedRandomPicker<ShipHullSpecAPI> picker =
                new WeightedRandomPicker<>();

        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {

            if (spec.getHullId() == null) continue;
            if (spec.hasTag("restricted") || spec.hasTag("no_drop")) continue;
            if (spec.isCivilianNonCarrier() && MathUtils.getRandomNumberInRange(0f, 1f) < 0.5f) continue;
            if (spec.getHullSize() == null) continue;
            if (spec.getHullSize().ordinal() > maxSize.ordinal()) continue;
            if (spec.getHullSize().ordinal() < HullSize.FRIGATE.ordinal()) continue;

            //weight halves per hull size step, so bigger hulls are rarer
            picker.add(spec, 1f / (float) Math.pow(2, spec.getHullSize().ordinal()));
        }

        if (picker.isEmpty()) {
            awardCommon(award, Global.getSector().getPlayerFleet().getCargo());
            return;
        }

        ShipHullSpecAPI spec = picker.pick();

        Global.getSector().getPlayerFleet().getCargo().getMothballedShips()
                .addFleetMember(Global.getFactory().createFleetMember(
                        FleetMemberType.SHIP, spec.getHullId() + "_Hull"));

        award.items.add(new TreasureAward.Item(spec.getHullName() + "-class hull",
                spec.getSpriteName(), 1));
    }

    /** Rolls vanilla's own drop group rather than listing its contents, so it tracks changes to the table. */
    protected static void awardFromDropGroup(TreasureAward award, CargoAPI cargo, String group) {
        DropData drop = new DropData();
        drop.chances = 1;
        drop.group = group;

        List<DropData> random = new ArrayList<>();
        random.add(drop);

        CargoAPI salvage = SalvageEntity.generateSalvage(
                new Random(), 1f, 1f, 1f, 1f, null, random);

        if (salvage == null || salvage.isEmpty()) {
            awardCommon(award, cargo);
            return;
        }

        cargo.addAll(salvage);

        for (CargoStackAPI stack : salvage.getStacksCopy()) {
            if (stack.isNull()) continue;

            award.items.add(new TreasureAward.Item(stack.getDisplayName(), getStackSprite(stack),
                    Math.max(1, Math.round(stack.getSize()))));
        }
    }

    /** No single icon accessor on {@link CargoStackAPI}, so each stack type is asked separately. Null for anything else. */
    protected static String getStackSprite(CargoStackAPI stack) {
        try {
            if (stack.isCommodityStack() && stack.getResourceIfResource() != null) {
                return stack.getResourceIfResource().getIconName();
            }

            if (stack.isSpecialStack() && stack.getSpecialItemSpecIfSpecial() != null) {
                return stack.getSpecialItemSpecIfSpecial().getIconName();
            }

            if (stack.isWeaponStack() && stack.getWeaponSpecIfWeapon() != null) {
                return stack.getWeaponSpecIfWeapon().getTurretSpriteName();
            }

            if (stack.isFighterWingStack() && stack.getFighterWingSpecIfWing() != null) {
                return getWingSprite(stack.getFighterWingSpecIfWing());
            }

            if (stack.getHullModSpecIfHullMod() != null) {
                return stack.getHullModSpecIfHullMod().getSpriteName();
            }
        } catch (Exception e) {
            //icon is decorative; fall through to null
        }

        return null;
    }
}
