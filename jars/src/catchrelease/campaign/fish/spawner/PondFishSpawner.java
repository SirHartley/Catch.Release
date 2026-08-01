package catchrelease.campaign.fish.spawner;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.HashSet;
import java.util.Set;

/**
 * Picks which fish a pond produces, from where the pond is.
 * <p>
 * A fish qualifies if the system's star type, tags and {@link SectorRegion} all satisfy whatever that
 * row asked for - and a row that asked for nothing qualifies everywhere. Between the qualifiers it is
 * a straight weighted roll on spawnWeight.
 */
public class PondFishSpawner {

    /** The id to hand to a mote, or null if nothing at all can live here. */
    public static String pickFishId(LocationAPI location) {
        FishSpec spec = pickFish(location);

        return spec == null ? null : spec.id;
    }

    public static FishSpec pickFish(LocationAPI location) {
        String starType = getStarType(location);
        SectorRegion region = SectorRegion.of(location);
        Set<String> tags = location == null ? new HashSet<String>() : new HashSet<>(location.getTags());

        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec.spawnWeight <= 0f) continue;
            if (!spec.matches(starType, tags, region)) continue;

            picker.add(spec, spec.spawnWeight);
        }

        if (picker.isEmpty()) {
            Global.getLogger(PondFishSpawner.class).warn("No fish match star type " + starType
                    + ", region " + region + " - check " + FishSpecLoader.PATH);
            return null;
        }

        return picker.pick();
    }

    /**
     * The star's planet type, e.g. "star_red" - null for anywhere without a star, which includes
     * hyperspace and the odd nebula system.
     */
    public static String getStarType(LocationAPI location) {
        if (!(location instanceof StarSystemAPI)) return null;

        PlanetAPI star = ((StarSystemAPI) location).getStar();
        if (star == null || star.getSpec() == null) return null;

        return star.getSpec().getPlanetType();
    }
}
