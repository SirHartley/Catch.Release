package catchrelease.tools;

import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import catchrelease.testing.DevShortcut;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.input.InputEventAPI;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class DevShortcutChecks {

    private static final String STEP_KEY = "$catchrelease_devSkip";
    private static int checks;

    private static final class Key {

        boolean consumed;
        final InputEventAPI event;

        Key(char key, boolean down) {
            event = proxy(InputEventAPI.class, (p, m, a) -> switch (m.getName()) {
                case "isConsumed" -> consumed;
                case "isKeyDownEvent" -> down;
                case "getEventChar" -> key;
                case "consume" -> { consumed = true; yield null; }
                default -> throw new AssertionError(m);
            });
        }
    }

    private static final class Shortcut extends DevShortcut {

        int grants;

        @Override protected void addEquipment() { grants++; }
        @Override protected void addBackgrounds() { grants++; }
        @Override protected void addSchematics() { grants++; }
        @Override protected void addUpgrades() { grants++; super.addUpgrades(); }
    }

    private static final class Fixture {

        final UpgradeManager upgrades = new UpgradeManager();
        final Map<String, Object> memory = new HashMap<>();
        final Map<String, Object> data = new HashMap<>();
        final Map<String, Boolean> active = new HashMap<>();
        int stops;
        boolean dev = true;

        Fixture() throws Exception {
            List<List<FishCsv.Cell>> rows = FishCsv.parse(Files.readString(Path.of("data/config/UpgradeData.csv")));
            for (List<FishCsv.Cell> row : rows.subList(1, rows.size())) {
                if (row.size() < 6 || row.get(0).value().isBlank()) continue;
                UpgradeStat stat = new UpgradeStat();
                stat.id = row.get(0).value();
                stat.maxLevel = Integer.parseInt(row.get(5).value());
                stat.level = switch (upgrades.levelMap.size() % 3) {
                    case 0 -> 0;
                    case 1 -> 1;
                    default -> stat.maxLevel;
                };
                upgrades.levelMap.put(stat.id, stat);
            }
            memory.put(UpgradeManager.MEMORY_ID, upgrades);
            MemoryAPI mem = proxy(MemoryAPI.class, (p, m, a) -> switch (m.getName()) {
                case "contains" -> memory.containsKey(a[0]);
                case "get", "getInt" -> memory.get(a[0]);
                case "set" -> { memory.put((String) a[0], a[1]); yield null; }
                default -> throw new AssertionError(m);
            });
            Map<String, AbilityPlugin> abilities = new HashMap<>();
            for (String id : List.of(StatIds.ROD_ABILITY, StatIds.HARPOON_ABILITY, StatIds.LAMPS_ABILITY)) {
                active.put(id, true);
                abilities.put(id, proxy(AbilityPlugin.class, (p, m, a) -> switch (m.getName()) {
                    case "isActiveOrInProgress" -> active.get(id);
                    case "deactivate" -> { active.put(id, false); stops++; yield null; }
                    default -> throw new AssertionError(m);
                }));
            }
            CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getAbility" -> abilities.get(a[0]);
                default -> throw new AssertionError(m);
            });
            Global.setSector(proxy(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getMemoryWithoutUpdate" -> mem;
                case "getPersistentData" -> data;
                case "getPlayerFleet" -> fleet;
                default -> throw new AssertionError(m);
            }));
            Global.setSettings(proxy(SettingsAPI.class, (p, m, a) -> switch (m.getName()) {
                case "isDevMode" -> dev;
                default -> throw new AssertionError(m);
            }));
        }
    }

    public static void main(String[] args) throws Exception {
        Fixture f = new Fixture();
        Shortcut shortcut = new Shortcut();
        f.dev = false;
        Key disabled = new Key('j', true);
        shortcut.processCampaignInputPreCore(List.of(disabled.event));
        check(!disabled.consumed && f.memory.get(STEP_KEY) == null, "dev-mode gate");
        f.dev = true;
        for (Key ignored : List.of(new Key('j', false), new Key('x', true))) {
            shortcut.processCampaignInputPreCore(List.of(ignored.event));
            check(!ignored.consumed && shortcut.grants == 0, "ignore unrelated input");
        }
        Key consumed = new Key('j', true);
        consumed.consumed = true;
        shortcut.processCampaignInputPreCore(List.of(consumed.event));
        check(shortcut.grants == 0, "ignore consumed keys");

        Map<String, Integer> before = new HashMap<>();
        f.upgrades.getAll().forEach((id, stat) -> before.put(id, stat.level));
        for (int step = 1; step <= 3; step++) {
            Key key = new Key('J', true);
            shortcut.processCampaignInputPreCore(List.of(key.event));
            check(key.consumed && (int) f.memory.get(STEP_KEY) == step && shortcut.grants == step,
                    "one stage per press: " + step);
        }
        f.upgrades.getAll().forEach((id, stat) -> check(stat.level == before.get(id), "no early tier grant"));
        for (UpgradeStat stat : f.upgrades.getAll().values()) {
            for (int rung = stat.level + 1; rung <= stat.maxLevel; rung++) {
                ShopMarks.mark(ShopMarks.getUpgradeMarkKey(stat.id, rung));
            }
        }
        Object fitted = Map.of("test-rig", "test-module");
        f.data.put(TackleManager.KEY, fitted);
        Shortcut reloaded = new Shortcut();
        Key fourth = new Key('j', true);
        Key extra = new Key('j', true);
        reloaded.processCampaignInputPreCore(List.of(fourth.event, extra.event));
        check(fourth.consumed && !extra.consumed && reloaded.grants == 1, "saved third stage resumes at fourth");
        check((int) f.memory.get(STEP_KEY) == 4, "fourth stage persists");
        for (UpgradeStat stat : f.upgrades.getAll().values()) {
            check(stat.level == stat.maxLevel, "maxed " + stat.id);
            check(ShopSchematics.has(stat, stat.maxLevel), "unlocked " + stat.id);
            for (int rung = before.get(stat.id) + 1; rung <= stat.maxLevel; rung++) {
                if (ShopSchematics.requires(stat, rung)) {
                    check(((Set<?>) f.data.get(ShopSchematics.KEY)).contains(ShopSchematics.getKey(stat.id, rung)),
                            "schematic recorded for " + stat.id + ":" + rung);
                }
            }
        }
        check(ShopMarks.getMarkedKeys().isEmpty(), "all granted rung marks removed");
        check(((Set<?>) f.data.get(ShopSchematics.FRESH_KEY)).isEmpty(), "granted tiers are not marked new");
        check(f.stops == 3, "active rigs deactivated through the normal grant path");
        check(f.data.get(TackleManager.KEY) == fitted, "fitted modules unchanged");
        reloaded.processCampaignInputPreCore(List.of(extra.event));
        check(reloaded.grants == 1 && f.stops == 3, "later presses do not repeat grants");
        System.out.println("Dev shortcut checks passed: " + checks);
    }

    private static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
}
