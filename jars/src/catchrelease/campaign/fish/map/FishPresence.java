package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRanges;
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

public class FishPresence {

    public static class Filter {

        public String search = "";
        public final Set<FishType> types = new LinkedHashSet<>();
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

    public static boolean hasRangeData(FishSpec spec) {
        if (spec == null || spec.id == null) return false;

        // Dev mode opens the complete chart without writing unlocks into the save.
        return Global.getSettings().isDevMode()
                || FishLog.isCaught(spec.id)
                || FishLog.isLocationDataUnlocked(spec.id);
    }

    public static boolean isKnown(FishSpec spec) {
        return hasRangeData(spec);
    }

    public static boolean livesIn(FishSpec spec, StarSystemAPI system) {
        return FishRanges.matches(spec, system, null);
    }

    public static boolean showsRegions(FishSpec spec) {
        if (spec == null || !spec.hasHabitat()) return false;

        return hasRangeData(spec);
    }

    public static boolean isChartable(StarSystemAPI system) {
        if (system == null) return false;
        if ("Limbo".equals(system.getBaseName())) return true;

        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
        if (system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;

        return true;
    }

    public static List<Vector2f> getHostLocations(FishSpec spec) {
        List<Vector2f> hosts = new ArrayList<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getLocation() == null) continue;
            if (!isChartable(system)) continue;

            if (livesIn(spec, system)) hosts.add(system.getLocation());
        }

        return hosts;
    }

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

    public static List<Vector2f> getTypeHostLocations(FishType type, Filter filter) {
        if (filter == null || !filter.speciesRestricted) return getTypeHostLocations(type);

        Set<Vector2f> hosts = new LinkedHashSet<>();
        for (FishSpec spec : getShown(filter)) {
            if (FishType.of(spec) != type || !showsRegions(spec)) continue;
            hosts.addAll(getHostLocations(spec));
        }

        return new ArrayList<>(hosts);
    }

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

    public static Vector2f getFocusPoint(FishSpec spec) {
        if (!hasRangeData(spec)) return null;

        FishLogEntry logged = FishLog.get(spec.id);
        if (logged != null && logged.recordLocationInHyper != null) return logged.recordLocationInHyper;

        List<Vector2f> hosts = getHostLocations(spec);
        return hosts.isEmpty() ? null : hosts.get(0);
    }

    public static String getStatus(FishSpec spec) {
        if (FishLog.isCaught(spec.id)) return "landed";

        if (!hasRangeData(spec) || !spec.hasHabitat()) return "no data";

        return "region data";
    }
}
