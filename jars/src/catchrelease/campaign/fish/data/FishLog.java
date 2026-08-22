package catchrelease.campaign.fish.data;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Map;


public class FishLog {

    public static final String KEY = "$catchrelease_log";


    public static boolean record(FishCatch entry, SectorEntityToken where, FishLogEntry.Method method) {
        if (entry == null || entry.speciesId == null) return false;

        Map<String, FishLogEntry> log = getLog();

        FishLogEntry logged = log.get(entry.speciesId);
        boolean first = logged == null || logged.caught <= 0;

        if (first) {
            logged = new FishLogEntry(entry.speciesId);
            log.put(entry.speciesId, logged);
        }

        logged.caught++;
        logged.hintOnly = false;

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


    public static FishLogEntry get(String speciesId) {
        return speciesId == null ? null : getLog().get(speciesId);
    }


    public static boolean isCaught(String speciesId) {
        FishLogEntry entry = get(speciesId);

        return entry != null && entry.caught > 0;
    }


    public static boolean unlockLocationData(String speciesId) {
        if (speciesId == null) return false;

        FishLogEntry entry = get(speciesId);

        // creates a hint-only entry if the species was never caught, rather than refusing
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


    public static void relockLocationData(String speciesId) {
        FishLogEntry entry = get(speciesId);
        if (entry == null) return;

        if (entry.caught <= 0) {
            getLog().remove(speciesId);
            return;
        }

        entry.locationDataUnlocked = false;
    }


    protected static long getTimestamp() {
        if (Global.getSector() == null) return 0L;

        return Global.getSector().getClock().getTimestamp();
    }


    public static String getSystemName(SectorEntityToken where) {
        if (where == null) return null;

        LocationAPI location = where.getContainingLocation();

        return location == null ? null : location.getName();
    }


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
