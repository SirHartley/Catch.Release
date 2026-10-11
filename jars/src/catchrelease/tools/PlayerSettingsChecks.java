package catchrelease.tools;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.jobs.QuestDuration;
import catchrelease.campaign.fish.minigame.FishingMinigame;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.config.PlayerSettings;
import catchrelease.config.Setting;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import lunalib.backend.ui.settings.ChangedSetting;
import lunalib.backend.ui.settings.LunaSettingsUISettingsPanel;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static catchrelease.config.Setting.*;

public final class PlayerSettingsChecks {

    private static int checks;

    private static class Game extends FishingMinigame {

        Game(FishSpec spec, StarSystemAPI location) {
            super(spec, Tackle.NONE, location);
        }

        @Override
        protected void rollTreasure() {
        }

        void progress(float progress) {
            this.progress = progress;
        }

        void tick(float seconds, boolean covered) {
            float amount = seconds * multiplier(GAME_SPEED);
            timeTotal += amount;
            barPosition = covered ? fishPosition - barHeight * 0.5f : -1f;
            advanceProgress(amount);
        }

        void treasure(boolean collecting) {
            collectingTreasure = collecting;
        }

        float zeroTime() {
            return zeroProgressTime;
        }

        float motionSpeed() {
            return motionSpeed;
        }
    }

    public static void main(String[] args) throws Exception {
        check(!RuntimeException.class.isAssignableFrom(JSONException.class),
                "use Starsector's json.jar; newer JSON jars hide checked-exception build errors");
        csv();
        lunaIcon();
        presets();
        retainedLunaValues();
        try (FishingParityChecks.Environment env = new FishingParityChecks.Environment()) {
            timing(env);
            assists(env);
        } finally {
            use(PlayerSettings.defaults());
        }
        deadlines();
        System.out.println("Player settings checks passed: " + checks);
    }

    static void use(Map<Setting, Object> values) throws Exception {
        Field current = PlayerSettings.class.getDeclaredField("current");
        current.setAccessible(true);
        current.set(null, PlayerSettings.resolve(values));
    }

    private static void retainedLunaValues() throws Exception {
        var pending = LunaSettingsUISettingsPanel.Companion.getChangedSettings();
        var previous = List.copyOf(pending);
        var sync = Class.forName("catchrelease.config.LunaSettingsBridge")
                .getDeclaredMethod("sync", Map.class);
        sync.setAccessible(true);
        try {
            pending.clear();
            var action = new ChangedSetting("catchrelease", PRESET.id, "Relaxed");
            var slider = new ChangedSetting("catchrelease", BAR_SIZE.id, 100d);
            var foreign = new ChangedSetting("another_mod", BAR_SIZE.id, 77d);
            pending.addAll(List.of(action, slider, foreign));
            var requested = PlayerSettings.defaults();
            requested.put(PRESET, "Relaxed");
            var saved = PlayerSettings.resolve(requested);
            sync.invoke(null, saved);
            check(action.getData().equals("Keep current"), "clear retained preset action");
            check(slider.getData().equals(saved.get(BAR_SIZE)), "sync retained slider");
            check(foreign.getData().equals(77d), "preserve another mod's pending value");
            saved.put(BAR_SIZE, 117d);
            sync.invoke(null, saved);
            check(slider.getData().equals(117d), "later edit survives another save");
            check(action.getData().equals("Keep current"), "preset stays cleared");
        } finally {
            pending.clear();
            pending.addAll(previous);
        }
    }

    private static void csv() throws Exception {
        List<List<FishCsv.Cell>> rows = FishCsv.parse(Files.readString(Path.of("data/config/LunaSettings.csv")));
        Map<String, List<FishCsv.Cell>> byId = new HashMap<>();
        Set<String> tabs = new HashSet<>();
        for (var row : rows.subList(1, rows.size())) {
            if (row.size() == 1 && row.get(0).value().isBlank()) continue;
            check(row.size() == 9, "nine Luna columns");
            check(byId.put(row.get(0).value(), row) == null, "unique setting ID");
            tabs.add(row.get(8).value());
            String id = row.get(0).value();
            switch (row.get(2).value()) {
                case "Header" -> { }
                case "Text" -> lunaParagraph(row.get(3).value(), id + " defaultValue");
                default -> {
                    lunaParagraph(row.get(1).value(), id + " fieldName");
                    lunaParagraph(row.get(5).value(), id + " fieldDescription");
                }
            }
        }
        check(tabs.size() == 9, "nine feature tabs");
        for (Setting setting : Setting.values()) {
            var row = byId.get(setting.id);
            check(row != null, "registered " + setting);
            check(!row.get(5).value().isBlank(), "tooltip " + setting);
            String value = row.get(3).value();
            if (setting.fallback instanceof Number number) {
                check(Double.parseDouble(value) == number.doubleValue(), "numeric default " + setting);
                check(Double.parseDouble(row.get(6).value()) == setting.min, "minimum " + setting);
                check(Double.parseDouble(row.get(7).value()) == setting.max, "maximum " + setting);
                check(setting.sanitize(Double.NaN).equals(setting.fallback), "reject NaN");
                check(setting.sanitize(Double.POSITIVE_INFINITY).equals(setting.fallback), "reject infinity");
                check(setting.sanitize(-1e6).equals(setting.min), "clamp minimum");
                check(setting.sanitize(1e6).equals(setting.max), "clamp maximum");
            } else {
                check(value.equals(setting.fallback.toString()), "default " + setting);
                check(setting.choices.equals(row.get(4).value()), "radio choices " + setting);
            }
            check(setting.sanitize(null).equals(setting.fallback), "missing value " + setting);
            check(setting.sanitize(new Object()).equals(setting.fallback), "invalid value " + setting);
        }
        check(!byId.containsKey("catchrelease_restock_time"), "no restock setting");
        for (String legacy : List.of("catchrelease_harpoonChargeReloadSound",
                "catchrelease_pondViewportSnap", "catchrelease_skipTutorialCompleted")) {
            check(byId.containsKey(legacy), "preserved existing setting " + legacy);
        }
        check(START_PROGRESS.value() / 100f == FishConstants.MINIGAME_PROGRESS_START, "default starting progress");
    }

