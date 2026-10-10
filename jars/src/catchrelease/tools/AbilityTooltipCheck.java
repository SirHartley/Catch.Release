package catchrelease.tools;

import catchrelease.abilities.harpoon.ability.HarpoonAbilityPlugin;
import catchrelease.abilities.rod.ability.PondInteractionAbilityPlugin;
import catchrelease.abilities.rod.scripts.FishingDroneSwarmScript;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.charges.ChargeManager;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AbilityTooltipCheck {

    private static int checks;

    private AbilityTooltipCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (Global.getSector() != null || Global.getSettings() != null) {
            throw new IllegalStateException("Run in a separate JVM, not inside Starsector.");
        }
        Locale original = Locale.getDefault();
        boolean codex = Global.CODEX_TOOLTIP_MODE;
        try {
            Locale.setDefault(Locale.US);
            Fixture f = new Fixture();
            checkBase(f);
            checkUpgraded(f);
            checkEquipment(f);
            checkStatus(f);
            System.out.println("Ability tooltips: " + checks + " checks passed");
        } finally {
            Global.CODEX_TOOLTIP_MODE = codex;
            Global.setSector(null);
            Global.setSettings(null);
            Locale.setDefault(original);
        }
    }

    private static void checkBase(Fixture f) {
        includes(f.rod(), "Drones: 1", "300 units/s", "190 units", "8 seconds", "No rupture in range.");
        includes(f.harpoon(), "2/2", "12 seconds", "1200 units", "900 units/s");
        for (int tier = 1; tier <= 3; tier++) {
            f.upgrades.setLevel(StatIds.HARPOON_REACH, tier);
            includes(f.harpoon(), (1200 + tier * 120) + " units");
        }
        f.upgrades.setLevel(StatIds.HARPOON_REACH, 0);
        includes(f.lamps(), "Lamps: 2", "Beam radius: 240 units", "30 units/s", "400 units", "100%");
        excludes(f.lamps(), "rarity weighting", "remain visible", "Slows surfaced", "Fitted:");

        f.upgrades.setLevel(StatIds.HARPOON_RECHARGE_TIME, 1);
        includes(f.harpoon(), "10.2 seconds");
        f.upgrades.setLevel(StatIds.SEARCHLIGHT_RARE_CHANCE, 1);
        includes(f.lamps(), "x1.05");
    }

    private static void checkUpgraded(Fixture f) {
        for (UpgradeStat stat : f.upgrades.levelMap.values()) stat.level = stat.maxLevel;
        f.data.remove(ChargeManager.KEY);
        includes(f.rod(), "Drones: 4", "444 units/s", "520 units", "16 seconds", "100%");
        includes(f.harpoon(), "6/6", "4.8 seconds", "1560 units", "1332 units/s", "7.5 degrees");
        includes(f.lamps(), "Lamps: 5", "Beam radius: 540 units", "48 units/s", "1000 units",
                "8 seconds", "x1.20", "50%");
    }

    private static void checkEquipment(Fixture f) {
        for (Tackle tackle : Tackle.values()) {
            for (Tackle.Fit rig : Tackle.Fit.values()) {
                if (!rig.isRig() || !tackle.fits(rig)) continue;
                TackleManager.fit(rig, tackle);
                String text = switch (rig) {
                    case DRONE -> f.rod();
                    case HARPOON -> f.harpoon();
                    default -> f.lamps();
                };
                if (tackle != Tackle.NONE) includes(text, "Fitted: " + tackle.name);
                TackleManager.fit(rig, Tackle.NONE);
            }
        }
        TackleManager.fit(Tackle.Fit.SEARCHLIGHT, Tackle.FANNED_ARRAY);
        includes(f.lamps(), "Fan angle: 64.3 degrees", "33.6 units/s");
        excludes(f.lamps(), "Beam radius");
        TackleManager.fit(Tackle.Fit.SEARCHLIGHT, Tackle.TRACKING_GIMBAL);
        includes(f.lamps(), "4 seconds; 4 seconds between locks");
        TackleManager.fit(Tackle.Fit.SEARCHLIGHT, Tackle.NEEDLE_SENSOR);
        includes(f.lamps(), "Fitted: Needle Sensor", "Passive sensor: occasional ripples while lamps are off.");
        TackleManager.fit(Tackle.Fit.DRONE, Tackle.BAITED_RESONATOR);
        includes(f.lamps(), "x1.80");

        TackleManager.fit(Tackle.Fit.HARPOON, Tackle.EXPLOSIVE_HEAD);
        includes(f.harpoon(), "Consumed on detonation", "immediate hostility");
        excludes(f.harpoon(), "pull the weaker fleet");
        Global.CODEX_TOOLTIP_MODE = true;
        includes(f.harpoon(), "Consumed on detonation");
        excludes(f.harpoon(), "TITLE:", "No harpoons ready.");
        excludes(f.rod(), "TITLE:", "No rupture in range.");
        excludes(f.lamps(), "TITLE:");
        Global.CODEX_TOOLTIP_MODE = false;
    }

    private static void checkStatus(Fixture f) {
        f.data.put(ChargeManager.KEY, new HashMap<>(Map.of(HarpoonAbilityPlugin.CHARGE_ID, 0f)));
        includes(f.harpoon(), "0/6", "No harpoons ready.");
        f.lamps.on = true;
        TackleManager.fit(Tackle.Fit.DRONE, Tackle.NONE);
        TackleManager.own(Tackle.BREACH_COUPLER);
        includes(f.rod(), "Turn off the breach lamps", "520 units from the selected spot");
        TackleManager.fit(Tackle.Fit.DRONE, Tackle.BREACH_COUPLER);
        includes(f.rod(), "2230 units from your fleet", "drones follow the fleet");
        excludes(f.rod(), "No rupture in range.", "Turn off the breach lamps");
        f.scripts.add(new FishingDroneSwarmScript(null, null));
        includes(f.rod(), "Waiting for all drones to return.");
        excludes(f.rod(), "No rupture in range.");
    }

    private static final class Lamps extends SearchlightAbilityPlugin {

        private boolean on;

        @Override
        public boolean isActive() {
            return on;
        }
    }

    private static final class Fixture {

        private final Map<String, Object> data = new HashMap<>();
        private final List<EveryFrameScript> scripts = new ArrayList<>();
        private final UpgradeManager upgrades = new UpgradeManager();
        private final PondInteractionAbilityPlugin rod = new PondInteractionAbilityPlugin();
        private final HarpoonAbilityPlugin harpoon = new HarpoonAbilityPlugin();
        private final Lamps lamps = new Lamps();

        private Fixture() throws Exception {
            List<List<FishCsv.Cell>> rows = FishCsv.parse(Files.readString(Path.of("data/config/UpgradeData.csv")));
            for (List<FishCsv.Cell> row : rows.subList(1, rows.size())) {
                if (row.size() < 7 || row.get(0).value().isBlank()) continue;
                UpgradeStat stat = new UpgradeStat();
                stat.id = row.get(0).value();
                stat.baseValue = Double.parseDouble(row.get(1).value());
                stat.baseType = UpgradeStat.BaseType.valueOf(row.get(2).value());
                stat.increasePerLevel = Double.parseDouble(row.get(3).value());
                stat.upgradeType = UpgradeStat.UpgradeType.valueOf(row.get(4).value());
                stat.maxLevel = Integer.parseInt(row.get(5).value());
                upgrades.levelMap.put(stat.id, stat);
            }
            MemoryAPI memory = proxy(MemoryAPI.class, (self, method, args) -> switch (method.getName()) {
                case "contains" -> UpgradeManager.MEMORY_ID.equals(args[0]);
                case "get" -> upgrades;
                default -> throw new AssertionError(method);
            });
            LocationAPI location = proxy(LocationAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getEntitiesWithTag", "getCustomEntitiesWithTag", "getTerrainCopy" -> List.of();
                case "isHyperspace" -> false;
                default -> throw new AssertionError(method);
            });
            CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getContainingLocation" -> location;
                case "getAbility" -> lamps;
                case "getAbilities" -> Map.of();
                case "isInHyperspace" -> false;
                default -> throw new AssertionError(method);
            });
            SectorAPI sector = proxy(SectorAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getMemoryWithoutUpdate" -> memory;
                case "getPersistentData" -> data;
                case "getScripts" -> scripts;
                case "getPlayerFleet" -> fleet;
                case "getCurrentLocation" -> location;
                default -> throw new AssertionError(method);
            });
            AbilitySpecAPI spec = proxy(AbilitySpecAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getName" -> "Ability";
                default -> throw new AssertionError(method);
            });
            SettingsAPI settings = proxy(SettingsAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getColor" -> Color.YELLOW;
                case "getBoolean" -> false;
                case "getFloat" -> 1f;
                case "getInt" -> 1;
                case "getAbilitySpec" -> spec;
                default -> throw new AssertionError(method);
            });
            Global.setSector(sector);
            Global.setSettings(settings);
            rod.init("catchrelease_rod", fleet);
            harpoon.init(HarpoonAbilityPlugin.CHARGE_ID, fleet);
            lamps.init(SearchlightAbilityPlugin.ABILITY_ID, fleet);
        }

        private String rod() {
            List<String> lines = new ArrayList<>();
            rod.addTooltip(tooltip(lines));
            return String.join("\n", lines);
        }

        private String harpoon() {
            List<String> lines = new ArrayList<>();
            harpoon.addTooltip(tooltip(lines));
            return String.join("\n", lines);
        }

        private String lamps() {
            List<String> lines = new ArrayList<>();
            lamps.createTooltip(tooltip(lines), true);
            return String.join("\n", lines);
        }
    }

    private static TooltipMakerAPI tooltip(List<String> lines) {
        LabelAPI label = proxy(LabelAPI.class, (self, method, args) -> null);
        return proxy(TooltipMakerAPI.class, (self, method, args) -> {
            if (method.getName().equals("addSpacer")) return null;
            if (method.getName().equals("addTitle")) {
                lines.add("TITLE:" + args[0]);
                return label;
            }
            if (!method.getName().equals("addPara")) throw new AssertionError(method);
            String text = (String) args[0];
            if (args[args.length - 1] instanceof String[] highlights) {
                require(text.split("%s", -1).length - 1 == highlights.length, "Highlight count: " + text);
                text = String.format(Locale.ROOT, text, (Object[]) highlights);
                for (String highlight : highlights) require(text.contains(highlight), "Missing highlight");
            }
            require(!text.contains("%s") && !text.contains("NaN"), "Unresolved text: " + text);
            lines.add(text);
            return label;
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static void includes(String text, String... fragments) {
        for (String fragment : fragments) require(text.contains(fragment), "Missing: " + fragment + "\n" + text);
    }

    private static void excludes(String text, String... fragments) {
        for (String fragment : fragments) require(!text.contains(fragment), "Unexpected: " + fragment + "\n" + text);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
