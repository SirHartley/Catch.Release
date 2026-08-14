package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishHabitat;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
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

    /**
     * What the pane is currently letting through. Starts with no types enabled, so a freshly
     * opened map shades nothing until the player - or the codex - asks it to; an empty type set
     * leaves the list itself unfiltered, since chips narrow the list only once any are on.
     */
    public static class Filter {

        public String search = "";
        public final Set<FishType> types = new LinkedHashSet<>();

        /** Optional job/intel constraint. Empty ordinarily means unrestricted; the separate flag
         * lets an accepted request with no currently known matches deliberately show no rows. */
        public boolean speciesRestricted = false;
        public final Set<String> allowedSpeciesIds = new LinkedHashSet<>();

        public boolean accepts(FishSpec spec) {
            if (speciesRestricted && !allowedSpeciesIds.contains(spec.id)) return false;
            if (!types.isEmpty() && !types.contains(FishType.of(spec))) return false;
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

    /**
     * Whether a species turns up in a system, on any gear.
     * <p>
     * The single answer every screen reads. The map used to test the region alone, so it shaded
     * systems under the wrong sun, of the wrong age, and where the fabric was the wrong thickness -
     * and said so beside a spawner that would never have offered the fish there.
     */
    public static boolean livesIn(FishSpec spec, StarSystemAPI system) {
        return spec != null && system != null && spec.matches(FishHabitat.of(system));
    }

    /** Whether this species' waters get drawn at all. */
    public static boolean showsRegions(FishSpec spec) {
        if (spec == null || !spec.hasHabitat()) return false;
        if (Global.getSettings().isDevMode()) return true;

        //shading tracks isKnown() - catching a species also teaches its location, so there's no
        //separate cutoff once landed
        return isKnown(spec);
    }

    /**
     * Whether a system belongs on the fish chart at all: reachable from hyperspace and not the
     * abyss. Limbo is the standing exception - vanilla itself carves it out of its own skips,
     * and its water is the whole point of going.
     */
    public static boolean isChartable(StarSystemAPI system) {
        if (system == null) return false;
        if ("Limbo".equals(system.getBaseName())) return true;

        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
        if (system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;

        return true;
    }

    /**
     * The systems a species is said to live in, as hyperspace positions. Asked of the habitat
     * itself rather than of the region alone, so what is shaded is what could actually be
     * caught - and only where a chart is any use, so unreachable water never draws a circle.
     */
    public static List<Vector2f> getHostLocations(FishSpec spec) {
        List<Vector2f> hosts = new ArrayList<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getLocation() == null) continue;
            if (!isChartable(system)) continue;

            if (livesIn(spec, system)) hosts.add(system.getLocation());
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

    /** The category union after an intel request has narrowed the species list. */
    public static List<Vector2f> getTypeHostLocations(FishType type, Filter filter) {
        if (filter == null || !filter.speciesRestricted) return getTypeHostLocations(type);

        Set<Vector2f> hosts = new LinkedHashSet<>();
        for (FishSpec spec : getShown(filter)) {
            if (FishType.of(spec) != type || !showsRegions(spec)) continue;
            hosts.addAll(getHostLocations(spec));
        }

        return new ArrayList<>(hosts);
    }

    /** Known species catchable in the system, caught first so the art leads the row. */
    public static List<FishSpec> getKnownFishIn(StarSystemAPI system) {
        List<FishSpec> caught = new ArrayList<>();
        List<FishSpec> surveyed = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (!livesIn(spec, system)) continue;
            if (!isKnown(spec)) continue;

            if (FishLog.isCaught(spec.id)) caught.add(spec);
            else surveyed.add(spec);
        }

        caught.addAll(surveyed);

        return caught;
    }

    /** How many species live here that the player has never heard of - counted, never named. */
    public static int getUnknownCountIn(StarSystemAPI system) {
        int count = 0;

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (!livesIn(spec, system)) continue;
            if (isKnown(spec)) continue;

            count++;
        }

        return count;
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

        //missing region data is flagged only in dev mode
        if (Global.getSettings().isDevMode() && !spec.hasHabitat()) return "no data";

        return "region data";
    }
}
