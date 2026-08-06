package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * How badly a specimen holds to reality, worked out from where it was taken.
 * <p>
 * Three things thin the local fabric, and being near any of them is enough - so the three are taken
 * at their strongest rather than added up. The abyss is the worst of them and reads directly off the
 * depth; a coronal hypershunt and a slipstream both fall off with distance, measured in light-years
 * from the system rather than in world units, since what matters is which part of the sector this is.
 * <p>
 * Nothing consumes this yet. It is recorded on every catch so that when something does, the fish
 * already in the hold have it.
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
        if (locInHyper == null) return 0f;

        float worst = getAbyssShare(locInHyper, location);
        worst = Math.max(worst, getHypershuntShare(locInHyper));
        worst = Math.max(worst, getSlipstreamShare(locInHyper));

        //two specimens out of the same rupture should not read identically
        worst += MathUtils.getRandomNumberInRange(-FishConstants.ABERRATION_SPREAD, FishConstants.ABERRATION_SPREAD);

        return MathUtils.clamp(worst, 0f, 1f);
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
     * The route planner's read: how unstable a system is known to be, deterministically - no
     * spread - and counting only what the player has actually found. Slipstreams always count,
     * since they are on every map the moment they run; a hypershunt that has never been
     * discovered and an abyss never entered cannot steer a plan the player is meant to be able
     * to reason about.
     */
    public static float knownInstability(com.fs.starfarer.api.campaign.StarSystemAPI system) {
        if (system == null || system.getLocation() == null) return 0f;

        Vector2f loc = system.getLocation();

        float worst = getSlipstreamShare(loc);
        worst = Math.max(worst, getDiscoveredHypershuntShare(loc));

        if (hasEnteredAbyss()) worst = Math.max(worst, getAbyssShare(loc, system));

        return MathUtils.clamp(worst, 0f, 1f);
    }

    /** Whether the player has ever stood in the abyss - before that, its depth is hearsay. */
    protected static boolean hasEnteredAbyss() {
        for (com.fs.starfarer.api.campaign.StarSystemAPI system
                : Global.getSector().getStarSystems()) {

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

    /**
     * Slipstreams are ribbons rather than points, so this measures to where the terrain is anchored
     * and takes the nearest. Close enough for a number that only says roughly how thin it is here.
     * Public because the route planner discounts travel along them.
     */
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
