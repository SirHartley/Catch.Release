package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * How badly a specimen holds to reality, from where it was taken - the inverse of the coherence the
 * player is shown on it.
 * <p>
 * Several things thin the local fabric, taken at their strongest rather than summed. Abyss reads
 * directly off depth; everything else falls off with distance in light-years from the system (not
 * world units), since what matters is which part of the sector this is.
 * <p>
 * A gate counts twice over, because it is two different objects. Dormant it is a hole with the lid
 * on - three light-years and not much of it. Lit, something is being held open between here and
 * somewhere else, and it reads harder than anything but the abyss. Which is why gates cannot go
 * through {@link #getNearestLY} like the rest: the nearest gate is not necessarily the worst one,
 * and a dormant gate overhead can matter less than a live one two systems away.
 * <p>
 * <b>Foreign tags.</b> Other mods put things in the sector that belong on this list, and the list
 * names them by tag - see {@link #FOREIGN_GATES} and below. Nothing here depends on any of those
 * mods being installed: a tag nobody has registered simply matches nothing, so the lookup costs an
 * empty list and the reading is unchanged.
 */
public class Aberration {

    /**
     * Somebody else's gates, treated as gates.
     * <p>
     * A second network of doors between here and elsewhere is the same fact about the fabric as the
     * first one, whoever built it.
     */
    public static final String[] FOREIGN_GATES = {"bifrost"};

    /** Somebody else's hypershunt, treated as a hypershunt, for the same reason. */
    public static final String[] FOREIGN_HYPERSHUNTS = {"aotd_hypershunt_receiver"};

    /**
     * Machines large enough to work on a planet rather than on a ship.
     * <p>
     * Not a hole in anything - a mining station with a laser that cuts worlds is only leaning on
     * local space very hard, so it reads short and shallow next to the doors.
     */
    public static final String[] FOREIGN_ENGINES = {"aotd_pluto_station"};

    /** For a pond, or anything else that was taken somewhere. */
    public static float of(SectorEntityToken where) {
        if (where == null) return 0f;

        return at(where.getLocationInHyperspace(), where.getContainingLocation());
    }

    /**
     * @param locInHyper where the system sits on the sector map
     * @param location   the system itself, for the abyssal tag its own terrain carries
     */
    public static float at(Vector2f locInHyper, LocationAPI location) {
        //two specimens out of the same rupture should not read identically
        return MathUtils.clamp(baseAt(locInHyper, location)
                + MathUtils.getRandomNumberInRange(-FishConstants.ABERRATION_SPREAD,
                        FishConstants.ABERRATION_SPREAD), 0f, 1f);
    }

    /**
     * The place's own reading, with none of the per-catch jitter on it.
     * <p>
     * What a habitat is tested against, since a species that lives where reality is thin lives there
     * whether or not this particular specimen rolled high - the spread is about the specimen, and
     * putting it into the habitat would make a fish's range flicker between two spawns in one pond.
     */
    public static float baseAt(Vector2f locInHyper, LocationAPI location) {
        if (locInHyper == null) return 0f;

        float worst = getAbyssShare(locInHyper, location);
        worst = Math.max(worst, getBlackHoleShare(locInHyper));
        worst = Math.max(worst, getHypershuntShare(locInHyper));
        worst = Math.max(worst, getSlipstreamShare(locInHyper));
        worst = Math.max(worst, getGateShare(locInHyper, false));
        worst = Math.max(worst, getEngineShare(locInHyper));

        return MathUtils.clamp(worst, 0f, 1f);
    }

    /**
     * Names the strongest source at a place - "the abyss", "a collapsed star", "a hypershunt",
     * "a slipstream" - or null when nothing there is worth blaming. The cut is the same one below
     * which the coherence labels read "stable", so a named source and a bad reading always arrive
     * together.
     */
    public static String dominantSourceAt(Vector2f locInHyper, LocationAPI location) {
        if (locInHyper == null) return null;

        float best = FishConstants.COHERENCE_OVERLAY_FLOOR;
        String name = null;

        float share = getAbyssShare(locInHyper, location);
        if (share > best) { best = share; name = "the abyss"; }

        share = getBlackHoleShare(locInHyper);
        if (share > best) { best = share; name = "a collapsed star"; }

        share = getHypershuntShare(locInHyper);
        if (share > best) { best = share; name = "a hypershunt"; }

        share = getSlipstreamShare(locInHyper);
        if (share > best) { best = share; name = "a slipstream"; }

        share = getGateShare(locInHyper, false);
        if (share > best) { best = share; name = "a gate"; }

        share = getEngineShare(locInHyper);
        if (share > best) { best = share; name = "something built too large"; }

        return name;
    }

    /**
     * A collapsed star bends what is around it, so what comes out of the water near one is bent too.
     * <p>
     * Measured to the system rather than to the star itself: at this scale they are the same point,
     * and a system's own coordinates are what the falloff is in light-years of. Full strength for
     * anything caught in the system, tailing off into its neighbours.
     */
    protected static float getBlackHoleShare(Vector2f locInHyper) {
        float nearest = Float.MAX_VALUE;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!system.hasBlackHole()) continue;

            nearest = Math.min(nearest, Misc.getDistanceLY(locInHyper, system.getLocation()));
        }

        return falloff(nearest, FishConstants.ABERRATION_BLACKHOLE_LY)
                * FishConstants.ABERRATION_BLACKHOLE_WEIGHT;
    }

    /** Deepest in the abyss is as far from holding together as anything gets. */
    protected static float getAbyssShare(Vector2f locInHyper, LocationAPI location) {
        float depth = Misc.getAbyssalDepth(locInHyper);

        //an abyssal system reads as fully in it even where the depth at its own coordinates does not
        if (depth <= 0f && location != null && location.hasTag(Tags.SYSTEM_ABYSSAL)) depth = 1f;

        return MathUtils.clamp(depth, 0f, 1f) * FishConstants.ABERRATION_ABYSS_WEIGHT;
    }

    protected static float getHypershuntShare(Vector2f locInHyper) {
        float nearest = getNearestLY(locInHyper, taggedWith(Tags.CORONAL_TAP, FOREIGN_HYPERSHUNTS));

        return falloff(nearest, FishConstants.ABERRATION_HYPERSHUNT_LY)
                * FishConstants.ABERRATION_HYPERSHUNT_WEIGHT;
    }

    /**
     * The doors, each read on its own terms.
     * <p>
     * Per-gate rather than nearest-gate, because a live one and a dormant one are not the same
     * source measured from different distances - they have different reaches and different depths,
     * so the nearest is not reliably the worst.
     *
     * @param discoveredOnly for the route planner, which may only reason about what the player has
     *                       actually seen
     */
    protected static float getGateShare(Vector2f locInHyper, boolean discoveredOnly) {
        float worst = 0f;

        for (SectorEntityToken gate : taggedWith(Tags.GATE, FOREIGN_GATES)) {
            if (discoveredOnly && gate.isDiscoverable()) continue;

            boolean active = isGateActive(gate);

            float share = falloff(
                    Misc.getDistanceLY(locInHyper, gate.getLocationInHyperspace()),
                    active ? FishConstants.ABERRATION_GATE_ACTIVE_LY
                            : FishConstants.ABERRATION_GATE_LY)
                    * (active ? FishConstants.ABERRATION_GATE_ACTIVE_WEIGHT
                            : FishConstants.ABERRATION_GATE_WEIGHT);

            worst = Math.max(worst, share);
        }

        return worst;
    }

    /**
     * Whether anything is coming through this one.
     * <p>
     * Vanilla's own plugin is asked where there is one. A foreign gate is not that class and asking
     * it directly would throw, so it is read off the sector-wide switch instead - which is the same
     * question one step out, and the best answer available without knowing whose gate it is.
     */
    protected static boolean isGateActive(SectorEntityToken gate) {
        if (gate.getCustomPlugin() instanceof GateEntityPlugin plugin) return plugin.isActive();

        return GateEntityPlugin.areGatesActive();
    }

    /** Machines big enough to lean on local space, which is not the same as opening it. */
    protected static float getEngineShare(Vector2f locInHyper) {
        float nearest = getNearestLY(locInHyper, taggedWith(null, FOREIGN_ENGINES));

        return falloff(nearest, FishConstants.ABERRATION_ENGINE_LY)
                * FishConstants.ABERRATION_ENGINE_WEIGHT;
    }

    /**
     * Everything carrying any of these tags, vanilla's and other mods' alike.
     * <p>
     * {@code getEntitiesWithTag} rather than the custom-entity version, because a foreign mod is
     * free to have built its gate out of something other than a custom entity, and an absent tag
     * returns nothing rather than failing - which is what keeps all of this optional.
     */
    protected static List<SectorEntityToken> taggedWith(String vanilla, String[] foreign) {
        List<SectorEntityToken> out = new ArrayList<>();

        if (vanilla != null) out.addAll(Global.getSector().getEntitiesWithTag(vanilla));

        for (String tag : foreign) out.addAll(Global.getSector().getEntitiesWithTag(tag));

        return out;
    }

    /**
     * Route planner's read: deterministic (no spread), counting only what the player has found -
     * slipstreams always count (visible the moment they run), but an undiscovered hypershunt or
     * an abyss never entered can't steer a plan the player is meant to reason about.
     */
    public static float knownInstability(StarSystemAPI system) {
        if (system == null || system.getLocation() == null) return 0f;

        Vector2f loc = system.getLocation();

        float worst = getSlipstreamShare(loc);

        //no discovery check on this one: a system's star is drawn on the sector map from the start,
        //so a black hole is a thing the player can already see and route around
        worst = Math.max(worst, getBlackHoleShare(loc));

        worst = Math.max(worst, getDiscoveredHypershuntShare(loc));

        //same rule as the hypershunt: a gate the player has not found cannot steer a plan they are
        //meant to be able to reason about
        worst = Math.max(worst, getGateShare(loc, true));

        worst = Math.max(worst, getEngineShare(loc));

        if (hasEnteredAbyss()) worst = Math.max(worst, getAbyssShare(loc, system));

        return MathUtils.clamp(worst, 0f, 1f);
    }

    /** Whether the player has ever stood in the abyss - before that, its depth is hearsay. */
    protected static boolean hasEnteredAbyss() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.SYSTEM_ABYSSAL) && system.isEnteredByPlayer()) return true;
        }

        return false;
    }

    /** The hypershunt share counting only taps the player has actually laid eyes on. */
    protected static float getDiscoveredHypershuntShare(Vector2f locInHyper) {
        float nearest = Float.MAX_VALUE;

        for (SectorEntityToken tap : taggedWith(Tags.CORONAL_TAP, FOREIGN_HYPERSHUNTS)) {
            if (tap.isDiscoverable()) continue;

            nearest = Math.min(nearest, Misc.getDistanceLY(locInHyper, tap.getLocationInHyperspace()));
        }

        return falloff(nearest, FishConstants.ABERRATION_HYPERSHUNT_LY)
                * FishConstants.ABERRATION_HYPERSHUNT_WEIGHT;
    }

    /** Slipstreams are ribbons, not points - measures to where the terrain is anchored and takes
     *  the nearest, close enough for a rough number. Public since the route planner discounts
     *  travel along them. */
    public static float getSlipstreamShare(Vector2f locInHyper) {
        float nearest = Float.MAX_VALUE;

        for (CampaignTerrainAPI terrain : Global.getSector().getHyperspace().getTerrainCopy()) {
            if (!Terrain.SLIPSTREAM.equals(terrain.getType())) continue;

            nearest = Math.min(nearest, Misc.getDistanceLY(locInHyper, terrain.getLocation()));
        }

        return falloff(nearest, FishConstants.ABERRATION_SLIPSTREAM_LY)
                * FishConstants.ABERRATION_SLIPSTREAM_WEIGHT;
    }

    protected static float getNearestLY(Vector2f locInHyper, Iterable<SectorEntityToken> entities) {
        float nearest = Float.MAX_VALUE;

        for (SectorEntityToken entity : entities) {
            nearest = Math.min(nearest, Misc.getDistanceLY(locInHyper, entity.getLocationInHyperspace()));
        }

        return nearest;
    }

    /** 1 on top of it, 0 at the given range and beyond, curved so most of the effect is close in. */
    protected static float falloff(float distanceLY, float rangeLY) {
        if (distanceLY >= rangeLY || rangeLY <= 0f) return 0f;

        float near = 1f - distanceLY / rangeLY;

        return near * near;
    }
}
