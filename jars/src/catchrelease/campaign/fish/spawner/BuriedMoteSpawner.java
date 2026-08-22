package catchrelease.campaign.fish.spawner;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
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

public class BuriedMoteSpawner implements EveryFrameScript {
    protected IntervalUtil interval = new IntervalUtil(
            FishConstants.BURIED_CHECK_INTERVAL * 0.75f, FishConstants.BURIED_CHECK_INTERVAL * 1.25f);

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
        if (fleet == null) return;

        if (fleet.isInHyperspace()) return;

        LocationAPI location = fleet.getContainingLocation();
        if (location == null) return;

        List<SectorEntityToken> nearby = getNearby(location, fleet.getLocation());

        cullDistant(location, fleet.getLocation());

        for (int i = nearby.size(); i < FishConstants.BURIED_POPULATION; i++) {
            spawn(location, fleet.getLocation());
        }
    }

    protected static float getSpawnMinRange() {
        return Searchlight.getMaxReach() + FishConstants.BURIED_SPAWN_CLEARANCE;
    }

    protected static float getSpawnMaxRange() {
        return getSpawnMinRange() + FishConstants.BURIED_SPAWN_BAND;
    }

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

    protected void cullDistant(LocationAPI location, Vector2f around) {
        for (SectorEntityToken buried : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {
            if (buried.isExpired()) continue;

            if (Misc.getDistance(around, buried.getLocation()) > FishConstants.BURIED_CULL_RANGE) {
                buried.getContainingLocation().removeEntity(buried);
            }
        }
    }

    protected static float getRareChance() {
        return UpgradeManager.getValue(StatIds.SEARCHLIGHT_RARE_CHANCE, 0f);
    }

    protected void spawn(LocationAPI location, Vector2f around) {
        // a buried mote is what the lights find, so only what the lights can find is offered
        String fishId = PondFishSpawner.pickFishId(location, CatchImplement.BREACH_LAMP,
                getRareChance());
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
