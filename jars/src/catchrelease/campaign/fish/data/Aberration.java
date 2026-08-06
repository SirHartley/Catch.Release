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
 * How badly a specimen holds to reality, based on where it was caught.
 * <p>
 * Three sources thin the fabric - abyssal depth, coronal hypershunts, slipstreams - taken at their
 * strongest rather than summed. Hypershunt/slipstream falloff is measured in light-years from the
 * system, since sector-scale position is what matters, not world units.
 * <p>
 * Not consumed anywhere yet; recorded on every catch so it's available once something reads it.
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

        // Adds spread so two specimens out of the same rupture don't read identically.
        worst += MathUtils.getRandomNumberInRange(-FishConstants.ABERRATION_SPREAD, FishConstants.ABERRATION_SPREAD);

        return MathUtils.clamp(worst, 0f, 1f);
    }

    /** Deepest in the abyss is as far from holding together as anything gets. */
    protected static float getAbyssShare(Vector2f locInHyper, LocationAPI location) {
        float depth = Misc.getAbyssalDepth(locInHyper);

        // An abyssal-tagged system counts as fully in it, even where its own coordinates read shallow.
        if (depth <= 0f && location != null && location.hasTag(Tags.SYSTEM_ABYSSAL)) depth = 1f;

        return MathUtils.clamp(depth, 0f, 1f) * FishConstants.ABERRATION_ABYSS_WEIGHT;
    }

    protected static float getHypershuntShare(Vector2f locInHyper) {
        float nearest = getNearestLY(locInHyper,
                Global.getSector().getCustomEntitiesWithTag(Tags.CORONAL_TAP));

        return falloff(nearest, FishConstants.ABERRATION_HYPERSHUNT_LY)
                * FishConstants.ABERRATION_HYPERSHUNT_WEIGHT;
    }

    /** Slipstreams are ribbons, not points - measures to each terrain's anchor point and takes the nearest. */
    protected static float getSlipstreamShare(Vector2f locInHyper) {
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
