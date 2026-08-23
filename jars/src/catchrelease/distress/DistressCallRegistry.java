package catchrelease.distress;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DistressCallRegistry {

    private static Map<String, DistressCallSpec> specs = Collections.emptyMap();

    static void reload() {
        Map<String, DistressCallSpec> loaded = new LinkedHashMap<>();

        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetDataForMod(
                    "id", DistressCallSettings.SPEC_PATH, DistressCallSettings.MASTER_MOD_ID);

            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);

                try {
                    DistressCallSpec spec = DistressCallSpec.parse(row);
                    if (spec == null) continue;
                    if (loaded.put(spec.id, spec) != null) {
                        throw new IllegalArgumentException("Duplicate distress call id '" + spec.id + "'");
                    }
                } catch (RuntimeException ex) {
                    DistressCallFramework.logError("Rejected distress call row " + (i + 2), ex);
                }
            }
        } catch (Exception ex) {
            DistressCallFramework.logError("Could not load " + DistressCallSettings.SPEC_PATH, ex);
        }

        specs = Collections.unmodifiableMap(loaded);
    }

    static DistressCallSpec get(String id) {
        return specs.get(id);
    }

    static List<DistressCallSpec> all() {
        return new ArrayList<>(specs.values());
    }

    static List<String> ids() {
        return new ArrayList<>(specs.keySet());
    }
}
