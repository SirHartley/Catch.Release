package catchrelease.campaign.ponds.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import java.util.HashSet;
import java.util.Set;

public class OnJumpPondSpawner implements CoreUITabListener, CurrentLocationChangedListener {

    private final Set<StarSystemAPI> prepared = new HashSet<>();

    public static void register() {
        OnJumpPondSpawner spawner = new OnJumpPondSpawner();
        spawner.prepareChartedSystems();
        spawner.prepareLocation(Global.getSector().getCurrentLocation());
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        listeners.removeListenerOfClass(OnJumpPondSpawner.class);
        listeners.addListener(spawner, true);
    }

    @Override
    public void reportAboutToOpenCoreTab(CoreUITabId tab, Object param) {
        // setEnteredByPlayer has no callback; this runs before Map/Intel is drawn, even while paused.
        if (tab == CoreUITabId.MAP || tab == CoreUITabId.INTEL) prepareChartedSystems();
    }

    private void prepareChartedSystems() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            // Vanilla also sets this for known core systems and acquired system maps.
            if (system.isEnteredByPlayer() && !prepared.contains(system)) prepareLocation(system);
        }
    }

    @Override
    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
        // Also covers non-jump travel, before vanilla necessarily marks the system as entered.
        prepareLocation(curr);
    }

    private void prepareLocation(LocationAPI location) {
        if (!(location instanceof StarSystemAPI system)) return;
        new PondCreator(system).createPonds();
        prepared.add(system);
    }
}
