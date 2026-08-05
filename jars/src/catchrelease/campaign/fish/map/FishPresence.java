package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the map is allowed to say about where fish live: the questions, separated from every
 * screen that asks them. Dev mode knows everything; a player knows a species once it has been
 * caught or its location data bought, and gets its waters drawn only while the data is the only
 * thing they have - a caught species keeps its listing and drops its shading, since the point of
 * the shading is finding one.
 */
public class FishPresence {

    /** What the pane is currently letting through. */
    public static class Filter {

        public String search = "";
        public final Set<FishType> types = new LinkedHashSet<>();

        public Filter() {
            for (FishType type : FishType.values()) types.add(type);
        }

        public boolean accepts(FishSpec spec) {
            if (!types.contains(FishType.of(spec))) return false;
            if (search == null || search.trim().isEmpty()) return true;

            String needle = search.trim().toLowerCase();

            return spec.getDisplayName().toLowerCase().contains(needle)
                    || spec.id.toLowerCase().contains(needle);
        }
    }

    /** What passes the filters, in table order so the list does not reshuffle as things are caught. */
    public static List<FishSpec> getShown(Filter filter) {
        List<FishSpec> shown = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (!filter.accepts(spec)) continue;
            if (!isKnown(spec)) continue;

            shown.add(spec);
        }

        return shown;
    }

    /** Dev mode knows everything. Otherwise it has to have been caught or paid for. */
    public static boolean isKnown(FishSpec spec) {
        if (Global.getSettings().isDevMode()) return true;

        return FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id);
    }

    /** Whether this species' waters get drawn at all. */
    public static boolean showsRegions(FishSpec spec) {
        if (spec == null || spec.regions.isEmpty()) return false;
        if (Global.getSettings().isDevMode()) return true;

        //knowing where it lives is the whole of it. This used to stop the moment a species was
        //caught, on the reasoning that shading is for finding one you have not got - which reads
        //backwards the moment you want it again: the shading is the answer to where the species can
        //be caught, and landing one is not a reason to be told less. Catching now teaches the
        //location outright, so the two halves of knowing are the same fact
        return isKnown(spec);
    }

    /**
     * The systems a species is said to live in, as hyperspace positions - which systems a region
     * means is asked of the region resolver itself, which is what keeps ABYSSAL working.
     */
    public static List<Vector2f> getHostLocations(FishSpec spec) {
        List<Vector2f> hosts = new ArrayList<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getLocation() == null) continue;

            SectorRegion at = SectorRegion.of(system);
            if (at != null && spec.regions.contains(at)) hosts.add(system.getLocation());
        }

        return hosts;
    }

    /**
     * Every system any species of the type haunts, deduplicated - the union that CATEGORY mode
     * cuts as one shape. A system's location object is the system's own, so identity is enough.
     */
    public static List<Vector2f> getTypeHostLocations(FishType type) {
        Set<Vector2f> hosts = new LinkedHashSet<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (FishType.of(spec) != type) continue;
            if (!isKnown(spec) || !showsRegions(spec)) continue;

            hosts.addAll(getHostLocations(spec));
        }

        return new ArrayList<>(hosts);
    }

    public static FishSpec getSpec(String id) {
        if (id == null) return null;

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec != null && id.equals(spec.id)) return spec;
        }

        return null;
    }

    /** Where pointing the map at this species should land: the record catch, else its first water. */
    public static Vector2f getFocusPoint(FishSpec spec) {
        FishLogEntry logged = FishLog.get(spec.id);
        if (logged != null && logged.recordLocationInHyper != null) return logged.recordLocationInHyper;

        List<Vector2f> hosts = getHostLocations(spec);
        return hosts.isEmpty() ? null : hosts.get(0);
    }

    /** The one quiet word at the end of a species' row. */
    public static String getStatus(FishSpec spec) {
        if (FishLog.isCaught(spec.id)) return "landed";

        //a table row with nowhere to be is a data problem, and dev mode is where it gets caught
        if (Global.getSettings().isDevMode() && spec.regions.isEmpty()) return "no data";

        return "region data";
    }
}
