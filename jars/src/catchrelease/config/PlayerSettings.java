package catchrelease.config;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.fisherman.FishermanMapIcon;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.jobs.fleet.FleetQuest;
import catchrelease.campaign.fish.jobs.fleet.FleetQuestMapIcon;
import catchrelease.campaign.fish.fisherman.chain.CaptainsFleet;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import lunalib.backend.ui.settings.LunaSettingsData;
import lunalib.backend.ui.settings.LunaSettingsLoader;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import org.json.JSONException;

import java.io.IOException;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static catchrelease.config.Setting.*;

public final class PlayerSettings implements LunaSettingsListener {

    private static final String STATUS_ID = "catchrelease_difficulty_status";
    private static final List<Setting> CATCH_PRESET_KEYS = List.of(
            BAR_SIZE, FISH_SPEED, CATCH_GAIN, CATCH_LOSS, START_PROGRESS, OPENING_GRACE,
            FAIL_DELAY, GAME_SPEED, COMMON_DIFFICULTY, UNCOMMON_DIFFICULTY, RARE_DIFFICULTY,
            EPIC_DIFFICULTY, LEGENDARY_DIFFICULTY, TELL_TIME, NO_ESCAPE,
            BAR_LIFT, BAR_FALL, BAR_BOUNCE, SHOW_TELLS, TREASURE_SAFE,
            TREASURE_LIFE, TREASURE_HOLD, TREASURE_DECAY);
    private static final List<Setting> HUNT_PRESET_KEYS = List.of(
            HUNT_SPEED, HAZARD_FREQUENCY, HAZARD_DAMAGE, HUNT_RECOVERY, HUNT_GRACE,
            SCAR_HELP, SCAR_QUALITY, DAWN_MINE_LIFE, DAWN_MINE_FREQUENCY, JACK_CALL_FREQUENCY,
            JACK_SHIELD_LIMIT, LONGLINER_ROTATION, LONGLINER_DECOYS, MORAY_DURATION);

    private static Map<Setting, Object> current = defaults();
    private static final Map<String, Float> barWeights = new HashMap<>();

    public static void install() {
        if (!LunaSettings.hasSettingsListenerOfClass(PlayerSettings.class)) {
            LunaSettings.addSettingsListener(new PlayerSettings());
        }
        reload();
    }

    public static Object get(Setting setting) {
        return current.getOrDefault(setting, setting.fallback);
    }

    public static Map<Setting, Object> snapshot() {
        return new EnumMap<>(current);
    }

