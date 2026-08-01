package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.lwjgl.util.vector.Vector2f;

/**
 * Where in the sector a system sits, for the purpose of deciding what swims there.
 * <p>
 * Eight rectangular sections - an inner band and an outer one, each quartered by direction - plus
 * {@link #ABYSSAL}, which is not a place on the map but a property of the system and so wins over
 * the geometry. The dividing lines are {@link FishConstants#CORE_BAND_HALF_WIDTH} and
 * {@link FishConstants#CORE_BAND_HALF_HEIGHT}, measured from the sector's centre.
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
     * @param location the star system, not the entity inside it - the position that matters is the
     *                 system's own place in hyperspace
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
