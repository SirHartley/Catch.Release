package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contacts with nothing behind them: vanilla sensor-ghost entities drifting through the
 * player's sensor bubble, unclickable, fading before anything can be made of them.
 */
public class SensorGhostsModule extends BaseHauntModule {

    public static final int MAX_ALIVE = 3;
    public static final float SPAWN_MIN_SECONDS = 12f;
    public static final float SPAWN_MAX_SECONDS = 25f;
    public static final float SPAWN_RANGE_MIN = 1200f;
    public static final float SPAWN_RANGE_MAX = 2400f;
    public static final float DRIFT_MIN_SPEED = 60f;
    public static final float DRIFT_MAX_SPEED = 140f;
    public static final float LIFE_MIN_SECONDS = 18f;
    public static final float LIFE_MAX_SECONDS = 40f;

    protected static class Drift {

        Vector2f velocity;
        float life;
    }

    protected float spawnTimer = 4f;
    protected final Map<SectorEntityToken, Drift> drifts = new LinkedHashMap<>();

    public SensorGhostsModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        prune();
        drifts.keySet().removeIf(e -> e == null || e.isExpired());

        spawnTimer -= amount;
        if (spawnTimer <= 0f && drifts.size() < MAX_ALIVE) {
            spawnTimer = MathUtils.getRandomNumberInRange(
                    SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);
            spawnGhost();
        }

        for (SectorEntityToken ghost : new ArrayList<>(drifts.keySet())) {
            Drift drift = drifts.get(ghost);
            drift.life -= amount;

            Vector2f at = ghost.getLocation();
            ghost.setLocation(at.x + drift.velocity.x * amount,
                    at.y + drift.velocity.y * amount);

            if (drift.life <= 0f) {
                Misc.fadeAndExpire(ghost, 1f);
                drifts.remove(ghost);
            }
        }
    }

    protected void spawnGhost() {
        SectorEntityToken ghost = track(system.addCustomEntity(
                null, null, Entities.SENSOR_GHOST, Factions.NEUTRAL));
        ghost.setDiscoverable(true);
        ghost.addTag(Tags.NON_CLICKABLE);

        Vector2f at = nearPlayer(SPAWN_RANGE_MIN, SPAWN_RANGE_MAX);
        ghost.setLocation(at.x, at.y);

        // aimed loosely past the player, so contacts cross the bubble rather than orbit it
        Vector2f aim = player() == null ? at : player().getLocation();
        float bearing = MathUtils.getRandomNumberInRange(-35f, 35f)
                + Misc.getAngleInDegrees(at, aim);
        float speed = MathUtils.getRandomNumberInRange(DRIFT_MIN_SPEED, DRIFT_MAX_SPEED);

        Drift drift = new Drift();
        drift.velocity = MathUtils.getPointOnCircumference(null, speed, bearing);
        drift.life = MathUtils.getRandomNumberInRange(LIFE_MIN_SECONDS, LIFE_MAX_SECONDS);
        drifts.put(ghost, drift);
    }
}
