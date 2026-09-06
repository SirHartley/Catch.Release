package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public class OuterReaches {

    public static final float AVOID_RADIUS = 5000f;
    public static final float ROUTE_PAD = 1000f;
    public static final float RECHECK_SECONDS = 0.25f;
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
        Set<SectorEntityToken> out = new LinkedHashSet<>();
        if (system == null) return new ArrayList<>();

        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (market.isPlanetConditionMarketOnly()) continue;
            out.add(market.getPrimaryEntity());
            out.addAll(market.getConnectedEntities());
        }

        // Hidden or non-economy markets may exist only on their entities.
        for (SectorEntityToken entity : system.getAllEntities()) {
            MarketAPI market = entity.getMarket();
            if (market == null || market.isPlanetConditionMarketOnly()) continue;
            out.add(entity);
            out.add(market.getPrimaryEntity());
            out.addAll(market.getConnectedEntities());
        }

        out.removeIf(entity -> entity == null || entity.isExpired()
                || entity.getContainingLocation() != system);
        return new ArrayList<>(out);
    }

    public static boolean isPopulated(StarSystemAPI system) {
        return !getPopulated(system).isEmpty();
    }

    public static float getInnerLimit(StarSystemAPI system) {
        float furthest = 0f;

        for (SectorEntityToken populated : getPopulated(system)) {
            furthest = Math.max(furthest,
                    Misc.getDistance(center(system), populated.getLocation())
                            + Math.max(0f, populated.getRadius()));
        }

        return furthest + AVOID_RADIUS + ROUTE_PAD + INNER_PAD;
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

            if (canTravel(system, from, at)) return at;
        }

        float bearing = from == null ? 0f : Misc.getAngleInDegrees(center, from);
        for (int i = 0; i < 72; i++) {
            Vector2f at = MathUtils.getPointOnCircumference(center, outer, bearing + i * 5f);
            if (canTravel(system, from, at)) return at;
        }

        return null;
    }

    public static boolean isLegClear(StarSystemAPI system, Vector2f from, Vector2f to) {
        return isLegClear(system, from, to, false);
    }

    public static boolean canTravel(StarSystemAPI system, Vector2f from, Vector2f to) {
        return isLegClear(system, from, to, true);
    }

    protected static boolean isLegClear(StarSystemAPI system, Vector2f from, Vector2f to,
                                        boolean allowEscape) {
        if (to == null) return false;

        for (SectorEntityToken populated : getPopulated(system)) {
            Vector2f market = populated.getLocation();
            float clearance = AVOID_RADIUS + ROUTE_PAD + Math.max(0f, populated.getRadius());
            if (Misc.getDistance(market, to) < clearance) return false;

            if (allowEscape && from != null && Misc.getDistance(market, from) < clearance) {
                // Old saves and moving markets can start a boat inside the exclusion.
                // An escape must move outward throughout, never cut through the market.
                float outward = (from.x - market.x) * (to.x - from.x)
                        + (from.y - market.y) * (to.y - from.y);
                if (outward < 0f) return false;
            } else if (distanceToSegment(market, from, to) < clearance) {
                return false;
            }
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
