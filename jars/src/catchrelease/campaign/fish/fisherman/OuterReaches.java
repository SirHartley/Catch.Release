package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class OuterReaches {

    public static final float AVOID_RADIUS = 2500f;
    public static final float INNER_PAD = 1500f;
    public static final float OUTER_PAD = 2500f;
    public static final float MIN_BAND = 3000f;
    public static final float DEFAULT_OUTER = 14000f;
    public static final int TRIES = 24;
    public static final float LEG_DAYS = 25f;

    public static Vector2f center(StarSystemAPI system) {
        if (system == null || system.getCenter() == null) return new Vector2f();

        return new Vector2f(system.getCenter().getLocation());
    }

    public static List<SectorEntityToken> getPopulated(StarSystemAPI system) {
        List<SectorEntityToken> out = new ArrayList<>();
        if (system == null) return out;

        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (market.isPlanetConditionMarketOnly()) continue;
            if (market.getPrimaryEntity() == null) continue;

            out.add(market.getPrimaryEntity());
        }

        return out;
    }

    public static boolean isPopulated(StarSystemAPI system) {
        return !getPopulated(system).isEmpty();
    }

    public static float getInnerLimit(StarSystemAPI system) {
        float furthest = 0f;

        for (SectorEntityToken populated : getPopulated(system)) {
            furthest = Math.max(furthest,
                    Misc.getDistance(center(system), populated.getLocation()));
        }

        return furthest + AVOID_RADIUS + INNER_PAD;
    }

    public static float getOuterLimit(StarSystemAPI system) {
        float furthest = 0f;

        if (system != null) {
            for (SectorEntityToken entity : system.getAllEntities()) {
                if (entity.getOrbit() == null) continue;

                furthest = Math.max(furthest,
                        Misc.getDistance(center(system), entity.getLocation()));
            }
        }

        if (furthest <= 0f) furthest = DEFAULT_OUTER;

        return Math.max(furthest + OUTER_PAD, getInnerLimit(system) + MIN_BAND);
    }

    public static Vector2f place(StarSystemAPI system, Vector2f preferred) {
        if (system == null || preferred == null) return preferred;
        if (!isPopulated(system)) return preferred;

        Vector2f center = center(system);

        float inner = getInnerLimit(system);
        float outer = getOuterLimit(system);

        float distance = Misc.getDistance(center, preferred);
        if (distance >= inner && distance <= outer) return preferred;

        // a point sitting on the star has no bearing to keep, so it is given one
        float bearing = distance <= 1f
                ? MathUtils.getRandomNumberInRange(0f, 360f)
                : Misc.getAngleInDegrees(center, preferred);

        return MathUtils.getPointOnCircumference(center,
                MathUtils.clamp(distance, inner, outer), bearing);
    }

    public static Vector2f pick(StarSystemAPI system, Vector2f from) {
        float inner = getInnerLimit(system);
        float outer = getOuterLimit(system);

        Vector2f center = center(system);

        for (int i = 0; i < TRIES; i++) {
            Vector2f at = MathUtils.getPointOnCircumference(center,
                    MathUtils.getRandomNumberInRange(inner, outer),
                    MathUtils.getRandomNumberInRange(0f, 360f));

            if (isLegClear(system, from, at)) return at;
        }

        return MathUtils.getPointOnCircumference(center, outer,
                MathUtils.getRandomNumberInRange(0f, 360f));
    }

    public static boolean isLegClear(StarSystemAPI system, Vector2f from, Vector2f to) {
        for (SectorEntityToken populated : getPopulated(system)) {
            if (distanceToSegment(populated.getLocation(), from, to) < AVOID_RADIUS) return false;
        }

        return true;
    }

    public static float distanceToSegment(Vector2f point, Vector2f from, Vector2f to) {
        if (from == null) return Misc.getDistance(point, to);

        float dx = to.x - from.x;
        float dy = to.y - from.y;

        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0001f) return Misc.getDistance(point, from);

        float along = ((point.x - from.x) * dx + (point.y - from.y) * dy) / lengthSquared;
        along = MathUtils.clamp(along, 0f, 1f);

        return Misc.getDistance(point, new Vector2f(from.x + dx * along, from.y + dy * along));
    }
}
