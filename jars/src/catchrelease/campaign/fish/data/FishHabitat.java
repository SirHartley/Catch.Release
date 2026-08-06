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

/**
 * Everything a place says about itself that a species might care about, read once.
 * <p>
 * Gathered here rather than at each caller because there are several, and they were not asking the
 * same question: the spawner tested star type, tags and region, and the map tested region alone - so
 * the map shaded systems a fish could never turn up in, and said so with a confidence the spawner
 * did not share. One object, one {@link FishSpec#matches} against it, and the two cannot drift.
 * <p>
 * Cached for the session, keyed by location id. None of these change during a game: the sector's
 * geometry, its stars, its constellations, its slipstreams and its abyss are all settled at
 * generation. Recomputing was not merely wasteful - {@link Aberration} walks every slipstream in
 * hyperspace, and the map asks this of every system for every species it draws.
 */
public class FishHabitat {

    public static final String CACHE_KEY = "$catchrelease_habitats";

    /** What the sky is. Never null - a system with no star answers {@link StarColour#NONE}. */
    public final StarColour star;

    /** The star's own planet type, kept for anything that wants the exact thing rather than its colour. */
    public final String starType;

    public final Set<String> tags;
    public final SectorRegion region;

    /** How old the constellation is, or null for a system that belongs to none. */
    public final StarAge age;

    /** How badly reality holds here, 0 to 1, without the per-catch jitter. */
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

    /**
     * How old the constellation this system belongs to is. Vanilla's own {@link StarAge#ANY} is
     * folded to null - it means the age was never decided, which is not a third age to fish in.
     */
    public static StarAge getAge(LocationAPI location) {
        if (!(location instanceof StarSystemAPI)) return null;

        Constellation constellation = ((StarSystemAPI) location).getConstellation();
        if (constellation == null) return null;

        StarAge age = constellation.getAge();

        return age == StarAge.ANY ? null : age;
    }
}
