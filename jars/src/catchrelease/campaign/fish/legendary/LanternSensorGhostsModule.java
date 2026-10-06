package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ghosts.BaseSensorGhost;
import com.fs.starfarer.api.impl.campaign.ghosts.GBEchoMovement;
import com.fs.starfarer.api.impl.campaign.ghosts.GBGoAwayFrom;
import com.fs.starfarer.api.impl.campaign.ghosts.GBGoInDirection;
import com.fs.starfarer.api.impl.campaign.ghosts.GBIntercept;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LanternSensorGhostsModule extends BaseHauntModule {

    public static final int MAX_ALIVE = 4;
    public static final float SPAWN_MIN_SECONDS = 5f;
    public static final float SPAWN_MAX_SECONDS = 10f;

    protected float spawnTimer;
    protected final List<BaseSensorGhost> ghosts = new ArrayList<>();

    public LanternSensorGhostsModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
        spawnTimer = MathUtils.getRandomNumberInRange(SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);
    }

    @Override
    public void advance(float amount) {
        prune();
        for (Iterator<BaseSensorGhost> it = ghosts.iterator(); it.hasNext();) {
            BaseSensorGhost ghost = it.next();
            SectorEntityToken entity = ghost.getEntity();
            if (entity == null || entity.isExpired() || entity.getContainingLocation() != system) {
                it.remove();
                continue;
            }
            ghost.advance(amount);
            if (ghost.isDone()) it.remove();
        }

        spawnTimer -= amount;
        if (spawnTimer <= 0f && spawned.size() < MAX_ALIVE && atFullIntensity()
                && player() != null && player().getContainingLocation() == system) {
            spawnTimer = MathUtils.getRandomNumberInRange(SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);
            spawnGhost();
        }
    }

    protected void spawnGhost() {
        BaseSensorGhost ghost = new BaseSensorGhost(null, 0);
        ghost.initEntity(ghost.genMediumSensorProfile(), ghost.genSmallRadius(), 0, system);
        ghost.setDespawnOutsideSector(false);
        ghost.setDespawnInAbyss(false);
        ghost.setLoc(nearPlayer(700f, 1400f));
        ghost.getEntity().addTag(Tags.NON_CLICKABLE);

        switch (random.nextInt(3)) {
            case 0 -> ghost.addBehavior(new GBEchoMovement(player(), days(2f), days(25f)));
            case 1 -> {
                ghost.addBehavior(new GBIntercept(player(), days(12f), 15, 350f, true));
                ghost.addBehavior(new GBGoAwayFrom(days(12f), player(), 20));
            }
            default -> ghost.addBehavior(new GBGoInDirection(days(20f), random.nextFloat() * 360f, 12));
        }

        // Vanilla clears getEntity() when fading starts; retain the token for teardown.
        track(ghost.getEntity());
        ghosts.add(ghost);
    }

    protected float days(float seconds) {
        return Global.getSector().getClock().convertToDays(seconds);
    }

    @Override
    public void cleanup() {
        ghosts.clear();
        super.cleanup();
    }
}
