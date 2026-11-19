package catchrelease.campaign.fish.spawner;

import catchrelease.config.Setting;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.fisherman.chain.WoundedEpic;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.legendary.EpicHunt;
import catchrelease.campaign.fish.legendary.LegendaryHaunt;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class BuriedMoteSpawner implements EveryFrameScript {

    protected IntervalUtil interval = new IntervalUtil(
            FishConstants.BURIED_CHECK_INTERVAL * 0.75f, FishConstants.BURIED_CHECK_INTERVAL * 1.25f);

    // the spawner only culls around the player, so wild motes in a system left behind would stay in the save
    public static class DeparturePurge implements CurrentLocationChangedListener {

        @Override
        public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
            if (prev == null || prev == curr) return;

            purge(prev, BuriedMoteEntityPlugin.BURIED_TAG);
            purge(prev, FishEntityPlugin.MOTE_TAG);
        }

        protected void purge(LocationAPI location, String tag) {
            for (SectorEntityToken mote : new ArrayList<>(location.getEntitiesWithTag(tag))) {
                if (mote.isExpired() || !isWild(mote)) continue;

                remove(mote);
            }
        }
    }

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        listeners.removeListenerOfClass(DeparturePurge.class);
        listeners.addListener(new DeparturePurge(), true);

        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof BuriedMoteSpawner) return;
        }

        Global.getSector().addScript(new BuriedMoteSpawner());
    }

    // legendaries, their haunt pieces and quest motes have owners that respawn or clean them up
    public static boolean isWild(SectorEntityToken mote) {
        if (QuestPond.isQuestMote(mote) || mote.hasTag(LegendaryHaunt.HAUNT_TAG)) return false;

        if (mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin buried) {
            return buried.getRarity() != FishRarity.LEGENDARY;
        }

        if (mote.getCustomPlugin() instanceof FishEntityPlugin fish) {
            FishSpec spec = fish.getFishSpec();
            // awake epics are left to EpicHunt.DepartureCleanup, which reports the reset
            return spec != null && spec.rarity != FishRarity.LEGENDARY && !fish.isHeld() && !fish.isPhantom()
                    && !fish.isDecoy() && fish.getOrbitAnchor() == null && !EpicHunt.isActive(fish);
        }

        return false;
    }

    protected static void remove(SectorEntityToken mote) {
        // removeEntity does not expire it, and an epic's trail renderer retires on expiry
        mote.setExpired(true);
        mote.getContainingLocation().removeEntity(mote);
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
        interval.advance(amount * Setting.FISH_REPLENISH.mult());
        if (!interval.intervalElapsed()) return;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        if (fleet.isInHyperspace()) return;

        LocationAPI location = fleet.getContainingLocation();
        if (location == null) return;

        List<SectorEntityToken> nearby = getNearby(location, fleet.getLocation());

        cullDistant(location, fleet.getLocation());

        for (int i = nearby.size(); i < Math.round(FishConstants.BURIED_POPULATION * Setting.FISH_POPULATION.mult()); i++) {
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
            if (buried.isExpired() || isLegendary(buried) || QuestPond.isQuestMote(buried)) continue;

            if (Misc.getDistance(around, buried.getLocation()) <= getPopulationRange()) {
                nearby.add(buried);
            }
        }

        // a fish the lamps surfaced still belongs to this population until it fades, or every sweep would add more
        for (SectorEntityToken mote : location.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (mote.isExpired() || !(mote.getCustomPlugin() instanceof FishEntityPlugin fish)
                    || !fish.isLampSurfaced() || QuestPond.isQuestMote(mote)) continue;

            if (Misc.getDistance(around, mote.getLocation()) <= getPopulationRange()) nearby.add(mote);
        }

        return nearby;
    }

    protected void cullDistant(LocationAPI location, Vector2f around) {
        for (SectorEntityToken buried : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {
            if (buried.isExpired() || isLegendary(buried) || QuestPond.isQuestMote(buried)) continue;

            if (Misc.getDistance(around, buried.getLocation()) > FishConstants.BURIED_CULL_RANGE) remove(buried);
        }
    }

    protected static float getRareChance() {
        return UpgradeManager.getValue(StatIds.SEARCHLIGHT_RARE_CHANCE, 0f);
    }

    private static boolean isLegendary(SectorEntityToken mote) {
        return mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin fish
                && fish.getRarity() == FishRarity.LEGENDARY;
    }

    protected void spawn(LocationAPI location, Vector2f around) {
        // a buried mote is what the lights find, so only what the lights can find is offered
        String fishId = PondFishSpawner.pickFishId(location, CatchImplement.BREACH_LAMP,
                getRareChance());
        if (fishId == null || WoundedEpic.isReserved(location, fishId)) return;

        float angle = MathUtils.getRandomNumberInRange(0f, 360f);
        float distance = MathUtils.getRandomNumberInRange(getSpawnMinRange(), getSpawnMaxRange());

        Vector2f loc = MathUtils.getPointOnCircumference(around, distance, angle);

        SectorEntityToken buried = location.addCustomEntity(
                Misc.genUID(), null, FishConstants.BURIED_ENTITY_ID, null,
                new BuriedMoteEntityPlugin.Params(fishId));

        buried.setLocation(loc.x, loc.y);
    }
}
