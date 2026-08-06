package catchrelease.helper.loading;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.memory.TransientMemory;
import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads data/campaign/fish.csv into {@link FishSpec}s, keyed by id, and caches the result for the
 * session. Same shape as {@link UpgradeStatLoader} - merged across mods, so another mod can add its
 * own fish or override ours by repeating the id.
 */
public class FishSpecLoader {

    public static final String PATH = "data/campaign/fish.csv";

    public static Map<String, FishSpec> getFishSpecsFromMemory() {
        String memKey = "$" + ModPlugin.MOD_ID + "_" + PATH;

        TransientMemory transientMemory = TransientMemory.getInstance();
        if (transientMemory.contains(memKey)) {
            return (Map<String, FishSpec>) transientMemory.get(memKey);
        }

        Map<String, FishSpec> out = new LinkedHashMap<>();

        try {
            JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", PATH, ModPlugin.MOD_ID);
            for (int i = 0; i < config.length(); i++) {
                JSONObject row = config.getJSONObject(i);

                FishSpec spec = parseRow(row);
                if (spec != null) {
                    out.put(spec.id, spec);
                }
            }
        } catch (IOException | JSONException ex) {
            Global.getLogger(FishSpecLoader.class).error("Failed to load " + PATH, ex);
        }

        transientMemory.set(memKey, out);
        return out;
    }

    public static FishSpec getFishSpec(String id) {
        return getFishSpecsFromMemory().get(id);
    }

    public static List<FishSpec> getAllFishSpecs() {
        return new ArrayList<>(getFishSpecsFromMemory().values());
    }

    private static FishSpec parseRow(JSONObject row) throws JSONException {
        if (row == null) return null;

        FishSpec s = new FishSpec();

        s.id = optString(row, "id", null);
        if (s.id == null || s.id.isBlank()) return null;

        s.name = optString(row, "name", "");
        s.icon = optString(row, "icon", "");
        s.desc = optString(row, "desc", "");

        s.tags = parseList(optString(row, "tags", ""));
        s.rarity = FishRarity.parse(optString(row, "rarity", null), FishRarity.COMMON);
        s.spawnWeight = optFloat(row, "spawnWeight", 10f);

        s.motion = FishMotion.parse(optString(row, "motion", null), FishMotion.SMOOTH);
        s.motionSpeed = optFloat(row, "motionSpeed", 1f);
        s.restlessness = optFloat(row, "restlessness", 1f);
        s.jitter = optFloat(row, "jitter", 1f);
        s.spriteDirection = optFloat(row, "spriteDirection", 180f);
        s.difficulty = optFloat(row, "difficulty", 50f);
        s.progressRateMult = optFloat(row, "progressRateMult", 1f);
        s.escapeRateMult = optFloat(row, "escapeRateMult", 1f);

        s.baseValue = optFloat(row, "baseValue", 100f);
        s.lengthMin = optFloat(row, "lengthMin", 0.3f);
        s.lengthMax = optFloat(row, "lengthMax", 0.6f);
        s.weightMin = optFloat(row, "weightMin", 0.5f);
        s.weightMax = optFloat(row, "weightMax", 2f);

        s.starTypes = parseList(optString(row, "starTypes", ""));
        s.systemTags = parseList(optString(row, "systemTags", ""));
        s.regions = parseRegions(optString(row, "regions", ""));

        return s;
    }

    /** Comma separated cell to a set, empties dropped. */
    private static Set<String> parseList(String value) {
        Set<String> out = new LinkedHashSet<>();
        if (value == null) return out;

        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }

        return out;
    }

    private static Set<SectorRegion> parseRegions(String value) {
        Set<SectorRegion> out = new LinkedHashSet<>();

        for (String name : parseList(value)) {
            SectorRegion region = SectorRegion.parse(name);

            if (region == null) {
                Global.getLogger(FishSpecLoader.class).warn("Unknown region '" + name + "' in " + PATH);
                continue;
            }

            out.add(region);
        }

        return out;
    }

    private static String optString(JSONObject row, String key, String def) {
        if (!row.has(key) || row.isNull(key)) return def;
        String v = row.optString(key, def);
        if (v == null) return def;
        v = v.trim();
        return v.isEmpty() ? def : v;
    }

    private static float optFloat(JSONObject row, String key, float def) {
        if (!row.has(key) || row.isNull(key)) return def;
        String s = row.optString(key, null);
        if (s == null) return def;
        s = s.trim();
        if (s.isEmpty()) return def;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
