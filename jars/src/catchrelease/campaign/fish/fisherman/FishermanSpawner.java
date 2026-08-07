package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.shop.FishCurrency;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.Map;

/**
 * Puts the Fisherman in the sky: a wandering independent boat that fishes the player's system
 * and serves as an upgrade and trade stop while it does. It stays a fortnight of the player's
 * absence rather than a fortnight outright - see FishermanBehavior.
 * <p>
 * Spawning is a daily roll in whatever system the player is standing in, leaned on by a hold
 * full of fish and by the boat not having come by in a couple of months. It never spawns in
 * hyperspace, in systems cut off from it, in the abyss, in special or hand-made systems - and
 * it would rather work the frontier than the core, though the core still gets the odd visit.
 * One boat at a time, sector-wide.
 */
public class FishermanSpawner implements EveryFrameScript {

    protected final IntervalUtil interval =
            new IntervalUtil(FishermanConstants.SPAWN_CHECK_DAYS * 0.8f,
                    FishermanConstants.SPAWN_CHECK_DAYS * 1.2f);

    /** Registered every load; transient, so a save never carries the watcher. */
    public static void register() {
        Global.getSector().addTransientScript(new FishermanSpawner());
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
        interval.advance(Global.getSector().getClock().convertToDays(amount));
        if (!interval.intervalElapsed()) return;

        if (getLiveFleet() != null) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;

        if (!(player.getContainingLocation() instanceof StarSystemAPI)) return;
        StarSystemAPI system = (StarSystemAPI) player.getContainingLocation();

        if (!isEligible(system)) return;

        if (MathUtils.getRandomNumberInRange(0f, 1f) > getChance(system)) return;

        spawn(system, player.getLocation());
    }

    /** The live boat if there is one anywhere, cleaned out of memory the moment it is not. */
    protected CampaignFleetAPI getLiveFleet() {
        Object stored = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.ACTIVE_KEY);

        if (stored instanceof CampaignFleetAPI) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) stored;
            if (!fleet.isExpired() && fleet.isAlive()) return fleet;
        }

        Global.getSector().getMemoryWithoutUpdate().unset(FishermanConstants.ACTIVE_KEY);

        return null;
    }

    /** Where the boat will and will not work. */
    protected boolean isEligible(StarSystemAPI system) {
        if (!system.isProcgen()) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
        if (system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) return false;

        return true;
    }

    /** The daily roll: small, until the hold is heavy or the absence long. */
    protected float getChance(StarSystemAPI system) {
        float chance = FishermanConstants.SPAWN_BASE_CHANCE;

        int aboard = 0;
        for (Map.Entry<catchrelease.campaign.fish.data.FishRarity, Integer> entry
                : FishCurrency.count().entrySet()) {
            aboard += entry.getValue();
        }
        if (aboard >= FishermanConstants.CARGO_FISH_THRESHOLD) {
            chance *= FishermanConstants.CARGO_FULL_MULT;
        }

        Object last = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.LAST_SEEN_KEY);
        boolean overdue = !(last instanceof Long)
                || Global.getSector().getClock().getElapsedDaysSince((Long) last)
                >= FishermanConstants.OVERDUE_DAYS;
        if (overdue) chance *= FishermanConstants.OVERDUE_MULT;

        SectorRegion at = SectorRegion.of(system);
        if (at != null && at.isCore()) chance *= FishermanConstants.CORE_SPAWN_MULT;

        return Math.min(1f, chance);
    }

    /** The boat itself: one cruiser, a few logistics hulls, lamps and manners fitted by script. */
    protected void spawn(StarSystemAPI system, Vector2f near) {
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(
                FishermanConstants.FACTION, FishermanConstants.FLEET_NAME, true);

        for (String variant : FishermanConstants.SHIPS) {
            fleet.getFleetData().addFleetMember(
                    Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant));
        }

        fleet.getFleetData().sort();
        fleet.forceSync();
        fleet.setTransponderOn(true);
        fleet.getMemoryWithoutUpdate().set(FishermanConstants.FLEET_FLAG, true);

        //the same man at the wheel every time - see FishermanIdentity
        FishermanIdentity.crew(fleet);

        //beyond anything's sensors, same as the quest fleets: it arrives, it does not appear
        float distance = Math.max(FishermanConstants.SPAWN_DISTANCE_MIN,
                Global.getSettings().getSensorRangeMax() * 1.3f)
                + MathUtils.getRandomNumberInRange(0f, FishermanConstants.SPAWN_DISTANCE_SPREAD);

        Vector2f at = MathUtils.getPointOnCircumference(near, distance,
                MathUtils.getRandomNumberInRange(0f, 360f));

        system.addEntity(fleet);
        fleet.setLocation(at.x, at.y);

        //the wander is vanilla's patrol; the two weeks and the leaving belong to the behaviour
        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, system.getCenter(),
                FishermanConstants.STAY_DAYS + 2f, "fishing the deep");

        fleet.addScript(new FishermanBehavior(fleet));

        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.ACTIVE_KEY, fleet);
    }

    /** Whether a fleet is the Fisherman, for anything that routes on it. */
    public static boolean isFisherman(CampaignFleetAPI fleet) {
        return fleet != null
                && fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.FLEET_FLAG);
    }
}
