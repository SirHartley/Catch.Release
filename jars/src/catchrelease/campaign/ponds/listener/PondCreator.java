package catchrelease.campaign.ponds.listener;

import catchrelease.campaign.helper.RandomMemoryHelper;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.entities.FishingPondEntityPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.Random;

public class PondCreator {

    private final StarSystemAPI system;
    private int pondsToCreate;
    private final Random random;

    public PondCreator(StarSystemAPI system){
        this.system = system;

        int presentSpots = system.getEntitiesWithTag(FishingPondEntityPlugin.ENTITY_ID).size();
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
        SectorEntityToken pond = system.addCustomEntity(Misc.genUID(), null, FishingPondEntityPlugin.ENTITY_ID,null, 500f, 0f, 0f,new FishingPondEntityPlugin.PondParams(random.nextLong()));

        if (system.isNebula()) pond.setLocation(loc.x, loc.y);
        else {
            float orbitRadius = Misc.getDistance(loc, system.getCenter().getLocation());
            pond.setCircularOrbit(
                    system.getCenter(),
                    random.nextFloat(0f, 360f),
                    orbitRadius,
                    orbitRadius / (20f + random.nextFloat() * 5f)); //similar to vanilla planets
        }
    }

    private Vector2f getPondSpawnLoc() {
        List<PlanetAPI> planets = system.getPlanets();
        Vector2f spawnLoc = null;

        float radius = PondConstants.MIN_DISTANCE;
        if (!system.isNebula()) radius+= system.getStar().getRadius();

        while (spawnLoc == null) {
            boolean tooClose = false;

            // Check all points along the radius for proximity to planets
            for (float angle = 0; angle < 360f; angle += 1f) {
                Vector2f check = MathUtils.getPointOnCircumference(new Vector2f(0, 0), radius, angle);

                for (PlanetAPI planet : planets) {
                    if (Misc.getDistance(planet.getLocation(), check) < PondConstants.MIN_EMPTY_RADIUS_AROUND_POND) {
                        tooClose = true;
                        break;
                    }
                }

                if (tooClose) break;
            }

            if (!tooClose) {
                float randomAngle = MathUtils.getRandomNumberInRange(0f, 360f);
                spawnLoc = MathUtils.getPointOnCircumference(new Vector2f(0, 0), radius, randomAngle);
            }

            radius += PondConstants.DIST_PER_FITTING_ATTEMPT + PondConstants.DIST_PER_FITTING_ATTEMPT * random.nextFloat(); // Increase for next iteration
        }

        return spawnLoc;
    }

}
