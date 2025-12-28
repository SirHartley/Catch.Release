package catchrelease.helper.loading;

import catchrelease.ModPlugin;
import catchrelease.memory.TransientMemory;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class UpgradeStatLoader {

    public static final String PATH = "data/config/UpgradeData.csv";

    public static Map<String, UpgradeStat> getUpgradeStatsFromMemory() {
        String memKey = "$" + ModPlugin.MOD_ID + "_" + PATH;

        TransientMemory transientMemory = TransientMemory.getInstance();
        if (transientMemory.contains(memKey)) {
            return (Map<String, UpgradeStat>) transientMemory.get(memKey);
        }

        Map<String, UpgradeStat> out = new LinkedHashMap<>();

        try {
            JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", PATH, ModPlugin.MOD_ID);
            for (int i = 0; i < config.length(); i++) {
                JSONObject row = config.getJSONObject(i);

                UpgradeStat stat = parseRow(row);
                if (stat != null) {
                    out.put(stat.id, stat);
                }
            }
        } catch (IOException | JSONException ex) {
            Global.getLogger(UpgradeStatLoader.class).error("Failed to load " + PATH, ex);
        }

        transientMemory.set(memKey, out);
        return out;
    }

    public static UpgradeStat getUpgradeStat(String id) {
        return getUpgradeStatsFromMemory().get(id);
    }

    private static UpgradeStat parseRow(JSONObject row) throws JSONException {
        if (row == null) return null;

        UpgradeStat s = new UpgradeStat();

        s.id = optString(row, "id", null);
        if (s.id == null || s.id.isBlank()) return null;

        s.baseValue = optDouble(row, "baseValue", 0d);
        s.baseType = parseEnum(optString(row, "baseType", "DOUBLE"),
                UpgradeStat.BaseType.class, UpgradeStat.BaseType.DOUBLE);

        s.increasePerLevel = optDouble(row, "increasePerLevel", 0d);
        s.upgradeType = parseEnum(optString(row, "upgradeType", "FLAT"),
                UpgradeStat.UpgradeType.class, UpgradeStat.UpgradeType.FLAT);

        s.maxLevel = optInt(row, "maxLevel", 0);
        s.description = optString(row, "description", "");

        return s;
    }

    private static String optString(JSONObject row, String key, String def) {
        if (!row.has(key) || row.isNull(key)) return def;
        String v = row.optString(key, def);
        if (v == null) return def;
        v = v.trim();
        return v.isEmpty() ? def : v;
    }

    private static double optDouble(JSONObject row, String key, double def) {
        if (!row.has(key) || row.isNull(key)) return def;
        String s = row.optString(key, null);
        if (s == null) return def;
        s = s.trim();
        if (s.isEmpty()) return def;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int optInt(JSONObject row, String key, int def) {
        if (!row.has(key) || row.isNull(key)) return def;
        String s = row.optString(key, null);
        if (s == null) return def;
        s = s.trim();
        if (s.isEmpty()) return def;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static <T extends Enum<T>> T parseEnum(String raw, Class<T> enumClass, T def) {
        if (raw == null) return def;
        String v = raw.trim();
        if (v.isEmpty()) return def;
        try {
            return Enum.valueOf(enumClass, v.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
