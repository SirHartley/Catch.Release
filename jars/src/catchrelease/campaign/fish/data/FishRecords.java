package catchrelease.campaign.fish.data;

import com.fs.starfarer.api.Global;

import java.util.HashMap;
import java.util.Map;

/**
 * The best of each species the player has ever landed, kept in the save.
 * <p>
 * Length is the measure, because length is what a record is about - a fish is remembered as the
 * longest one, not the heaviest or the most valuable. Everything else about a specimen is already
 * said elsewhere.
 * <p>
 * Held in sector persistent data rather than in a script, so it survives without anything having to
 * be running to keep it.
 */
public class FishRecords {

    public static final String KEY = "$catchrelease_records";

    /**
     * Files a specimen and says whether it beat what was there.
     * <p>
     * The first of a species is a record, which is the right answer: it is the best one caught, and
     * a player who has never seen one before should be told that they have now.
     */
    public static boolean submit(FishCatch entry) {
        if (entry == null || entry.speciesId == null) return false;

        Map<String, Float> records = getRecords();
        Float best = records.get(entry.speciesId);

        if (best != null && entry.length <= best) return false;

        records.put(entry.speciesId, entry.length);

        return true;
    }

    /** The longest of a species ever landed, or null for one that has never been caught. */
    public static Float getBest(String speciesId) {
        return speciesId == null ? null : getRecords().get(speciesId);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Float> getRecords() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(KEY);
        if (stored instanceof Map) return (Map<String, Float>) stored;

        Map<String, Float> records = new HashMap<>();
        data.put(KEY, records);

        return records;
    }
}
