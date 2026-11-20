package catchrelease.campaign.fish.treasure;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.jobs.FishRewardRoller;
import catchrelease.config.SurvivalMode;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TreasureRoller {

    public static boolean rollForTreasure(float chanceMult) {
        return MathUtils.getRandomNumberInRange(0f, 1f)
                < FishConstants.TREASURE_CHANCE * Math.max(0f, chanceMult);
    }

    public static int rollCount(float chanceMult) {
        if (!rollForTreasure(chanceMult)) return 0;

        WeightedRandomPicker<Integer> picker = new WeightedRandomPicker<>();
        picker.add(1, FishConstants.TREASURE_COUNT_WEIGHT_1);
        picker.add(2, FishConstants.TREASURE_COUNT_WEIGHT_2);
        picker.add(3, FishConstants.TREASURE_COUNT_WEIGHT_3);

        return Math.min(picker.pick(), FishConstants.TREASURE_MAX_PER_CATCH);
    }

    public static TreasureRarity rollRarity() {
        return rollRarity(1f);
    }

    public static TreasureRarity rollRarity(float rarityBias) {
        return rollRarity(rarityBias, TreasureRarity.COMMON);
    }

    public static TreasureRarity rollRarity(float rarityBias, TreasureRarity minimum) {
        WeightedRandomPicker<TreasureRarity> picker = new WeightedRandomPicker<>();

        for (TreasureRarity rarity : TreasureRarity.values()) {
            if (rarity.rank < minimum.rank) continue;
            picker.add(rarity, rarity.weight * (float) Math.pow(rarityBias, rarity.rank));
        }

        return picker.pick();
    }

    public static TreasureAward award(TreasureRarity rarity, boolean hasShipTackle) {
        TreasureAward award = new TreasureAward(rarity);

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo == null) return award;

        boolean survival = SurvivalMode.isOn();
        switch (rarity) {
            case UNCOMMON:
                if (hasShipTackle) awardHull(award, survival ? HullSize.DESTROYER : HullSize.CRUISER);
                else awardCommon(award, cargo);
                awardRangeData(award, MathUtils.getRandom());
                break;
            case RARE:
                if (hasShipTackle) awardHull(award, survival ? HullSize.CRUISER : HullSize.CAPITAL_SHIP);
                else awardFromDropGroup(award, cargo, FishConstants.TREASURE_GROUP_BLUEPRINTS);
                break;
            case EPIC:
                awardFromDropGroup(award, cargo, FishConstants.TREASURE_GROUP_RARE_TECH);
                break;
            default:
                awardCommon(award, cargo);
        }

        return award;
    }

    protected static void awardCommon(TreasureAward award, CargoAPI cargo) {
        float roll = MathUtils.getRandomNumberInRange(0f, 1f);

        if (roll >= 0.8f) {
            if (survivalKeeps("ext_fighterSalvageDropFraction")) {
                awardFighter(award, cargo);
                return;
            }
        } else if (roll >= 0.6f) {
            if (survivalKeeps("ext_weaponSalvageDropFraction")) {
                awardWeapon(award, cargo);
                return;
            }
        }
        awardCommodity(award, cargo);
    }

    // survival keeps a weapon or LPC at vanilla's salvage drop fraction; the rest falls to a commodity
    protected static boolean survivalKeeps(String fractionKey) {
        return !SurvivalMode.isOn()
                || MathUtils.getRandomNumberInRange(0f, 1f) < Global.getSettings().getFloat(fractionKey);
    }

    protected static void awardRangeData(TreasureAward award, Random random) {
        if (random.nextFloat() >= FishConstants.TREASURE_RANGE_DATA_CHANCE) return;

        for (FishReward reward : FishRewardRoller.rollLocationData(random, 1, 0)) {
            FishReward.LocationData data = (FishReward.LocationData) reward;
            var spec = FishSpecLoader.getFishSpec(data.speciesId);
            data.grant();
            award.items.add(new TreasureAward.Item("Range data: " + spec.getDisplayName(),
                    "graphics/icons/intel/remote_survey_data.png", 1, spec.rarity.color));
        }
    }

    protected static void awardCommodity(TreasureAward award, CargoAPI cargo) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            if (spec.isNonEcon() || spec.isMeta() || spec.isPersonnel()) continue;
            if (spec.getBasePrice() <= 0f) continue;

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
            if (isLootWeapon(spec)) picker.add(spec, 1f);
        }

        if (picker.isEmpty()) {
            awardCommodity(award, cargo);
            return;
        }

        WeaponSpecAPI spec = picker.pick();
        cargo.addWeapons(spec.getWeaponId(), 1);

        award.items.add(new TreasureAward.Item(spec.getWeaponName(), spec.getTurretSpriteName(), 1));
    }

    protected static boolean isLootWeapon(WeaponSpecAPI spec) {
        if (spec.getWeaponId() == null) return false;
        if (spec.hasTag(Tags.RESTRICTED) || spec.hasTag(Tags.NO_DROP) || spec.hasTag(Tags.WEAPON_NO_SELL)) return false;
        if (spec.getAIHints().contains(AIHints.SYSTEM)) return false;

        return switch (spec.getType()) {
            case BUILT_IN, DECORATIVE, SYSTEM, STATION_MODULE, LAUNCH_BAY -> false;
            default -> true;
        };
    }

    protected static void awardFighter(TreasureAward award, CargoAPI cargo) {
        WeightedRandomPicker<FighterWingSpecAPI> picker = new WeightedRandomPicker<>();

        for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
            if (spec.getId() == null) continue;
            if (spec.hasTag(Tags.RESTRICTED) || spec.hasTag(Tags.WING_NO_DROP) || spec.hasTag(Tags.WING_NO_SELL)) continue;

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

    protected static String wingName(FighterWingSpecAPI spec) {
        String name = spec.getWingName();
        if (name == null || name.isEmpty()) return "fighter wing";

        return name.toLowerCase().endsWith("wing") ? name : name + " wing";
    }

    protected static String getWingSprite(FighterWingSpecAPI spec) {
        try {
            return Global.getSettings().getVariant(spec.getVariantId()).getHullSpec().getSpriteName();
        } catch (Exception e) {
            return null;
        }
    }

    protected static void awardHull(TreasureAward award, HullSize maxSize) {
        boolean survival = SurvivalMode.isOn();
        WeightedRandomPicker<ShipHullSpecAPI> picker =
                new WeightedRandomPicker<>();

        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (spec.getHullId() == null) continue;
            if (spec.hasTag("restricted") || spec.hasTag("no_drop")) continue;
            if (spec.isCivilianNonCarrier() && MathUtils.getRandomNumberInRange(0f, 1f) < 0.5f) continue;
            if (spec.getHullSize() == null) continue;
            if (spec.getHullSize().ordinal() > maxSize.ordinal()) continue;
            if (spec.getHullSize().ordinal() < HullSize.FRIGATE.ordinal()) continue;

            picker.add(spec, survival ? 1f / Math.max(1f, spec.getBaseValue())
                    : 1f / (float) Math.pow(2, spec.getHullSize().ordinal()));
        }

        if (picker.isEmpty()) {
            awardCommon(award, Global.getSector().getPlayerFleet().getCargo());
            return;
        }

        ShipHullSpecAPI spec = picker.pick();
        int dMods = survival ? hullDMods(spec.getBaseValue(), MathUtils.getRandom()) : 0;

        Global.getSector().getPlayerFleet().getCargo().getMothballedShips()
                .addFleetMember(createHull(spec, dMods));

        award.items.add(new TreasureAward.Item(spec.getHullName() + "-class hull",
                spec.getSpriteName(), 1));
    }

    // cheaper hulls come clean; the cap stops a capital from rolling more than combat would add
    protected static int hullDMods(float baseValue, Random random) {
        if (baseValue < FishConstants.TREASURE_HULL_DMOD_VALUE) return 0;

        float expected = baseValue / FishConstants.TREASURE_HULL_DMOD_VALUE;
        int count = (int) expected;
        if (random.nextFloat() < expected - count) count++;

        return Math.min(count, FishConstants.TREASURE_HULL_MAX_DMODS);
    }

    // as vanilla's SurplusShipHull: a cloned _Hull variant turned into its d-hull before the d-mods go on
    protected static FleetMemberAPI createHull(ShipHullSpecAPI spec, int dMods) {
        if (dMods <= 0) return Global.getFactory().createFleetMember(FleetMemberType.SHIP, spec.getHullId() + "_Hull");

        ShipVariantAPI variant = Global.getSettings().getVariant(spec.getHullId() + "_Hull").clone();
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        DModManager.setDHull(variant);
        DModManager.addDMods(member, true, dMods, MathUtils.getRandom());

        return member;
    }

    protected static void awardFromDropGroup(TreasureAward award, CargoAPI cargo, String group) {
        CargoAPI salvage = rollDropGroup(group);

        if (salvage == null || salvage.isEmpty()) {
            // survival's salvage cut empties more of these; a full common roll would undo it
            if (SurvivalMode.isOn()) awardCommodity(award, cargo);
            else awardCommon(award, cargo);
            return;
        }

        addSalvage(award, cargo, salvage);
    }

    protected static CargoAPI rollDropGroup(String group) {
        DropData drop = new DropData();
        drop.chances = 1;
        drop.group = group;

        List<DropData> random = new ArrayList<>();
        random.add(drop);

        return SalvageEntity.generateSalvage(new Random(), 1f, 1f, 1f, 1f, null, random);
    }

    protected static void addSalvage(TreasureAward award, CargoAPI cargo, CargoAPI salvage) {
        cargo.addAll(salvage);

        for (CargoStackAPI stack : salvage.getStacksCopy()) {
            if (stack.isNull()) continue;

            award.items.add(new TreasureAward.Item(stack.getDisplayName(), getStackSprite(stack),
                    Math.max(1, Math.round(stack.getSize()))));
        }
    }

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
        }

        return null;
    }
}
