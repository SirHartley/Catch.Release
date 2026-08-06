package catchrelease.campaign.fish.map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * The plotted fishing route, as the save knows it: an ordered list of stops, each a system and
 * the fish to take there.
 * <p>
 * Held as ids rather than as live objects, because the route outlives every screen it is drawn
 * on and has to ride the save - systems are looked back up when something wants to draw or fly
 * it. There is at most one route; plotting a new one replaces the old, and it stays until the
 * player closes it by hand off the map.
 */
public class FishRoute {

    public static final String KEY = "$catchrelease_fish_route";

    /** One system on the route, and which of the picked fish it covers. */
    public static class Stop implements Serializable {
        public String systemId;
        public ArrayList<String> fishIds = new ArrayList<>();
    }

    /** The whole route, in travel order from wherever the player was when it was plotted. */
    public static class Saved implements Serializable {
        public ArrayList<Stop> stops = new ArrayList<>();
    }

    public static Saved get() {
        if (Global.getSector() == null) return null;

        Object stored = Global.getSector().getPersistentData().get(KEY);

        return stored instanceof Saved ? (Saved) stored : null;
    }

    public static void set(Saved route) {
        if (Global.getSector() == null) return;

        if (route == null || route.stops.isEmpty()) {
            clear();
            return;
        }

        Global.getSector().getPersistentData().put(KEY, route);
    }

    public static void clear() {
        if (Global.getSector() == null) return;

        Global.getSector().getPersistentData().remove(KEY);
    }

    /** The stop's system as it exists right now, or null if the sector no longer has it. */
    public static StarSystemAPI getSystem(Stop stop) {
        if (stop == null || stop.systemId == null || Global.getSector() == null) return null;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (stop.systemId.equals(system.getId())) return system;
        }

        return null;
    }
}
