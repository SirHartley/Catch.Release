package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.lwjgl.util.vector.Vector2f;

/**
 * Where in the sector a system sits, for deciding what swims there.
 * <p>
 * Eight rectangular sections (inner/outer band, quartered by direction) plus {@link #ABYSSAL}, a
 * system property that overrides geometry. Dividing lines are
 * {@link FishConstants#CORE_BAND_HALF_WIDTH} and {@link FishConstants#CORE_BAND_HALF_HEIGHT},
 * measured from the sector's centre.
 */
public enum SectorRegion {

    CORE_NE,
    CORE_NW,
    CORE_SE,
    CORE_SW,

    RIM_NE,
    RIM_NW,
    RIM_SE,
    RIM_SW,

    ABYSSAL;

    /**
     * The region a location belongs to, or null if it has no position to speak of.
     *
     * @param location the star system, not the entity inside it - its own hyperspace position is what matters
     */
    public static SectorRegion of(LocationAPI location) {
        if (location == null) return null;

        if (location.hasTag(Tags.SYSTEM_ABYSSAL)) return ABYSSAL;

        Vector2f position = location.getLocation();
        if (position == null) return null;

        boolean core = Math.abs(position.x) <= FishConstants.CORE_BAND_HALF_WIDTH
                && Math.abs(position.y) <= FishConstants.CORE_BAND_HALF_HEIGHT;

        boolean north = position.y >= 0f;
        boolean east = position.x >= 0f;

        if (core) {
            if (north) return east ? CORE_NE : CORE_NW;
            return east ? CORE_SE : CORE_SW;
        }

        if (north) return east ? RIM_NE : RIM_NW;
        return east ? RIM_SE : RIM_SW;
    }

    /**
     * Corner of the sector this region covers, as {minX, minY, maxX, maxY} - same arithmetic as
     * {@link #of(LocationAPI)}, read in reverse, so a map drawn from these matches exactly. Rim
     * quadrants use the caller-supplied sector extent as their outer edge. ABYSSAL is a system tag,
     * not a place, and returns null.
     */
    public float[] getBounds(float sectorHalfWidth, float sectorHalfHeight) {
        if (this == ABYSSAL) return null;

        float coreW = FishConstants.CORE_BAND_HALF_WIDTH;
        float coreH = FishConstants.CORE_BAND_HALF_HEIGHT;

        boolean core = name().startsWith("CORE");
        boolean north = name().endsWith("NE") || name().endsWith("NW");
        boolean east = name().endsWith("NE") || name().endsWith("SE");

        float minX = east ? 0f : (core ? -coreW : -sectorHalfWidth);
        float maxX = east ? (core ? coreW : sectorHalfWidth) : 0f;
        float minY = north ? 0f : (core ? -coreH : -sectorHalfHeight);
        float maxY = north ? (core ? coreH : sectorHalfHeight) : 0f;

        return new float[]{minX, minY, maxX, maxY};
    }

    /** Whether this region is one of the four inner quadrants. */
    public boolean isCore() {
        return name().startsWith("CORE");
    }

    /** Parses a name from the fish table, or null if it is not one of ours. */
    public static SectorRegion parse(String name) {
        if (name == null) return null;

        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
