package catchrelease.rendering.spiral;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Catch.Release wiring for {@link CircularSpiralWarpRenderer}: every black-hole star in the
 * player's current system is a source. The renderer itself knows nothing about stars, sector
 * traversal or this mod's settings, which is the seam used when the effect moves to another mod.
 */
public final class BlackHoleSpiralWarp {

    public static final String RANGE_SETTING = "catchreleaseBlackHoleSpiralWarpRange";
    public static final float DEFAULT_RANGE = CircularSpiralWarpRenderer.DEFAULT_RANGE;
    public static final String BLACK_HOLE_TYPE = "black_hole";

    protected static final BlackHoleProvider provider = new BlackHoleProvider();
    protected static CircularSpiralWarpRenderer renderer;

    private BlackHoleSpiralWarp() {
    }

    /** Registers the transient pass on each load, reading the merged settings value. */
    public static void install() {
        install(readRange());
    }

    /** Public overload for another host or dev harness that wants to choose the reach directly. */
    public static void install(float range) {
        if (renderer == null) {
            CircularSpiralWarpRenderer.Config config = new CircularSpiralWarpRenderer.Config();
            config.range = Math.max(0f, range);
            renderer = new CircularSpiralWarpRenderer(provider, config);
        } else {
            renderer.setRange(range);
        }

        provider.reset();
        if (!LunaCampaignRenderer.hasRenderer(renderer)) {
            LunaCampaignRenderer.addTransientRenderer(renderer);
        }
    }

    public static float getRange() {
        return renderer == null ? readRange() : renderer.getRange();
    }

    public static void setRange(float range) {
        install(range);
    }

    protected static float readRange() {
        try {
            return Global.getSettings().getFloat(RANGE_SETTING);
        } catch (RuntimeException ignored) {
            return DEFAULT_RANGE;
        }
    }

    /** Caches the star list until the player changes location; star vectors themselves stay live. */
    protected static final class BlackHoleProvider
            implements CircularSpiralWarpRenderer.SourceProvider {

        protected LocationAPI location;
        protected final List<CircularSpiralWarpRenderer.Source> cached = new ArrayList<>();

        @Override
        public void collect(List<CircularSpiralWarpRenderer.Source> out) {
            LocationAPI current = Global.getSector() == null ? null
                    : Global.getSector().getCurrentLocation();

            if (current != location) rebuild(current);
            out.addAll(cached);
        }

        protected void rebuild(LocationAPI current) {
            location = current;
            cached.clear();

            if (!(current instanceof StarSystemAPI system)) return;

            for (PlanetAPI planet : system.getPlanets()) {
                if (planet == null || !planet.isStar() || planet.getSpec() == null) continue;
                if (!BLACK_HOLE_TYPE.equals(planet.getSpec().getPlanetType())) continue;

                cached.add(new CircularSpiralWarpRenderer.Source(planet.getLocation()));
            }
        }

        protected void reset() {
            location = null;
            cached.clear();
        }
    }
}
