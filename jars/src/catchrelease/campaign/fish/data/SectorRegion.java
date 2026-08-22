package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.lwjgl.util.vector.Vector2f;

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

    public boolean isCore() {
        return name().startsWith("CORE");
    }

    public static SectorRegion parse(String name) {
        if (name == null) return null;

        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
