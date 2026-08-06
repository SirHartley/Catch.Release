package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * How badly a specimen holds to reality, from where it was taken - the inverse of the coherence the
 * player is shown on it.
 * <p>
 * Four things thin the local fabric, taken at their strongest rather than summed. Abyss reads
 * directly off depth; black hole, hypershunt and slipstream fall off with distance in light-years
 * from the system (not world units), since what matters is which part of the sector this is.
 */
public class Aberration {

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

        return MathUtils.clamp(worst, 0f, 1f);
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
        float nearest = getNearestLY(locInHyper,
                Global.getSector().getCustomEntitiesWithTag(Tags.CORONAL_TAP));

        return falloff(nearest, FishConstants.ABERRATION_HYPERSHUNT_LY)
                * FishConstants.ABERRATION_HYPERSHUNT_WEIGHT;
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

        for (SectorEntityToken tap : Global.getSector().getCustomEntitiesWithTag(Tags.CORONAL_TAP)) {
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
