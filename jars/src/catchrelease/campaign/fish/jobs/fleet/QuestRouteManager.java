package catchrelease.campaign.fish.jobs.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import java.util.Random;

// Adds routes for fleets that carry fleet-quest offers no vanilla fleet fits. RouteManager spawns and
// despawns the fleets near the player; the economy tick (about every three days) replaces the
// per-frame interval of BaseRouteFleetManager. Saved: every route keeps a reference to its spawner.
public abstract class QuestRouteManager implements RouteFleetSpawner, EconomyTickListener {

    protected static void register(Class<? extends QuestRouteManager> type, QuestRouteManager manager) {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(type)) listeners.addListener(manager);
    }

    protected abstract String getRouteSourceId();

    protected abstract int getMaxFleets();

    protected abstract float getChancePerTick();

    protected abstract void addRoute(Random random);

    protected boolean isActive() {
        return true;
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (!isActive()) return;
        if (RouteManager.getInstance().getNumRoutesFor(getRouteSourceId()) >= getMaxFleets()) return;

        Random random = new Random();
        if (random.nextFloat() >= getChancePerTick()) return;

        addRoute(random);
    }

    @Override
    public void reportEconomyMonthEnd() {
    }

    @Override
    public boolean shouldCancelRouteAfterDelayCheck(RouteData route) {
        return false;
    }

    @Override
    public boolean shouldRepeat(RouteData route) {
        return false;
    }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteData route) {
    }

    protected static boolean isOrdinaryDestination(StarSystemAPI system) {
        return system != null
                && !system.hasTag(Tags.SYSTEM_ABYSSAL)
                && !system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
                && !system.hasTag(Tags.THEME_HIDDEN)
                && !system.hasTag(Tags.THEME_SPECIAL)
                && !system.hasTag(Tags.THEME_UNSAFE)
                && !system.hasTag(Tags.TEMPORARY_LOCATION);
    }
}
