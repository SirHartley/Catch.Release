package catchrelease.campaign.fish.spawner;

import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a population of buried motes around the player, so a searchlight always has the chance of
 * finding something.
 * <p>
 * They are seeded out of sight rather than in view - a mote that pops into existence inside the
 * light was not found, it was handed over - and culled once the player has left them well behind, so
 * a long game does not end up with a system full of things nobody is ever going to look for.
 * <p>
 * Deliberately a population rather than a spawn rate: the number in range is what is aimed at, so
 * sitting still does not accumulate them and travelling does not outrun them.
 */
public class BuriedMoteSpawner implements EveryFrameScript {

    protected IntervalUtil interval = new IntervalUtil(
            FishConstants.BURIED_CHECK_INTERVAL * 0.75f, FishConstants.BURIED_CHECK_INTERVAL * 1.25f);

    /** Installed once. Idempotent, so calling it on every load is safe. */
    public static void register() {
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof BuriedMoteSpawner) return;
        }

        Global.getSector().addScript(new BuriedMoteSpawner());
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) return;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.isInHyperspace()) return;

        LocationAPI location = fleet.getContainingLocation();
        if (location == null) return;

        List<SectorEntityToken> nearby = getNearby(location, fleet.getLocation());

        cullDistant(location, fleet.getLocation());

        for (int i = nearby.size(); i < FishConstants.BURIED_POPULATION; i++) {
            spawn(location, fleet.getLocation());
        }
    }

    /**
     * Just past what the lights can see, rather than a number picked to sound far away.
     * <p>
     * This was fixed at a distance chosen before the lights were, and the two never met: the beams
     * reach a little over a thousand units and nothing was ever seeded inside sixteen hundred, so
     * every mote the player ever found had wandered several hundred units inward on its own. That is
     * why so few turned up. Measured off the lights now, so it stays just out of sight when the
     * area upgrade widens them instead of falling inside the beam.
     */
    protected static float getSpawnMinRange() {
        return Searchlight.getMaxReach() + FishConstants.BURIED_SPAWN_CLEARANCE;
    }

    /**
     * And a band rather than the whole surrounding disc. Seeded out to the old range most of them
     * sat too far out to ever drift in, so the population was real and unreachable at the same time.
     */
    protected static float getSpawnMaxRange() {
        return getSpawnMinRange() + FishConstants.BURIED_SPAWN_BAND;
    }

    /** Everything inside the band counts towards the population, so the target is what is reachable. */
    protected static float getPopulationRange() {
        return getSpawnMaxRange();
    }

    protected List<SectorEntityToken> getNearby(LocationAPI location, Vector2f around) {
        List<SectorEntityToken> nearby = new ArrayList<>();

        for (SectorEntityToken buried : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {
            if (buried.isExpired()) continue;

            if (Misc.getDistance(around, buried.getLocation()) <= getPopulationRange()) {
                nearby.add(buried);
            }
        }

        return nearby;
    }

    /** Anything the player has left far enough behind stops being worth keeping in the world. */
    protected void cullDistant(LocationAPI location, Vector2f around) {
        for (SectorEntityToken buried : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {
            if (buried.isExpired()) continue;

            if (Misc.getDistance(around, buried.getLocation()) > FishConstants.BURIED_CULL_RANGE) {
                buried.getContainingLocation().removeEntity(buried);
            }
        }
    }

    /**
     * How much better the lights are at turning up something worth having.
     * <p>
     * Applied where the population is seeded rather than where a light passes over one, which is the
     * only place it can honestly go: a buried mote is a specific species from the moment it exists,
     * and a beam that changed what was already down there would be deciding rather than finding.
     * What the upgrade actually buys is a better class of thing put out there to be found.
     * <p>
     * Added to the drones' own bias rather than replacing it, so a fleet that has invested in both
     * gets both, and a fleet that has bought neither sees exactly what it saw before.
     */
    protected static float getRareChance() {
        return UpgradeManager.getValue(StatIds.SEARCHLIGHT_RARE_CHANCE, 0f);
    }

    /**
     * Out past the light's reach, in a ring rather than a disc - one appearing inside the searchlight
     * would read as the light making them rather than finding them.
     */
    protected void spawn(LocationAPI location, Vector2f around) {
        String fishId = PondFishSpawner.pickFishId(location, getRareChance());
        if (fishId == null) return;

        float angle = MathUtils.getRandomNumberInRange(0f, 360f);
        float distance = MathUtils.getRandomNumberInRange(getSpawnMinRange(), getSpawnMaxRange());

        Vector2f loc = MathUtils.getPointOnCircumference(around, distance, angle);

        SectorEntityToken buried = location.addCustomEntity(
                Misc.genUID(), null, FishConstants.BURIED_ENTITY_ID, null,
                new BuriedMoteEntityPlugin.Params(fishId));

        buried.setLocation(loc.x, loc.y);
    }
}