    private static void lunaParagraph(String text, String field) {
        try {
            // Luna's coloured addPara overload formats even with no highlight arguments.
            check(String.format(text).equals(text.replace("%%", "%")),
                    "Luna paragraph preserves literal text: " + field);
        } catch (IllegalFormatException ex) {
            throw new AssertionError("Invalid Luna paragraph format: " + field, ex);
        }
    }

    private static void lunaIcon() throws Exception {
        String modId = new JSONObject(Files.readString(Path.of("mod_info.json"))).getString("id");
        JSONObject configs = new JSONObject(Files.readString(Path.of("data/config/LunaSettingsConfig.json")));
        Path icon = Path.of(configs.getJSONObject(modId).getString("iconPath"));
        check(Files.isRegularFile(icon), "registered Luna mod icon exists");
        var image = ImageIO.read(icon.toFile());
        check(image != null && image.getWidth() == image.getHeight(),
                "Luna mod icon is a readable square image");
    }

    private static void presets() {
        for (String preset : List.of("Relaxed", "Easy", "Standard", "Hard")) {
            Map<Setting, Object> values = PlayerSettings.defaults();
            values.put(PRESET, preset);
            values.put(OFFER_MARKERS, false);
            values.put(SOUND_VOLUME, 37d);
            values.put(TOGGLE_LIFT, true);
            values.put(QUEST_CREDITS, 220d);
            values = PlayerSettings.resolve(values);
            check(preset.equals(PlayerSettings.difficulty(values)), "preset status " + preset);
            check(values.equals(PlayerSettings.resolve(values)), "preset applies once " + preset);
            check(values.get(SOUND_VOLUME).equals(37d) && values.get(OFFER_MARKERS).equals(false)
                    && values.get(TOGGLE_LIFT).equals(true) && values.get(QUEST_CREDITS).equals(220d),
                    "difficulty preserves sound, markers, input and economy");
            values.put(BAR_LIFT, 117d);
            check("Custom".equals(PlayerSettings.difficulty(values)), "individual physics produces Custom");
            values.put(PRESET, "Keep current");
            check(PlayerSettings.resolve(values).get(BAR_LIFT).equals(117d), "Custom remains edited");
        }
        Map<Setting, Object> values = PlayerSettings.defaults();
        values.put(BAR_SIZE, 123d);
        values.put(HUNT_PRESET, "Relaxed");
        values = PlayerSettings.resolve(values);
        check(values.get(BAR_SIZE).equals(123d), "hunt preset leaves catch sliders alone");
        values.put(MARKER_PRESET, "None");
        values = PlayerSettings.resolve(values);
        check(values.get(POND_MARKERS).equals(false) && values.get(ACCEPTED_MARKERS).equals(false),
                "marker shortcut updates canonical fields");
        values.put(MARKER_PRESET, "Accepted quests only");
        values = PlayerSettings.resolve(values);
        check(values.get(POND_MARKERS).equals(true) && values.get(ACCEPTED_MARKERS).equals(true)
                && values.get(FISHERMAN_MARKERS).equals(false) && values.get(OFFER_MARKERS).equals(false),
                "accepted-only marker shortcut");
    }

