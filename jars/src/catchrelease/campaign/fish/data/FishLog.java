package catchrelease.campaign.fish.data;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Map;

/**
 * What the player has caught, and what they know about it.
 * <p>
 * The one place a catch is written down. The codex reads it to decide which species exist as far as
 * the player is concerned, what the record is, and whether the location data has been paid for -
 * nothing else in the mod needs to know how any of that is stored.
 * <p>
 * Lives in sector persistent data, so it survives without a script running to hold it.
 */
public class FishLog {

    public static final String KEY = "$catchrelease_log";

    /**
     * Files a catch. Everything the codex shows about a species comes from here.
     *
     * @param where  what it was taken from or near - the pond, the mote, the break. Only its system
     *               and where that system sits are read off it
     * @param method how it came out
     * @return true if this one beat the record, which is also true for the first of a species
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

        //landing one is knowing where it came from. Buying the survey and catching one were separate
        //facts before, so a species could be in the log, in the codex, and on the map list while the
        //map itself refused to shade the waters it had just been pulled out of
        logged.locationDataUnlocked = true;

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
     * Opens up where a species is found - the map and the circle on it.
     * <p>
     * The one call anything selling a hint needs to make. Silently does nothing for a species that
     * has never been caught, since there is nothing recorded to unlock.
     *
     * @return true if there was something to unlock and it is now unlocked
     */
    public static boolean unlockLocationData(String speciesId) {
        if (speciesId == null) return false;

        FishLogEntry entry = get(speciesId);

        //a hint is worth buying for something never caught, which is most of the point of one - so
        //this makes the entry rather than refusing, and marks it as having no catch behind it
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

    /** The clock's own stamp. Turned into a date at the point it is shown, not here. */
    protected static long getTimestamp() {
        if (Global.getSector() == null) return 0L;

        return Global.getSector().getClock().getTimestamp();
    }

    protected static String getSystemName(SectorEntityToken where) {
        if (where == null) return null;

        LocationAPI location = where.getContainingLocation();

        return location == null ? null : location.getName();
    }

    /** Where the system sits on the sector map, which is what a circle is drawn around. */
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
