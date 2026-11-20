package catchrelease.tools;

import catchrelease.campaign.fish.treasure.TreasureAward;
import catchrelease.campaign.fish.treasure.TreasureRarity;
import catchrelease.campaign.fish.treasure.TreasureRoller;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import org.lazywizard.lazylib.MathUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class TreasureLootChecks extends TreasureRoller {

    public static void main(String[] args) {
        int[] counts = new int[3];
        int[] survivalCounts = new int[3];
        boolean[] survival = {false};
        try (var env = new FishingParityChecks.Environment()) {
            Global.setSettings(proxy(SettingsAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getFloat" -> 1f;
                case "getInt" -> 1;
                case "getBoolean" -> false;
                case "getColor" -> java.awt.Color.WHITE;
                default -> throw new AssertionError(m);
            }));
            CommoditySpecAPI commodity = proxy(CommoditySpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "isNonEcon", "isMeta", "isPersonnel" -> false;
                case "getBasePrice" -> 100f;
                case "getId", "getName", "getIconName" -> "supplies";
                default -> throw new AssertionError(m);
            });
            WeaponSpecAPI weapon = proxy(WeaponSpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasTag" -> false;
                case "getAIHints" -> EnumSet.noneOf(AIHints.class);
                case "getType" -> WeaponType.BALLISTIC;
                case "getWeaponId", "getWeaponName", "getTurretSpriteName" -> "weapon";
                default -> throw new AssertionError(m);
            });
            WeaponSpecAPI minelayer = proxy(WeaponSpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasTag" -> false;
                case "getAIHints" -> EnumSet.of(AIHints.SYSTEM);
                case "getType" -> WeaponType.MISSILE;
                case "getWeaponId" -> "minelayer1";
                default -> throw new AssertionError(m);
            });
            WeaponSpecAPI decorative = proxy(WeaponSpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasTag" -> false;
                case "getAIHints" -> EnumSet.noneOf(AIHints.class);
                case "getType" -> WeaponType.DECORATIVE;
                case "getWeaponId" -> "lidardish";
                default -> throw new AssertionError(m);
            });
            FighterWingSpecAPI wing = proxy(FighterWingSpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasTag" -> false;
                case "getId", "getWingName", "getVariantId" -> "wing";
                default -> throw new AssertionError(m);
            });
            Global.setSector(proxy(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "isExtMode" -> survival[0];
                default -> throw new AssertionError(m);
            }));
            Global.setSettings(proxy(SettingsAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getCurrentState" -> GameState.CAMPAIGN;
                case "getFloat" -> switch ((String) a[0]) {
                    case "ext_weaponSalvageDropFraction" -> 0.2f;
                    case "ext_fighterSalvageDropFraction" -> 0.3f;
                    default -> 1f;
                };
                case "getInt" -> 1;
                case "getBoolean" -> false;
                case "getColor" -> java.awt.Color.WHITE;
                case "getAllCommoditySpecs" -> List.of(commodity);
                case "getCommoditySpec" -> commodity;
                case "getAllWeaponSpecs" -> List.of(weapon, minelayer, decorative);
                case "getAllFighterWingSpecs" -> List.of(wing);
                case "getVariant" -> null;
                default -> throw new AssertionError(m);
            }));
            int[][] target = {counts};
            CargoAPI cargo = proxy(CargoAPI.class, (p, m, a) -> {
                int index = switch (m.getName()) {
                    case "addCommodity" -> 0;
                    case "addWeapons" -> 1;
                    case "addFighters" -> 2;
                    default -> throw new AssertionError(m);
                };
                if (index == 1 && !"weapon".equals(a[0])) {
                    throw new AssertionError("Treasure awarded a system or decorative weapon: " + a[0]);
                }
                if (((Number) a[1]).intValue() != (index == 0 ? 25 : 1)) {
                    throw new AssertionError("Award quantity changed: " + m.getName());
                }
                target[0][index]++;
                return null;
            });
            MathUtils.getRandom().setSeed(71345L);
            roll(cargo);
            survival[0] = true;
            target[0] = survivalCounts;
            roll(cargo);
        }
        // survival keeps weapons at 0.2 and LPCs at 0.3 of their 20% shares, the rest are commodities
        requireShares(counts, new float[]{0.6f, 0.2f, 0.2f});
        requireShares(survivalCounts, new float[]{0.9f, 0.04f, 0.06f});
        hullDMods();
        System.out.printf("Treasure loot: 10,000 awards passed (%d commodities, %d weapons, %d LPCs); "
                        + "survival (%d, %d, %d)%n", counts[0], counts[1], counts[2],
                survivalCounts[0], survivalCounts[1], survivalCounts[2]);
    }

    private static void roll(CargoAPI cargo) {
        for (int i = 0; i < 10000; i++) {
            TreasureAward award = new TreasureAward(TreasureRarity.COMMON);
            awardCommon(award, cargo);
            if (award.items.size() != 1) throw new AssertionError("Each roll awards one item stack");
        }
    }

    private static void requireShares(int[] counts, float[] expected) {
        for (int i = 0; i < counts.length; i++) {
            if (Math.abs(counts[i] / 10000f - expected[i]) > 0.02f) {
                throw new AssertionError("Wrong share for category " + i + ": " + counts[i]);
            }
        }
        if (counts[0] + counts[1] + counts[2] != 10000) throw new AssertionError("Missing cargo awards");
    }

    private static void hullDMods() {
        Random random = new Random(1L);
        if (TreasureRoller.hullDMods(49_999f, random) != 0) throw new AssertionError("cheap hull got d-mods");
        if (TreasureRoller.hullDMods(150_000f, random) != 3) throw new AssertionError("whole steps are certain");
        if (TreasureRoller.hullDMods(900_000f, random) != 5) throw new AssertionError("d-mods capped at 5");

        int twos = 0;
        for (int i = 0; i < 10000; i++) {
            int dMods = TreasureRoller.hullDMods(75_000f, random);
            if (dMods != 1 && dMods != 2) throw new AssertionError("75,000 rolls 1 or 2 d-mods: " + dMods);
            if (dMods == 2) twos++;
        }
        if (Math.abs(twos / 10000f - 0.5f) > 0.02f) throw new AssertionError("remainder chance: " + twos);
    }
}
