package catchrelease.distress;

import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DistressCallSpec {

    public final String id;
    public final String providerId;
    public final float weight;
    public final float probability;
    public final String factionId;
    public final String fleetType;
    public final float minFP;
    public final float maxFP;
    public final float cooldownDays;
    public final int maxActive;
    public final String dialogTrigger;
    public final Set<String> tags;

    private DistressCallSpec(String id, String providerId, float weight, float probability,
                             String factionId, String fleetType, float minFP, float maxFP,
                             float cooldownDays, int maxActive, String dialogTrigger,
                             Set<String> tags) {
        this.id = id;
        this.providerId = providerId;
        this.weight = weight;
        this.probability = probability;
        this.factionId = factionId;
        this.fleetType = fleetType;
        this.minFP = minFP;
        this.maxFP = maxFP;
        this.cooldownDays = cooldownDays;
        this.maxActive = maxActive;
        this.dialogTrigger = dialogTrigger;
        this.tags = Collections.unmodifiableSet(tags);
    }

    static DistressCallSpec parse(JSONObject row) {
        String id = text(row, "id");
        if (id == null || id.startsWith("#")) return null;

        String providerId = text(row, "providerId");
        String factionId = text(row, "factionId");
        String fleetType = text(row, "fleetType");
        String dialogTrigger = text(row, "dialogTrigger");
        if (providerId == null || factionId == null || fleetType == null || dialogTrigger == null) {
            throw new IllegalArgumentException("Missing required field for distress call '" + id + "'");
        }
        if (!id.contains("_")) {
            throw new IllegalArgumentException("Distress call id must be namespaced: '" + id + "'");
        }

        float weight = number(row, "weight", 1f);
        float probability = number(row, "probability", 1f);
        float minFP = number(row, "minFP", 10f);
        float maxFP = number(row, "maxFP", minFP);
        float cooldownDays = number(row, "cooldownDays", 30f);
        int maxActive = Math.round(number(row, "maxActive", 1f));
        if (weight <= 0f || probability <= 0f || probability > 1f || minFP <= 0f
                || maxFP < minFP || cooldownDays < 0f || maxActive < 1) {
            throw new IllegalArgumentException("Invalid numeric field for distress call '" + id + "'");
        }

        Set<String> tags = new LinkedHashSet<>();
        String tagText = text(row, "tags");
        if (tagText != null) {
            for (String tag : tagText.split(",")) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) tags.add(trimmed);
            }
        }

        return new DistressCallSpec(id, providerId, weight, probability, factionId, fleetType,
                minFP, maxFP, cooldownDays, maxActive, dialogTrigger, tags);
    }

    private static String text(JSONObject row, String key) {
        if (row == null || !row.has(key) || row.isNull(key)) return null;
        String value = row.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    private static float number(JSONObject row, String key, float fallback) {
        String value = text(row, key);
        if (value == null) return fallback;

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + key + " value '" + value + "'");
        }
    }
}
