package catchrelease.campaign.ponds.listener;

import catchrelease.memory.RandomMemoryHelper;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
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
        //terrain, not a custom entity: no id/name/radius args - the plugin names and sizes the
        //entity from the params in its init()
        SectorEntityToken pond = system.addTerrain(
                MaskedFishingPondTerrainPlugin.TERRAIN_ID,
                new MaskedFishingPondTerrainPlugin.PondParams(random.nextLong(), PondConstants.POND_RADIUS));
        pond.setLocation(loc.x, loc.y);

        //ponds intentionally do not orbit - following them around is tedious
    }

    /** A spot with nothing in it, working outwards ring by ring, each walked from a random start
     * so ponds do not all end up due east of their star. */
    private Vector2f getPondSpawnLoc() {
        float radius = PondConstants.MIN_DISTANCE;
        if (!system.isNebula()) radius += system.getStar().getRadius();

        //bounded rather than while(true): a packed system with nowhere left would otherwise hang
        //sector generation
        for (int ring = 0; ring < PondConstants.MAX_FITTING_ATTEMPTS; ring++) {
            float offset = MathUtils.getRandomNumberInRange(0f, 360f);

            for (float step = 0; step < 360f; step += PondConstants.FITTING_ANGLE_STEP) {
                Vector2f check = MathUtils.getPointOnCircumference(new Vector2f(0, 0), radius, offset + step);

                if (isClear(check)) return check;
            }

            radius += PondConstants.DIST_PER_FITTING_ATTEMPT
                    + PondConstants.DIST_PER_FITTING_ATTEMPT * random.nextFloat();
        }

        //nowhere clean left - out past everything beats landing on top of something
        return MathUtils.getPointOnCircumference(new Vector2f(0, 0), radius,
                MathUtils.getRandomNumberInRange(0f, 360f));
    }

    /** Whether a pond would sit clear of planets and other ponds here. */
    private boolean isClear(Vector2f point) {
        for (PlanetAPI planet : system.getPlanets()) {
            if (Misc.getDistance(planet.getLocation(), point) < PondConstants.MIN_EMPTY_RADIUS_AROUND_POND) {
                return false;
            }
        }

        for (SectorEntityToken other : system.getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID)) {
            if (Misc.getDistance(other.getLocation(), point) < PondConstants.MIN_POND_SEPARATION) {
                return false;
            }
        }

        return !isInNebula(point) && !isOnRingBand(point);
    }

    /** Whether a ring band runs through this spot. Terrain rings answer through their plugin;
     * art-only rings have none, so their band is measured by hand off focus/radius/width. */
    private boolean isOnRingBand(Vector2f point) {
        for (CampaignTerrainAPI terrain : system.getTerrainCopy()) {
            if (!(terrain.getPlugin() instanceof BaseRingTerrain)) continue;

            //pond radius rides along: checks whether the pond's edge reaches the band, not its centre
            if (terrain.getPlugin().containsPoint(point,
                    PondConstants.POND_RADIUS + PondConstants.MIN_RING_CLEARANCE)) {
                return true;
            }
        }

        for (SectorEntityToken token : system.getAllEntities()) {
            if (!(token instanceof RingBandAPI)) continue;

            RingBandAPI ring = (RingBandAPI) token;
            SectorEntityToken focus = ring.getFocus();
            if (focus == null) continue;

            float distance = Misc.getDistance(focus.getLocation(), point);
            float halfBand = ring.getBandWidthInEngine() * 0.5f
                    + PondConstants.POND_RADIUS + PondConstants.MIN_RING_CLEARANCE;

            if (Math.abs(distance - ring.getMiddleRadius()) < halfBand) return true;
        }

        return false;
    }

    /** Asks the terrain's own containsPoint rather than measuring off its centre, since a nebula
     * is a grid of tiles with holes. */
    private boolean isInNebula(Vector2f point) {
        for (CampaignTerrainAPI terrain : system.getTerrainCopy()) {
            if (!(terrain.getPlugin() instanceof NebulaTerrainPlugin)) continue;

            if (terrain.getPlugin().containsPoint(point, PondConstants.MIN_NEBULA_CLEARANCE)) return true;
        }

        return false;
    }

}