    private static void timing(FishingParityChecks.Environment env) throws Exception {
        FishSpec fish = new FishSpec();
        fish.id = "settings_timing";
        for (int fps : new int[]{30, 60, 144}) {
            for (double speed : new double[]{25, 100, 150}) {
                Map<Setting, Object> values = PlayerSettings.defaults();
                values.put(FAIL_DELAY, 2d);
                values.put(GAME_SPEED, speed);
                use(values);
                Game game = new Game(fish, env.system);
                game.progress(0f);
                for (int i = 0; i < fps * 2 - 1; i++) game.tick(1f / fps, false);
                check(game.isRunning(), "no early failure at " + fps + " fps / " + speed);
                game.tick(2f / fps, false);
                check(game.getState() == FishingMinigame.State.ESCAPED, "fails after two real seconds");

                game.restart();
                game.progress(0f);
                game.tick(1f, false);
                game.tick(0.1f, true);
                check(game.zeroTime() == 0f, "recovery resets zero timer");
                game.progress(0f);
                game.tick(1.5f, false);
                check(game.isRunning(), "recovered timer is not cumulative");
                float timer = game.zeroTime();
                game.advance(0f, false);
                check(game.zeroTime() == timer, "pause does not count");
                game.restart();
                check(game.zeroTime() == 0f && game.getProgress() == START_PROGRESS.value() / 100f,
                        "restart clears timer and restores configured progress");

                values.put(OPENING_GRACE, 2d);
                use(values);
                game = new Game(fish, env.system);
                float before = game.getProgress();
                game.tick(1.5f, false);
                check(game.getProgress() == before, "opening grace uses real seconds");
                game.tick(0.75f, false);
                check(game.getProgress() < before, "opening grace expires");

                values.put(NO_ESCAPE, true);
                use(values);
                game = new Game(fish, env.system);
                game.progress(0f);
                game.tick(30f, false);
                check(game.isRunning() && game.getProgress() > 0f, "no-loss overrides both timers");
            }
        }
        use(PlayerSettings.defaults());
        Game instant = new Game(fish, env.system);
        instant.progress(0.0001f);
        instant.tick(1f, false);
        check(instant.getState() == FishingMinigame.State.ESCAPED, "default zero delay preserves immediate failure");

        Map<Setting, Object> values = PlayerSettings.defaults();
        values.put(FAIL_DELAY, 1d);
        use(values);
        Game crossing = new Game(fish, env.system);
        crossing.progress(FishConstants.MINIGAME_ESCAPE_RATE * 0.5f);
        crossing.tick(1f, false);
        check(crossing.isRunning() && Math.abs(crossing.zeroTime() - 0.5f) < 0.00001f,
                "only the part of a frame after reaching zero counts");
    }

    private static void assists(FishingParityChecks.Environment env) throws Exception {
        use(PlayerSettings.defaults());
        FishSpec fish = new FishSpec();
        fish.id = "settings_assists";
        fish.rarity = FishRarity.RARE;
        Game normal = new Game(fish, env.system);
        normal.progress(0.5f);
        normal.tick(0.1f, true);
        float gain = normal.getProgress() - 0.5f;
        normal.progress(0.5f);
        normal.tick(0.1f, false);
        float loss = 0.5f - normal.getProgress();

        Map<Setting, Object> values = PlayerSettings.defaults();
        values.put(CATCH_GAIN, 200d);
        values.put(CATCH_LOSS, 50d);
        values.put(RARE_DIFFICULTY, 50d);
        values.put(TREASURE_SAFE, true);
        values.put(START_PROGRESS, 63d);
        use(values);
        Game easy = new Game(fish, env.system);
        check(easy.getProgress() == 0.63f, "configured start");
        check(easy.motionSpeed() == normal.motionSpeed() * 0.5f, "rarity scales movement");
        easy.progress(0.5f);
        easy.tick(0.1f, true);
        check(Math.abs(easy.getProgress() - 0.5f - 4f * gain) < 0.00001f, "rarity and gain stack after compression");
        easy.progress(0.5f);
        easy.tick(0.1f, false);
        check(Math.abs(0.5f - easy.getProgress() - 0.25f * loss) < 0.00001f, "rarity and drain stack");
        easy.progress(0.5f);
        easy.treasure(true);
        easy.tick(1f, false);
        check(easy.getProgress() == 0.5f, "treasure protection");
        values.put(CATCH_GAIN, 100d);
        use(values);
        easy.progress(0.5f);
        easy.tick(0.1f, true);
        check(Math.abs(easy.getProgress() - 0.5f - 4f * gain) < 0.00001f, "attempt keeps settings snapshot");

        values.put(HARPOON_RANGE, 200d);
        use(values);
        check(UpgradeManager.getValue("harpoon_reach", 100f) == 200f, "equipment modifier applies to base stat");
        use(PlayerSettings.defaults());
    }

    private static void deadlines() throws Exception {
        use(PlayerSettings.defaults());
        float standard = QuestDuration.daysForTravelLY(0f, List.of());
        Map<Setting, Object> values = PlayerSettings.defaults();
        values.put(QUEST_TIME, 250d);
        use(values);
        check(QuestDuration.daysForTravelLY(0f, List.of()) == standard * 2.5f, "new deadline multiplier");
        values.put(QUEST_NO_DEADLINE, true);
        use(values);
        check(QuestDuration.daysForTravelLY(0f, List.of()) == 0f, "unlimited ordinary jobs");
        use(PlayerSettings.defaults());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private PlayerSettingsChecks() {
    }
}
