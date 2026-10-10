package catchrelease.tools;

import catchrelease.campaign.fish.treasure.TreasureAward;
import catchrelease.campaign.fish.treasure.TreasureRarity;
import catchrelease.campaign.fish.treasure.TreasureRoller;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import org.lazywizard.lazylib.MathUtils;

import java.util.List;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class TreasureLootChecks extends TreasureRoller {

    public static void main(String[] args) {
        int[] counts = new int[3];
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
                case "getWeaponId", "getWeaponName", "getTurretSpriteName" -> "weapon";
                default -> throw new AssertionError(m);
            });
            FighterWingSpecAPI wing = proxy(FighterWingSpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasTag" -> false;
                case "getId", "getWingName", "getVariantId" -> "wing";
                default -> throw new AssertionError(m);
            });
            Global.setSettings(proxy(SettingsAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getAllCommoditySpecs" -> List.of(commodity);
                case "getCommoditySpec" -> commodity;
                case "getAllWeaponSpecs" -> List.of(weapon);
                case "getAllFighterWingSpecs" -> List.of(wing);
                case "getVariant" -> null;
                default -> throw new AssertionError(m);
            }));
            CargoAPI cargo = proxy(CargoAPI.class, (p, m, a) -> {
                int index = switch (m.getName()) {
                    case "addCommodity" -> 0;
                    case "addWeapons" -> 1;
                    case "addFighters" -> 2;
                    default -> throw new AssertionError(m);
                };
                if (((Number) a[1]).intValue() != (index == 0 ? 25 : 1)) {
                    throw new AssertionError("Award quantity changed: " + m.getName());
                }
                counts[index]++;
                return null;
            });
            MathUtils.getRandom().setSeed(71345L);
            for (int i = 0; i < 10000; i++) {
                TreasureAward award = new TreasureAward(TreasureRarity.COMMON);
                awardCommon(award, cargo);
                if (award.items.size() != 1) throw new AssertionError("Each roll awards one item stack");
            }
        }
        float[] expected = {0.6f, 0.2f, 0.2f};
        for (int i = 0; i < counts.length; i++) {
            if (Math.abs(counts[i] / 10000f - expected[i]) > 0.02f) {
                throw new AssertionError("Wrong share for category " + i + ": " + counts[i]);
            }
        }
        if (counts[0] + counts[1] + counts[2] != 10000) throw new AssertionError("Missing cargo awards");
        System.out.printf("Treasure loot: 10,000 awards passed (%d commodities, %d weapons, %d LPCs)%n",
                counts[0], counts[1], counts[2]);
    }
}
