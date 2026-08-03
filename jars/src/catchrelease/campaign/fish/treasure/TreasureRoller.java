package catchrelease.campaign.fish.treasure;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
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
 */
public class TreasureRoller {

    /**
     * Whether anything at all is down there this time. Deliberately low: treasure that shows up
     * every other catch is not treasure, it is a second reward slot.
     */
    public static boolean rollForTreasure() {
        return MathUtils.getRandomNumberInRange(0f, 1f) < FishConstants.TREASURE_CHANCE;
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
     * @return a description of what came up, for the readout - never null, never empty
     */
    public static String award(TreasureRarity rarity, boolean hasShipTackle) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo == null) return "nothing";

        switch (rarity) {
            case UNCOMMON:
                if (hasShipTackle) return awardHull(HullSize.CRUISER);
                return awardCommon(cargo);
            case RARE:
                if (hasShipTackle) return awardHull(HullSize.CAPITAL_SHIP);
                return awardFromDropGroup(cargo, FishConstants.TREASURE_GROUP_BLUEPRINTS);
            case LEGENDARY:
                return awardFromDropGroup(cargo, FishConstants.TREASURE_GROUP_RARE_TECH);
            default:
                return awardCommon(cargo);
        }
    }

    /** A pile of something, a weapon, or a fighter chip - whichever the roll lands on. */
    protected static String awardCommon(CargoAPI cargo) {
        float roll = MathUtils.getRandomNumberInRange(0f, 1f);

        if (roll < 0.5f) return awardCommodity(cargo);
        if (roll < 0.8f) return awardWeapon(cargo);

        return awardFighter(cargo);
    }

    protected static String awardCommodity(CargoAPI cargo) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {

            if (spec.isNonEcon() || spec.isMeta() || spec.isPersonnel()) continue;
            if (spec.getBasePrice() <= 0f) continue;

            //cheap things come up in bulk and expensive ones rarely, which is what makes a pile of
            //something read as a find rather than as a rounding error
            picker.add(spec.getId(), 1f / Math.max(1f, spec.getBasePrice()));
        }

        String id = picker.isEmpty() ? Commodities.SUPPLIES : picker.pick();

        float unit = Math.max(1f, Global.getSettings().getCommoditySpec(id).getBasePrice());
        int amount = Math.max(1, Math.round(FishConstants.TREASURE_COMMODITY_VALUE / unit));

        cargo.addCommodity(id, amount);

        return amount + " " + Global.getSettings().getCommoditySpec(id).getName().toLowerCase();
    }

    protected static String awardWeapon(CargoAPI cargo) {
        WeightedRandomPicker<WeaponSpecAPI> picker = new WeightedRandomPicker<>();

        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
            if (spec.getWeaponId() == null) continue;
            if (spec.hasTag("restricted") || spec.hasTag("no_drop")) continue;

            picker.add(spec, 1f);
        }

        if (picker.isEmpty()) return awardCommodity(cargo);

        WeaponSpecAPI spec = picker.pick();
        cargo.addWeapons(spec.getWeaponId(), 1);

        return spec.getWeaponName();
    }

    protected static String awardFighter(CargoAPI cargo) {
        WeightedRandomPicker<FighterWingSpecAPI> picker = new WeightedRandomPicker<>();

        for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
            if (spec.getId() == null) continue;
            if (spec.hasTag("restricted") || spec.hasTag("no_drop")) continue;

            picker.add(spec, 1f);
        }

        if (picker.isEmpty()) return awardCommodity(cargo);

        FighterWingSpecAPI spec = picker.pick();
        cargo.addFighters(spec.getId(), 1);

        return spec.getWingName() + " wing";
    }

    /**
     * A hull, mothballed into the fleet, no bigger than the tackle can lift.
     * <p>
     * Mothballed rather than crewed: something dragged up out of a rupture arrives as a hull, and
     * making the player decide whether to fit it out is more interesting than handing it over ready.
     */
    protected static String awardHull(HullSize maxSize) {
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

        if (picker.isEmpty()) return awardCommon(Global.getSector().getPlayerFleet().getCargo());

        ShipHullSpecAPI spec = picker.pick();

        Global.getSector().getPlayerFleet().getCargo().getMothballedShips()
                .addFleetMember(Global.getFactory().createFleetMember(
                        FleetMemberType.SHIP, spec.getHullId() + "_Hull"));

        return spec.getHullName() + "-class hull";
    }

    /**
     * The game's own tables. Rolling a drop group rather than listing what is in one is what keeps
     * "anything in rare_tech" true after the game or another mod changes what that means.
     */
    protected static String awardFromDropGroup(CargoAPI cargo, String group) {
        DropData drop = new DropData();
        drop.chances = 1;
        drop.group = group;

        List<DropData> random = new ArrayList<>();
        random.add(drop);

        CargoAPI salvage = SalvageEntity.generateSalvage(
                new Random(), 1f, 1f, 1f, 1f, null, random);

        if (salvage == null || salvage.isEmpty()) return awardCommon(cargo);

        cargo.addAll(salvage);

        return describe(salvage);
    }

    /** What came out of a drop group, said as a list rather than as a count. */
    protected static String describe(CargoAPI salvage) {
        StringBuilder out = new StringBuilder();

        for (com.fs.starfarer.api.campaign.CargoStackAPI stack : salvage.getStacksCopy()) {
            if (out.length() > 0) out.append(", ");
            out.append(stack.getDisplayName());
        }

        return out.length() == 0 ? "nothing" : out.toString();
    }
}
