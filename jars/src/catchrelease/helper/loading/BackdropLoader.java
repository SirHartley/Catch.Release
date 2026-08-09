package catchrelease.helper.loading;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.memory.TransientMemory;
import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads data/campaign/backdrops.csv into {@link Backdrop}s, keyed by id, cached for the session.
 * Same shape as {@link FishSpecLoader}: merged across mods, so another mod can add its own scenes
 * or replace one of ours by repeating the id.
 * <p>
 * A row whose art is missing is still a row. The picker says so and the tank falls back to its own
 * gradient, which is what a mod adding scenes wants while the art is still being drawn - and what
 * this mod itself wants, since the table ships ahead of the pictures.
 */
public class BackdropLoader {

    public static final String PATH = "data/campaign/backdrops.csv";

    @SuppressWarnings("unchecked")
    public static Map<String, Backdrop> getBackdropsFromMemory() {
        String memKey = "$" + ModPlugin.MOD_ID + "_" + PATH;

        TransientMemory transientMemory = TransientMemory.getInstance();
        if (transientMemory.contains(memKey)) {
            return (Map<String, Backdrop>) transientMemory.get(memKey);
        }

        Map<String, Backdrop> out = new LinkedHashMap<>();

        try {
            JSONArray config = Global.getSettings()
                    .getMergedSpreadsheetDataForMod("id", PATH, ModPlugin.MOD_ID);

            for (int i = 0; i < config.length(); i++) {
                Backdrop backdrop = parseRow(config.getJSONObject(i));

                if (backdrop != null) out.put(backdrop.id, backdrop);
            }
        } catch (IOException | JSONException ex) {
            Global.getLogger(BackdropLoader.class).error("Failed to load " + PATH, ex);
        }

        transientMemory.set(memKey, out);
        return out;
    }

    public static Backdrop get(String id) {
        return id == null ? null : getBackdropsFromMemory().get(id);
    }

    public static List<Backdrop> getAll() {
        return new ArrayList<>(getBackdropsFromMemory().values());
    }

    private static Backdrop parseRow(JSONObject row) throws JSONException {
        if (row == null) return null;

        Backdrop b = new Backdrop();

        b.id = optString(row, "id", null);
        if (b.id == null || b.id.isBlank()) return null;

        b.name = optString(row, "name", "");
        b.sprite = optString(row, "sprite", "");

        b.rarity = FishRarity.parse(optString(row, "rarity", null), FishRarity.COMMON);
        b.crabStock = optBoolean(row, "crabStock", true);
        b.owned = optBoolean(row, "owned", false);

        return b;
    }

    private static String optString(JSONObject row, String key, String def) {
        if (!row.has(key) || row.isNull(key)) return def;

        String v = row.optString(key, def);
        if (v == null) return def;

        v = v.trim();
        return v.isEmpty() ? def : v;
    }

    /** TRUE/true/1/yes, the way every hand-written column in the game gets filled in. */
    private static boolean optBoolean(JSONObject row, String key, boolean def) {
        String v = optString(row, key, null);
        if (v == null) return def;

        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }
}
