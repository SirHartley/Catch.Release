package catchrelease.campaign.fish.data;

import catchrelease.memory.TransientMemory;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


public class FishHabitat {

    public static final String CACHE_KEY = "$catchrelease_habitats";


    public final StarColour star;


    public final String starType;

    public final Set<String> tags;
    public final SectorRegion region;


    public final StarAge age;


    public final float aberration;

    protected FishHabitat(StarColour star, String starType, Set<String> tags, SectorRegion region,
                          StarAge age, float aberration) {
        this.star = star;
        this.starType = starType;
        this.tags = tags;
        this.region = region;
        this.age = age;
        this.aberration = aberration;
    }

    @SuppressWarnings("unchecked")
    public static FishHabitat of(LocationAPI location) {
        if (location == null) return null;

        Map<String, FishHabitat> cache;

        Object stored = TransientMemory.getInstance().get(CACHE_KEY);
        if (stored instanceof Map) {
            cache = (Map<String, FishHabitat>) stored;
        } else {
            cache = new HashMap<>();
            TransientMemory.getInstance().set(CACHE_KEY, cache);
        }

        FishHabitat known = cache.get(location.getId());
        if (known != null) return known;

        FishHabitat built = read(location);
        cache.put(location.getId(), built);

        return built;
    }

    protected static FishHabitat read(LocationAPI location) {
        String starType = getStarType(location);

        return new FishHabitat(
                StarColour.of(starType),
                starType,
                new LinkedHashSet<>(location.getTags()),
                SectorRegion.of(location),
                getAge(location),
                Aberration.baseAt(location.getLocation(), location));
    }


    public static String getStarType(LocationAPI location) {
        if (!(location instanceof StarSystemAPI)) return null;

        PlanetAPI star = ((StarSystemAPI) location).getStar();
        if (star == null || star.getSpec() == null) return null;

        return star.getSpec().getPlanetType();
    }


    public static StarAge getAge(LocationAPI location) {
        if (!(location instanceof StarSystemAPI)) return null;

        Constellation constellation = ((StarSystemAPI) location).getConstellation();
        if (constellation == null) return null;

        StarAge age = constellation.getAge();

        return age == StarAge.ANY ? null : age;
    }
}
