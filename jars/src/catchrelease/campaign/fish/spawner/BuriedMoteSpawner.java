package catchrelease.campaign.fish.spawner;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
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

    protected List<SectorEntityToken> getNearby(LocationAPI location, Vector2f around) {
        List<SectorEntityToken> nearby = new ArrayList<>();

        for (SectorEntityToken buried : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {
            if (buried.isExpired()) continue;

            if (Misc.getDistance(around, buried.getLocation()) <= FishConstants.BURIED_RANGE) {
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
     * Out past the light's reach, in a ring rather than a disc - one appearing inside the searchlight
     * would read as the light making them rather than finding them.
     */
    protected void spawn(LocationAPI location, Vector2f around) {
        String fishId = PondFishSpawner.pickFishId(location);
        if (fishId == null) return;

        float angle = MathUtils.getRandomNumberInRange(0f, 360f);
        float distance = MathUtils.getRandomNumberInRange(
                FishConstants.BURIED_SPAWN_MIN_RANGE, FishConstants.BURIED_RANGE);

        Vector2f loc = MathUtils.getPointOnCircumference(around, distance, angle);

        SectorEntityToken buried = location.addCustomEntity(
                Misc.genUID(), null, FishConstants.BURIED_ENTITY_ID, null,
                new BuriedMoteEntityPlugin.Params(fishId));

        buried.setLocation(loc.x, loc.y);
    }
}
