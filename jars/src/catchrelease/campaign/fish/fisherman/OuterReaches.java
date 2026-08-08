package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Where in a system a fishing boat is willing to be, and how it gets from one such place to the
 * next.
 * <p>
 * The band is everything past the last inhabited world and out to a little beyond the furthest thing
 * that orbits - so the far-flung outer planets are on the route and the populated inner system is
 * not. That is deliberate: the fishing is out where the fabric is thin, and a trawler parked over
 * Jangala reads as traffic rather than as a trade.
 * <p>
 * The routing is the part that could not be left to {@code PATROL_SYSTEM}. A patrol wanders the
 * whole system and will happily cut straight across a populated orbit on its way somewhere, so it is
 * not enough to choose good destinations - the <b>leg</b> has to be clear too, since a fleet flies
 * its assignment in a straight line. {@link #pick} rejects both, and a system so crowded that
 * nothing passes falls back to the far edge, which is the one place always clear of everything.
 */
public class OuterReaches {

    /** How wide a berth an inhabited world gets, in world units. */
    public static final float AVOID_RADIUS = 2500f;

    /** Past the last of them, and past the last thing in orbit. */
    public static final float INNER_PAD = 1500f;
    public static final float OUTER_PAD = 2500f;

    /** The band is never thinner than this, however tightly packed the system is. */
    public static final float MIN_BAND = 3000f;

    /** A system with nothing to measure still has to have somewhere to be. */
    public static final float DEFAULT_OUTER = 14000f;

    /** How many destinations are tried before the far edge is taken instead. */
    public static final int TRIES = 24;

    /** How long one leg is allowed to take before it is reissued. */
    public static final float LEG_DAYS = 25f;

    /** What the band is measured from - the star, not the origin, which a binary moves off. */
    public static Vector2f center(StarSystemAPI system) {
        if (system == null || system.getCenter() == null) return new Vector2f();

        return new Vector2f(system.getCenter().getLocation());
    }

    /** Every inhabited world in a system - what the boats keep clear of. */
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

    /** Whether a system is one of the inhabited ones the standing boats are posted to. */
    public static boolean isPopulated(StarSystemAPI system) {
        return !getPopulated(system).isEmpty();
    }

    /** Past the outermost inhabited world, with its berth on top. */
    public static float getInnerLimit(StarSystemAPI system) {
        float furthest = 0f;

        for (SectorEntityToken populated : getPopulated(system)) {
            furthest = Math.max(furthest,
                    Misc.getDistance(center(system), populated.getLocation()));
        }

        return furthest + AVOID_RADIUS + INNER_PAD;
    }

    /** A little past the furthest thing that orbits, which is where the system stops being one. */
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

    /**
     * Where a boat may actually be put, given somewhere it would like to be.
     * <p>
     * The band is a rule about <i>inhabited</i> systems and only about those. Where there are
     * people, a fishing boat belongs past the last of them - anything closer in is either traffic
     * over somebody's world or, at the extreme this exists to stop, a trawler parked against the
     * star. Where there is nobody, there is nothing to keep clear of and no distance rule at all:
     * the whole system is the water, and a boat may be anywhere in it.
     * <p>
     * Clamped along the point's own bearing from the star rather than re-rolled, so the nearest
     * legal spot is the one taken and a placement that was chosen for a reason - to be a short way
     * off the player, say - keeps as much of that reason as the band allows.
     */
    public static Vector2f place(StarSystemAPI system, Vector2f preferred) {
        if (system == null || preferred == null) return preferred;
        if (!isPopulated(system)) return preferred;

        Vector2f center = center(system);

        float inner = getInnerLimit(system);
        float outer = getOuterLimit(system);

        float distance = Misc.getDistance(center, preferred);
        if (distance >= inner && distance <= outer) return preferred;

        //a point sitting on the star has no bearing to keep, so it is given one
        float bearing = distance <= 1f
                ? MathUtils.getRandomNumberInRange(0f, 360f)
                : Misc.getAngleInDegrees(center, preferred);

        return MathUtils.getPointOnCircumference(center,
                MathUtils.clamp(distance, inner, outer), bearing);
    }

    /**
     * Somewhere to work next: a point in the band whose leg from {@code from} clears every
     * inhabited world.
     * <p>
     * Both ends are tested because a fleet flies its assignment straight - a destination out in the
     * dark reached by cutting through the inner system is the thing this class exists to stop.
     */
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

        //nothing passed: the far edge is clear of everything by construction, so the boat goes and
        //sits out there rather than standing still or picking a leg it was told not to fly
        return MathUtils.getPointOnCircumference(center, outer,
                MathUtils.getRandomNumberInRange(0f, 360f));
    }

    /** Whether a straight run between two points passes wide of everything inhabited. */
    public static boolean isLegClear(StarSystemAPI system, Vector2f from, Vector2f to) {
        for (SectorEntityToken populated : getPopulated(system)) {
            if (distanceToSegment(populated.getLocation(), from, to) < AVOID_RADIUS) return false;
        }

        return true;
    }

    /** Closest approach of a point to a line segment - the leg test, in one line of algebra. */
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
