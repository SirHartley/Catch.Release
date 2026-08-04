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
 * What a piece of treasure turns out to be.
 * <p>
 * The two rarer tiers go through the game's own drop groups - "blueprints" and "rare_tech" are
 * tables vanilla already maintains, and rolling them rather than listing their contents means this
 * keeps working when those tables change and when another mod adds to them. The two commoner tiers
 * are picked from the loaded specs directly, since "any commodity" and "any weapon" are not drop
 * groups and would have to be invented.
 * <p>
 * Ship hulls are gated on tackle: they come out only for a rig carrying the gear for it, and only up
 * to cruiser at uncommon and capital at rare. Nothing here decides whether that gear is fitted - the
 * caller passes it in, so the tackle system can turn it on without this having to know what tackle
 * is.
 * <p>
 * Everything awarded goes straight into the player's possession; what comes back is a
 * {@link TreasureAward} - the receipt, item by item with the icons the hold will show them under,
 * for the loot card to read out.
 */
public class TreasureRoller {

    /**
     * Whether anything at all is down there this time. Deliberately low: treasure that shows up
     * every other catch is not treasure, it is a second reward slot.
     */
    public static boolean rollForTreasure(float chanceMult) {
        return MathUtils.getRandomNumberInRange(0f, 1f)
                < FishConstants.TREASURE_CHANCE * Math.max(0f, chanceMult);
    }

    /**
     * How many pieces this catch holds, 0 to {@link FishConstants#TREASURE_MAX_PER_CATCH}. The
     * chance gate answers whether there is anything at all; past it, the weights pick how much,
     * with three kept a story. Whether the later pieces are ever seen is the catch's problem -
     * they spawn on a clock, and a short fight ends before they were due.
     */
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
     * @param hasShipTackle whether the rig can bring a hull up. Without it, the tiers that would
     *                      have given one give their usual contents instead
     * @return the receipt - never null, never empty
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

    /** A pile of something, a weapon, or a fighter chip - whichever the roll lands on. */
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

            //cheap things come up in bulk and expensive ones rarely, which is what makes a pile of
            //something read as a find rather than as a rounding error
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

        award.items.add(new TreasureAward.Item(spec.getWingName() + " wing",
                getWingSprite(spec), 1));
    }

    /** A wing's cargo chip shows the fighter itself, so the card does too. Null if anything is off. */
    protected static String getWingSprite(FighterWingSpecAPI spec) {
        try {
            return Global.getSettings().getVariant(spec.getVariantId()).getHullSpec().getSpriteName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A hull, mothballed into the fleet, no bigger than the tackle can lift.
     * <p>
     * Mothballed rather than crewed: something dragged up out of a rupture arrives as a hull, and
     * making the player decide whether to fit it out is more interesting than handing it over ready.
     */
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

            //bigger is rarer, so a capital is a story rather than a Tuesday
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

    /**
     * The game's own tables. Rolling a drop group rather than listing what is in one is what keeps
     * "anything in rare_tech" true after the game or another mod changes what that means.
     */
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

    /**
     * The icon the hold will show a stack under, asked type by type - a cargo stack has no single
     * icon accessor, so each kind is asked in its own words. Null for anything exotic; the card
     * has a fallback.
     */
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
            //an icon is decoration; a missing one is not worth more than the fallback
        }

        return null;
    }
}
