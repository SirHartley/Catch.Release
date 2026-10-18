package catchrelease.tools;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.map.FishPresence;
import catchrelease.campaign.fish.treasure.TreasureAward;
import catchrelease.campaign.fish.treasure.TreasureRarity;
import catchrelease.campaign.fish.treasure.TreasureRoller;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import org.lazywizard.lazylib.MathUtils;

import java.util.HashMap;
import java.util.List;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class TreasureRangeDataChecks extends TreasureRoller {

    private static int checks;

    public static void main(String[] args) {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            FishSpec unknown = species("unknown", FishRarity.EPIC);
            FishSpec known = species("known", FishRarity.COMMON);
            FishSpec caught = species("caught", FishRarity.RARE);
            FishSpec legendary = species("legendary", FishRarity.LEGENDARY);
            FishSpec mechanism = species("mechanism", FishRarity.RARE);
            mechanism.spawnWeight = 0f;
            FishSpec noHabitat = species("noHabitat", FishRarity.COMMON);
            noHabitat.reachedBy.clear();
            for (FishSpec spec : List.of(unknown, known, caught, legendary, mechanism, noHabitat)) {
                env.specs.put(spec.id, spec);
            }
            var log = new HashMap<String, FishLogEntry>();
            FishLogEntry caughtEntry = new FishLogEntry(caught.id);
            caughtEntry.caught = 1;
            log.put(caught.id, caughtEntry);
            env.persistent.put(FishLog.KEY, log);
            FishLog.unlockLocationData(known.id);

            int[] stacks = {0};
            CargoAPI cargo = proxy(CargoAPI.class, (p, m, a) -> {
                if (!m.getName().equals("addCommodity")) throw new AssertionError(m);
                stacks[0]++;
                return null;
            });
            CampaignFleetAPI player = proxy(CampaignFleetAPI.class, (p, m, a) -> {
                if (!m.getName().equals("getCargo")) throw new AssertionError(m);
                return cargo;
            });
            SectorAPI baseSector = Global.getSector();
            Global.setSector(proxy(SectorAPI.class, (p, m, a) ->
                    m.getName().equals("getPlayerFleet") ? player : m.invoke(baseSector, a)));
            CommoditySpecAPI commodity = proxy(CommoditySpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "isNonEcon", "isMeta", "isPersonnel" -> false;
                case "getBasePrice" -> 100f;
                case "getId", "getName", "getIconName" -> "supplies";
                default -> throw new AssertionError(m);
            });
            SettingsAPI baseSettings = Global.getSettings();
            Global.setSettings(proxy(SettingsAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getAllCommoditySpecs" -> List.of(commodity);
                case "getCommoditySpec" -> commodity;
                case "getAllWeaponSpecs", "getAllFighterWingSpecs", "getAllShipHullSpecs" -> List.of();
                default -> m.invoke(baseSettings, a);
            }));

            MathUtils.getRandom().setSeed(912547L);
            for (boolean rig : new boolean[]{false, true}) {
                int drops = 0;
                for (int i = 0; i < 10000; i++) {
                    FishLog.relockLocationData(unknown.id);
                    check(!FishPresence.hasRangeData(unknown), "unknown starts hidden from the map");
                    int before = stacks[0];
                    TreasureAward result = award(TreasureRarity.UNCOMMON, rig);
                    check(stacks[0] == before + 1, "normal chest loot is preserved");
                    if (result.items.size() == 2) {
                        drops++;
                        TreasureAward.Item data = result.items.get(1);
                        check(data.name.equals("Range data: unknown"), "only eligible species selected");
                        check(data.color.equals(unknown.rarity.color), "species colour retained");
                        check(data.sprite.equals("graphics/icons/intel/remote_survey_data.png"), "no uncaught portrait revealed");
                        check(FishPresence.hasRangeData(unknown) && !FishLog.isCaught(unknown.id), "map unlock without a catch");
                    } else {
                        check(result.items.size() == 1 && !FishLog.isLocationDataUnlocked(unknown.id), "miss grants no data");
                    }
                }
                check(Math.abs(drops / 10000f - 0.25f) < 0.02f, "25 percent with or without the lifting rig");
            }
            FishLog.unlockLocationData(unknown.id);
            for (int i = 0; i < 100; i++) {
                check(award(TreasureRarity.UNCOMMON, false).items.size() == 1, "no duplicate or ineligible fallback");
            }
            FishLog.relockLocationData(unknown.id);
            for (int i = 0; i < 100; i++) {
                check(award(TreasureRarity.COMMON, false).items.size() == 1, "common chest unchanged");
            }
            check(!FishLog.isLocationDataUnlocked(unknown.id), "common chest never unlocks data");
            for (String id : List.of(legendary.id, mechanism.id, noHabitat.id)) {
                check(FishLog.get(id) == null, "excluded species never added to log");
            }
        }
        System.out.println("Treasure range-data checks passed: " + checks);
    }

    private static FishSpec species(String id, FishRarity rarity) {
        FishSpec spec = new FishSpec();
        spec.id = id;
        spec.rarity = rarity;
        spec.reachedBy.add(CatchImplement.BREACH_LAMP);
        return spec;
    }

    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }
}
