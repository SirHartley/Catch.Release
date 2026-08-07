package catchrelease.campaign.fish.data;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Map;

/**
 * What the player has caught: species seen, records, and unlocked location data. Single source
 * the codex reads from. Lives in sector persistent data, so it survives without a script holding it.
 */
public class FishLog {

    public static final String KEY = "$catchrelease_log";

    /**
     * Files a catch.
     *
     * @param where  entity the catch was taken near (pond/mote/break) - only its system and location are read
     * @param method how it came out
     * @return true if this beat the record (also true for the species' first catch)
     */
    public static boolean record(FishCatch entry, SectorEntityToken where, FishLogEntry.Method method) {
        if (entry == null || entry.speciesId == null) return false;

        Map<String, FishLogEntry> log = getLog();

        FishLogEntry logged = log.get(entry.speciesId);
        boolean first = logged == null;

        if (first) {
            logged = new FishLogEntry(entry.speciesId);
            log.put(entry.speciesId, logged);
        }

        logged.caught++;
        logged.hintOnly = false;

        logged.locationDataUnlocked = true; //landing a catch always unlocks its location too

        boolean record = first || logged.isRecord(entry);

        if (first) {
            logged.firstSystemName = getSystemName(where);
            logged.firstLocationInHyper = getLocationInHyper(where);
            logged.firstTimestamp = getTimestamp();
            logged.firstMethod = method == null ? FishLogEntry.Method.UNKNOWN : method;
        }

        if (record) {
            logged.recordLength = entry.length;
            logged.recordWeight = entry.weight;
            logged.recordAberration = entry.aberration;
            logged.recordSystemName = getSystemName(where);
            logged.recordLocationInHyper = getLocationInHyper(where);
            logged.recordTimestamp = getTimestamp();
            logged.recordMethod = method == null ? FishLogEntry.Method.UNKNOWN : method;
        }

        return record;
    }

    /** Null for a species that has never been caught, which is how the codex knows to hide it. */
    public static FishLogEntry get(String speciesId) {
        return speciesId == null ? null : getLog().get(speciesId);
    }

    /** Caught means landed at least once - a hint bought for something never seen does not count. */
    public static boolean isCaught(String speciesId) {
        FishLogEntry entry = get(speciesId);

        return entry != null && !entry.hintOnly;
    }

    /**
     * Unlocks a species' map location, e.g. from a purchased hint.
     *
     * @return true if it is now unlocked
     */
    public static boolean unlockLocationData(String speciesId) {
        if (speciesId == null) return false;

        FishLogEntry entry = get(speciesId);

        //creates a hint-only entry if the species was never caught, rather than refusing
        if (entry == null) {
            entry = new FishLogEntry(speciesId);
            entry.hintOnly = true;
            getLog().put(speciesId, entry);
        }

        entry.locationDataUnlocked = true;

        return true;
    }

    public static boolean isLocationDataUnlocked(String speciesId) {
        FishLogEntry entry = get(speciesId);

        return entry != null && entry.locationDataUnlocked;
    }

    /**
     * The inverse of {@link #unlockLocationData}, for a purchase taken back. A hint-only entry
     * that exists solely because of that unlock is removed outright; a species with catches on
     * the books keeps its record and only has the location flag lowered.
     */
    public static void relockLocationData(String speciesId) {
        FishLogEntry entry = get(speciesId);
        if (entry == null) return;

        if (entry.hintOnly && entry.caught <= 0) {
            getLog().remove(speciesId);
            return;
        }

        entry.locationDataUnlocked = false;
    }

    /** Raw clock timestamp; formatted as a date only where displayed. */
    protected static long getTimestamp() {
        if (Global.getSector() == null) return 0L;

        return Global.getSector().getClock().getTimestamp();
    }

    protected static String getSystemName(SectorEntityToken where) {
        if (where == null) return null;

        LocationAPI location = where.getContainingLocation();

        return location == null ? null : location.getName();
    }

    /** Hyperspace location, used to draw the map circle. */
    protected static Vector2f getLocationInHyper(SectorEntityToken where) {
        if (where == null) return null;

        Vector2f loc = where.getLocationInHyperspace();

        return loc == null ? null : new Vector2f(loc);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, FishLogEntry> getLog() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(KEY);
        if (stored instanceof Map) return (Map<String, FishLogEntry>) stored;

        Map<String, FishLogEntry> log = new HashMap<>();
        data.put(KEY, log);

        return log;
    }
}
