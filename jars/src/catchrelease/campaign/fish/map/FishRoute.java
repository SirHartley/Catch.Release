package catchrelease.campaign.fish.map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.io.Serializable;
import java.util.ArrayList;

public class FishRoute {
    public static final String KEY = "$catchrelease_fish_route";

    public static class Stop implements Serializable {
        public String systemId;
        public ArrayList<String> fishIds = new ArrayList<>();
    }

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

    public static StarSystemAPI getSystem(Stop stop) {
        if (stop == null || stop.systemId == null || Global.getSector() == null) return null;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (stop.systemId.equals(system.getId())) return system;
        }

        return null;
    }
}
