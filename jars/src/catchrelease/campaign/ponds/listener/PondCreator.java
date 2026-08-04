package catchrelease.campaign.ponds.listener;

import catchrelease.memory.RandomMemoryHelper;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.terrain.NebulaTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

public class PondCreator {

    private final StarSystemAPI system;
    private int pondsToCreate;
    private final Random random;

    public PondCreator(StarSystemAPI system){
        this.system = system;

        int presentSpots = system.getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID).size();
        int planetAmt = system.getPlanets().size();
        this.pondsToCreate = PondConstants.MIN_POND_AMT_PER_SYSTEM + (int) Math.floor(planetAmt / PondConstants.PLANETS_PER_ADDITIONAL_POND) - presentSpots;

        this.random = RandomMemoryHelper.getRandom(system);
    }

    public void createPonds(){
        if (pondsToCreate <= 0) return;

        while (pondsToCreate > 0){
            Vector2f loc = getPondSpawnLoc();
            spawnPond(loc);
            pondsToCreate--;
        }
    }

    public void spawnPond(Vector2f loc){
        //terrain rather than a custom entity: no id, name or radius arguments - the plugin names the
        //entity and sizes it from the params in its init()
        SectorEntityToken pond = system.addTerrain(
                MaskedFishingPondTerrainPlugin.TERRAIN_ID,
                new MaskedFishingPondTerrainPlugin.PondParams(random.nextLong(), PondConstants.POND_RADIUS));
        pond.setLocation(loc.x, loc.y);

        //Decided that ponds should not orbit because it sucks to follow them around
        /*if (system.isNebula()) pond.setLocation(loc.x, loc.y);
        else {
            float orbitRadius = Misc.getDistance(loc, system.getCenter().getLocation());
            pond.setCircularOrbit(
                    system.getCenter(),
                    random.nextFloat(0f, 360f),
                    orbitRadius,
                    orbitRadius / (20f + random.nextFloat() * 5f)); //similar to vanilla planets
        }*/
    }

    /**
     * A spot with nothing in it, working outwards until one turns up.
     * <p>
     * Every bearing on a ring is tried now, and the one that passes is the one used. It used to
     * throw away the whole radius the moment any single bearing on it sat near a planet, and then
     * roll a fresh angle at random on whichever ring survived - so the place that was tested and the
     * place the pond went were not the same place, and the test said very little about either.
     * <p>
     * Each ring is also walked from a random bearing rather than from zero, so ponds in different
     * systems do not all end up due east of their star.
     */
    private Vector2f getPondSpawnLoc() {
        float radius = PondConstants.MIN_DISTANCE;
        if (!system.isNebula()) radius += system.getStar().getRadius();

        //bounded rather than while(true): a system packed enough to have nowhere left would
        //otherwise hang the sector generating itself
        for (int ring = 0; ring < PondConstants.MAX_FITTING_ATTEMPTS; ring++) {
            float offset = MathUtils.getRandomNumberInRange(0f, 360f);

            for (float step = 0; step < 360f; step += PondConstants.FITTING_ANGLE_STEP) {
                Vector2f check = MathUtils.getPointOnCircumference(new Vector2f(0, 0), radius, offset + step);

                if (isClear(check)) return check;
            }

            radius += PondConstants.DIST_PER_FITTING_ATTEMPT
                    + PondConstants.DIST_PER_FITTING_ATTEMPT * random.nextFloat();
        }

        //nowhere clean left. Out past everything is still better than on top of something
        return MathUtils.getPointOnCircumference(new Vector2f(0, 0), radius,
                MathUtils.getRandomNumberInRange(0f, 360f));
    }

    /** Whether a rupture would sit on its own here, clear of everything it ought to be clear of. */
    private boolean isClear(Vector2f point) {
        for (PlanetAPI planet : system.getPlanets()) {
            if (Misc.getDistance(planet.getLocation(), point) < PondConstants.MIN_EMPTY_RADIUS_AROUND_POND) {
                return false;
            }
        }

        //the check that was not being made at all. Ponds were only ever measured against planets, so
        //nothing stopped the second one landing on the first - and since each search restarted at the
        //same radius and picked its own angle on it, that is exactly what they tended to do
        for (SectorEntityToken other : system.getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID)) {
            if (Misc.getDistance(other.getLocation(), point) < PondConstants.MIN_POND_SEPARATION) {
                return false;
            }
        }

        return !isInNebula(point);
    }

    /**
     * Whether a nebula reaches this spot.
     * <p>
     * Asked of the terrain rather than measured off its centre: a nebula is a grid of tiles with
     * holes in it, so the distance to the terrain entity says nothing about whether there is
     * anything at any particular point. Its own containsPoint walks the tiles, and the radius handed
     * to it is what turns "under one" into "under one or near one".
     */
    private boolean isInNebula(Vector2f point) {
        for (CampaignTerrainAPI terrain : system.getTerrainCopy()) {
            if (!(terrain.getPlugin() instanceof NebulaTerrainPlugin)) continue;

            if (terrain.getPlugin().containsPoint(point, PondConstants.MIN_NEBULA_CLEARANCE)) return true;
        }

        return false;
    }

}