    public static Map<Setting, Object> defaults() {
        Map<Setting, Object> result = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) result.put(setting, setting.fallback);
        return result;
    }

    @Override
    public void settingsChanged(String modId) {
        if (ModPlugin.MOD_ID.equals(modId)) reload();
    }

    public static void reload() {
        // Luna initializes lazily; never reload another mod's settings.
        LunaSettings.getString(ModPlugin.MOD_ID, PRESET.id);
        var data = LunaSettingsLoader.getSettings().get(ModPlugin.MOD_ID);
        if (data == null) return;
        Map<Setting, Object> requested = new EnumMap<>(Setting.class);
        for (Setting key : Setting.values()) requested.put(key, data.opt(key.id));
        current = resolve(requested);
        try {
            boolean changed = false;
            for (Setting key : Setting.values()) {
                Object value = current.get(key);
                if (value.equals(data.opt(key.id))) continue;
                data.put(key.id, value);
                changed = true;
            }
            if (changed) data.save();
        } catch (IOException | JSONException ex) {
            Global.getLogger(PlayerSettings.class).error("Could not save fishing settings", ex);
        }
        updateStatus();
        LunaSettingsBridge.sync(current);
        applyCampaignPreferences();
    }

    private static void applyCampaignPreferences() {
        for (var spec : Global.getSettings().getAllBarEventSpecs()) {
            if (!spec.getId().startsWith("catchrelease_")) continue;
            float base = barWeights.computeIfAbsent(spec.getId(), id -> spec.getFreq());
            spec.setFreq(base * BAR_FREQUENCY.mult());
        }
        if (Global.getSector() == null) return;
        QuestPond.refreshMarkers();
        CaptainsFleet.syncMapIcon();
        CaptainsFleet.syncMarker();
        for (var location : Global.getSector().getAllLocations()) {
            for (var fleet : location.getFleets()) {
                Object ref = fleet.getMemoryWithoutUpdate().get(FishJob.REF_KEY);
                if (ref instanceof FleetQuest quest) {
                    quest.ensureMarked();
                    FleetQuestMapIcon.findOrAdd(fleet);
                }
            }
            if (location instanceof StarSystemAPI system) {
                var marker = FishermanMapIcon.findStanding(system);
                if (marker != null) {
                    ((FishermanMapIcon) marker.getCustomPlugin()).syncVisibility();
                }
            }
            for (var token : new ArrayList<>(location.getCustomEntities())) {
                if (token.getCustomPlugin() instanceof FishermanMapIcon icon) {
                    icon.syncVisibility();
                }
            }
        }
    }

    public static Map<Setting, Object> resolve(Map<Setting, Object> requested) {
        Map<Setting, Object> result = defaults();
        for (Setting key : Setting.values()) result.put(key, key.sanitize(requested.get(key)));
        applyPreset(result, (String) result.get(PRESET), true);
        applyPreset(result, (String) result.get(HUNT_PRESET), false);
        switch ((String) result.get(MARKER_PRESET)) {
            case "All" -> setMarkers(result, true, true, true, true);
            case "Accepted quests only" -> setMarkers(result, false, true, false, true);
            case "None" -> setMarkers(result, false, false, false, false);
        }
        result.put(PRESET, "Keep current");
        result.put(HUNT_PRESET, "Keep current");
        result.put(MARKER_PRESET, "Keep current");
        return result;
    }

    private static void setMarkers(Map<Setting, Object> values, boolean offers, boolean accepted,
                                   boolean fisherman, boolean ponds) {
        values.put(OFFER_MARKERS, offers);
        values.put(ACCEPTED_MARKERS, accepted);
        values.put(FISHERMAN_MARKERS, fisherman);
        values.put(POND_MARKERS, ponds);
    }

    public static void applyPreset(Map<Setting, Object> values, String preset, boolean catching) {
        if ("Keep current".equals(preset)) return;
        for (Setting key : HUNT_PRESET_KEYS) values.put(key, key.fallback);
        if (catching) for (Setting key : CATCH_PRESET_KEYS) values.put(key, key.fallback);
        switch (preset) {
            case "Relaxed" -> {
                put(values, HUNT_PRESET_KEYS, 60, 50, 35, 175, 200);
                if (catching) {
                    put(values, List.of(BAR_SIZE, FISH_SPEED, CATCH_GAIN, CATCH_LOSS,
                            START_PROGRESS, OPENING_GRACE, FAIL_DELAY, GAME_SPEED, TELL_TIME),
                            160, 65, 160, 35, 55, 2, 3, 85, 200);
                }
            }
            case "Easy" -> {
                put(values, HUNT_PRESET_KEYS, 80, 75, 65, 125, 150);
                if (catching) {
                    put(values, List.of(BAR_SIZE, FISH_SPEED, CATCH_GAIN, CATCH_LOSS,
                            START_PROGRESS, OPENING_GRACE, FAIL_DELAY, TELL_TIME),
                            130, 85, 125, 65, 45, 1, 1.5, 150);
                }
            }
            case "Hard" -> {
                put(values, HUNT_PRESET_KEYS, 115, 125, 125, 85, 80);
                if (catching) {
                    put(values, List.of(BAR_SIZE, FISH_SPEED, CATCH_GAIN, CATCH_LOSS),
                            85, 115, 90, 125);
                }
            }
        }
    }

    private static void put(Map<Setting, Object> values, List<Setting> keys, double... numbers) {
        for (int i = 0; i < numbers.length; i++) values.put(keys.get(i), keys.get(i).sanitize(numbers[i]));
    }

    public static String difficulty(Map<Setting, Object> values) {
        for (String name : List.of("Standard", "Relaxed", "Easy", "Hard")) {
            Map<Setting, Object> preset = defaults();
            applyPreset(preset, name, true);
            boolean matches = true;
            for (Setting key : CATCH_PRESET_KEYS) matches &= preset.get(key).equals(values.get(key));
            for (Setting key : HUNT_PRESET_KEYS) matches &= preset.get(key).equals(values.get(key));
            if (matches) return name;
        }
        return "Custom";
    }

    private static void updateStatus() {
        var rows = LunaSettingsLoader.getSettingsData();
        for (int i = 0; i < rows.size(); i++) {
            LunaSettingsData row = rows.get(i);
            if (!ModPlugin.MOD_ID.equals(row.getModID()) || !STATUS_ID.equals(row.getFieldID())) continue;
            rows.set(i, new LunaSettingsData(row.getModID(), row.getFieldID(), row.getFieldName(),
                    row.getFieldType(), row.getFieldDescription(), "Saved difficulty: " + difficulty(current) + " (reopen this tab after saving)",
                    row.getSecondaryValue(), row.getMinValue(), row.getMaxValue(), row.getTab()));
            return;
        }
    }

    public static float effects() {
        return REDUCED_EFFECTS.enabled() ? 0.25f : 1f;
    }

    public static float flashStrength() {
        return REDUCED_FLASHES.enabled() || REDUCED_EFFECTS.enabled() ? 0.15f : 1f;
    }

    public static boolean rumorNotices() {
        return !IMPORTANT_NOTIFICATIONS.enabled() && RUMOR_NOTICES.enabled();
    }

    public static float soundVolume(String soundId) {
        if (soundId == null || !soundId.startsWith("catchrelease")) return 1f;
        if (!LINE_CLICK.enabled() && soundId.contains("line_click")) return 0f;
        if (!INDICATOR_SOUND.enabled() && soundId.contains("bar_click")) return 0f;
        if (!TREASURE_SOUND.enabled() && (soundId.contains("treasure") || soundId.contains("chest_open"))) return 0f;
        Setting group = soundId.contains("minigame") ? MINIGAME_VOLUME
                : soundId.contains("haunt") || soundId.contains("legendary") || soundId.contains("coherence") || soundId.contains("longliner") ? LEGENDARY_VOLUME
                : EQUIPMENT_VOLUME;
        return SOUND_VOLUME.mult() * group.mult();
    }

    private PlayerSettings() {
    }
}
