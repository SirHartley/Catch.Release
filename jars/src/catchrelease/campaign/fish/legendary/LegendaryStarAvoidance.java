package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public final class LegendaryStarAvoidance {

    public static final float CLEARANCE = 150f;
    private static final float EDGE_EPSILON = 1f;

    private record Star(Vector2f center, float radius) {
    }

    private LegendaryStarAvoidance() {
    }

    public static boolean applies(FishSpec spec) {
        return spec != null && spec.rarity == FishRarity.LEGENDARY
                && !LegendaryShields.DAWN_SPECIES.equals(spec.id);
    }

    public static boolean confine(SectorEntityToken mote, FishSpec spec) {
        return confine(mote, spec, 0f);
    }

    public static boolean confine(SectorEntityToken mote, FishSpec spec, float extra) {
        if (spec == null) return true;
        if (!applies(spec)) return FalseDawnOrbit.confine(mote, spec.id);
        Vector2f safe = patrolStart(mote, spec, extra, patrolCenter(mote, spec),
                stars(mote.getContainingLocation(), extra));
        mote.setLocation(safe.x, safe.y);
        return true;
    }

    public static void move(SectorEntityToken mote, FishSpec spec, Vector2f destination, float extra) {
        if (!applies(spec)) {
            mote.setLocation(destination.x, destination.y);
            return;
        }
        List<Star> stars = stars(mote.getContainingLocation(), extra);
        Vector2f center = patrolCenter(mote, spec);
        Vector2f from = mote.getLocation();
        Vector2f start = patrolStart(mote, spec, extra, center, stars);
        Vector2f next = step(stars, start, destination.x - from.x, destination.y - from.y, extra, center);
        mote.setLocation(next.x, next.y);
    }

    private static Vector2f patrolCenter(SectorEntityToken mote, FishSpec spec) {
        return mote.getContainingLocation() instanceof StarSystemAPI system && LegendarySpawns.isPatrolling(spec)
                ? LegendarySpawns.center(system) : null;
    }

    private static Vector2f patrolStart(SectorEntityToken mote, FishSpec spec, float extra,
                                      Vector2f center, List<Star> stars) {
        Vector2f safe = outside(stars, mote.getLocation());
        if (inside(center, safe, extra)) return safe;
        Vector2f replacement = LegendarySpawns.position((StarSystemAPI) mote.getContainingLocation(), spec, extra);
        return replacement == null ? safe : replacement;
    }

    public static Vector2f place(LocationAPI location, Vector2f point, float extra) {
        return outside(stars(location, extra), point);
    }

    public static Vector2f step(LocationAPI location, Vector2f from, Vector2f to, float extra) {
        List<Star> stars = stars(location, extra);
        return step(stars, outside(stars, from), to.x - from.x, to.y - from.y, extra, null);
    }

    private static Vector2f step(List<Star> stars, Vector2f start, float dx, float dy,
                                 float extra, Vector2f center) {
        Vector2f wanted = new Vector2f(start.x + dx, start.y + dy);
        if (inside(center, wanted, extra) && clear(stars, start, wanted)) return wanted;

        // Test the whole segment: a long dash can cross a star with both endpoints outside it.
        double heading = Math.atan2(dy, dx);
        double length = Math.hypot(dx, dy);
        for (int degrees = 5; degrees <= 180; degrees += 5) {
            for (int side = 1; side >= -1; side -= 2) {
                double angle = heading + side * Math.toRadians(degrees);
                Vector2f next = new Vector2f(start.x + (float) (Math.cos(angle) * length),
                        start.y + (float) (Math.sin(angle) * length));
                if (inside(center, next, extra) && clear(stars, start, next)) return next;
            }
        }
        return start;
    }

    private static boolean inside(Vector2f center, Vector2f point, float extra) {
        if (center == null) return true;
        double dx = point.x - center.x;
        double dy = point.y - center.y;
        double radius = LegendarySpawns.RADIUS - extra;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static List<Star> stars(LocationAPI location, float extra) {
        List<Star> result = new ArrayList<>();
        if (location == null) return result;
        List<CampaignTerrainAPI> terrain = location.getTerrainCopy();
        for (PlanetAPI star : location.getPlanets()) {
            if (!star.isStar() || star.isExpired()) continue;
            float radius = star.getRadius();
            for (CampaignTerrainAPI token : terrain) {
                if (token.isExpired() || !(token.getPlugin() instanceof StarCoronaTerrainPlugin corona)) continue;
                var params = corona.getParams();
                if (params.relatedEntity == star) {
                    radius = Math.max(radius, params.middleRadius + params.bandWidthInEngine * 0.5f);
                }
            }
            result.add(new Star(new Vector2f(star.getLocation()), radius + CLEARANCE + extra));
        }
        return result;
    }

    private static Vector2f outside(List<Star> stars, Vector2f point) {
        Vector2f safe = new Vector2f(point);
        for (int pass = 0; pass <= stars.size() * 4; pass++) {
            boolean changed = false;
            for (Star star : stars) {
                float dx = safe.x - star.center.x;
                float dy = safe.y - star.center.y;
                double distance = Math.hypot(dx, dy);
                if (distance >= star.radius + EDGE_EPSILON) continue;
                if (distance < 0.001) safe.set(star.center.x + star.radius + EDGE_EPSILON, star.center.y);
                else {
                    float scale = (float) ((star.radius + EDGE_EPSILON) / distance);
                    safe.set(star.center.x + dx * scale, star.center.y + dy * scale);
                }
                changed = true;
            }
            if (!changed) return safe;
        }
        // Overlapping coronas can defeat successive projections; this point clears every disk.
        for (Star star : stars) safe.x = Math.max(safe.x, star.center.x + star.radius + EDGE_EPSILON);
        return safe;
    }

    private static boolean clear(List<Star> stars, Vector2f from, Vector2f to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double lengthSquared = dx * dx + dy * dy;
        for (Star star : stars) {
            double along = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1,
                    ((star.center.x - from.x) * dx + (star.center.y - from.y) * dy) / lengthSquared));
            double x = from.x + along * dx - star.center.x;
            double y = from.y + along * dy - star.center.y;
            if (x * x + y * y < (double) star.radius * star.radius) return false;
        }
        return true;
    }
}
