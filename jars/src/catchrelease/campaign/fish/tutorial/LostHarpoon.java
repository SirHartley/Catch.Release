package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Somebody else's harpoon, still transmitting, stuck in a world nobody lives on.
 * <p>
 * The way in that is a <i>find</i> rather than a conversation - the same shape as the data package
 * that opens the Academy chain, and for the same reason: the strongest hook for a trade nobody has
 * heard of is physical evidence of somebody already doing it. There is a line, a transponder, and a
 * great deal of very wrong water around both.
 * <p>
 * The water is the argument. The harpoon sits inside one of the mod's own ponds, which is the
 * feature the whole mod is built on rendering, so the player's first sight of a rupture is a
 * hundred metres from the thing that was used to fish it. Nothing has to be explained; a working
 * pond drawn around a broken piece of somebody's gear says it.
 * <p>
 * Placed once, at the start of a campaign, on an uninhabited world a few jumps out - close enough
 * to be stumbled into on the way to somewhere, far enough that stumbling into it is a small event.
 * It carries a sensor profile so the signal is what finds it, rather than it simply being on the
 * map from the first day.
 */
public class LostHarpoon extends BaseCustomEntityPlugin {

    /** Puts it somewhere, once per campaign - and not at all for one that is long past needing it. */
    public static void place() {
        if (Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.HARPOON_PLACED_KEY)) {

            return;
        }

        if (FishingIntro.isAtLeast(FishingIntro.DONE)) return;

        //marked before the search rather than after: a campaign with nowhere suitable should not
        //re-run the whole sweep on every load for the rest of its life
        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.HARPOON_PLACED_KEY, true);

        PlanetAPI host = pickWorld();
        if (host == null) return;

        StarSystemAPI system = host.getStarSystem();
        if (system == null) return;

        float angle = MathUtils.getRandomNumberInRange(0f, 360f);

        SectorEntityToken harpoon = system.addCustomEntity(Misc.genUID(),
                TutorialConstants.HARPOON_NAME, TutorialConstants.HARPOON_ENTITY_ID, null, null);

        //on the limb rather than in orbit - it did not arrive here under power, it stuck
        harpoon.setCircularOrbit(host, angle,
                host.getRadius() + TutorialConstants.HARPOON_SURFACE_PAD,
                TutorialConstants.HARPOON_ORBIT_DAYS);

        harpoon.setSensorProfile(TutorialConstants.HARPOON_SENSOR_PROFILE);
        harpoon.setDiscoverable(true);

        warpTheWater(system, host, angle);
    }

    /**
     * An uninhabited world, a few light-years out from the middle of things.
     * <p>
     * Uninhabited because a harpoon nobody came back for does not stay unclaimed above a colony,
     * and out a way because being handed the hook before leaving the starting system would make it
     * the tutorial rather than one of three ways into it.
     */
    protected static PlanetAPI pickWorld() {
        WeightedRandomPicker<PlanetAPI> picker = new WeightedRandomPicker<>();

        Vector2f middle = new Vector2f();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            if (system.hasTag(Tags.SYSTEM_ABYSSAL)) continue;
            if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) continue;
            if (!Misc.getMarketsInLocation(system).isEmpty()) continue;

            float distance = Misc.getDistanceLY(middle, system.getLocation());
            if (distance < TutorialConstants.HARPOON_MIN_LY) continue;
            if (distance > TutorialConstants.HARPOON_MAX_LY) continue;

            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar() || planet.isGasGiant()) continue;

                picker.add(planet, 1f);
            }
        }

        return picker.pick();
    }

    /**
     * The rupture it was thrown into, still open.
     * <p>
     * Off to one side of the world rather than on top of it, because a pond drawn over a planet is
     * a pond nobody can see. Same terrain, same renderer, same everything the rest of the mod
     * uses - this is a first look at the real feature, not a set piece that behaves differently.
     */
    protected static void warpTheWater(StarSystemAPI system, PlanetAPI host, float angle) {
        Vector2f at = MathUtils.getPointOnCircumference(host.getLocation(),
                host.getRadius() + TutorialConstants.HARPOON_POND_OFFSET, angle);

        SectorEntityToken pond = system.addTerrain(MaskedFishingPondTerrainPlugin.TERRAIN_ID,
                new MaskedFishingPondTerrainPlugin.PondParams(
                        Misc.getRandom(system.getId().hashCode(), 11).nextLong(),
                        PondConstants.POND_RADIUS));

        pond.setLocation(at.x, at.y);
    }

    /** Whether an entity is the wreck, for the dialog router. */
    public static boolean isLostHarpoon(SectorEntityToken entity) {
        return entity != null
                && TutorialConstants.HARPOON_ENTITY_ID.equals(entity.getCustomEntityType());
    }
}
