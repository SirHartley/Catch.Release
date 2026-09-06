package catchrelease.campaign.ponds.listener;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.util.IntervalUtil;

import java.util.HashSet;
import java.util.Set;

public class OnJumpPondSpawner extends BaseCampaignEventListener implements EveryFrameScript {

    private final Set<StarSystemAPI> prepared = new HashSet<>();
    private final IntervalUtil check = new IntervalUtil(1f, 1f);

    public OnJumpPondSpawner() {
        super(false);
    }

    public static void register() {
        OnJumpPondSpawner spawner = new OnJumpPondSpawner();
        spawner.prepareChartedSystems();
        Global.getSector().addTransientListener(spawner);
        Global.getSector().addTransientScript(spawner);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        // Starcharts can unlock a map during a paused dialogue.
        return true;
    }

    @Override
    public void advance(float amount) {
        check.advance(amount);
        if (check.intervalElapsed()) prepareChartedSystems();
    }

    private void prepareChartedSystems() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            // Vanilla also sets this for known core systems and acquired system maps.
            if (system.isEnteredByPlayer() && prepared.add(system)) {
                new PondCreator(system).createPonds();
            }
        }
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        super.reportFleetJumped(fleet, from, to);

        if (!fleet.isPlayerFleet() || to == null || to.getDestination() == null) return;

        LocationAPI loc = to.getDestination().getContainingLocation();
        if (!(loc instanceof StarSystemAPI system)) return;

        new PondCreator(system).createPonds();
        prepared.add(system);
    }
}
