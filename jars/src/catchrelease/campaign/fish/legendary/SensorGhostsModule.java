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
 * Contacts with nothing behind them, and plenty of them: a steady stream of vanilla
 * sensor-ghost entities crossing the player's sensor bubble, unclickable, fading before
 * anything can be made of them. A share of them stalk - the contact turns with the
 * fleet and follows it for as long as it lives, which is what makes them a problem
 * rather than scenery.
 */
public class SensorGhostsModule extends BaseHauntModule {

    public static final int MAX_ALIVE = 8;
    public static final float SPAWN_MIN_SECONDS = 3.5f;
    public static final float SPAWN_MAX_SECONDS = 8f;
    public static final float SPAWN_RANGE_MIN = 700f;
    public static final float SPAWN_RANGE_MAX = 1900f;
    public static final float DRIFT_MIN_SPEED = 60f;
    public static final float DRIFT_MAX_SPEED = 140f;
    public static final float STALK_CHANCE = 0.4f;
    public static final float STALK_MIN_SPEED = 45f;
    public static final float STALK_MAX_SPEED = 95f;
    public static final float STALK_TURN_DEG_PER_SECOND = 40f;
    public static final float LIFE_MIN_SECONDS = 25f;
    public static final float LIFE_MAX_SECONDS = 55f;

    protected static class Drift {

        Vector2f velocity;
        float life;
        boolean stalks;
    }

    protected float spawnTimer = 1.5f;
    protected final Map<SectorEntityToken, Drift> drifts = new LinkedHashMap<>();

    public SensorGhostsModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        prune();
        drifts.keySet().removeIf(e -> e == null || e.isExpired());

        spawnTimer -= amount;
        if (spawnTimer <= 0f && drifts.size() < MAX_ALIVE && atFullIntensity()) {
            spawnTimer = MathUtils.getRandomNumberInRange(
                    SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);
            spawnGhost();
        }

        for (SectorEntityToken ghost : new ArrayList<>(drifts.keySet())) {
            Drift drift = drifts.get(ghost);
            drift.life -= amount;

            if (drift.stalks && player() != null) steer(ghost, drift, amount);

            Vector2f at = ghost.getLocation();
            ghost.setLocation(at.x + drift.velocity.x * amount,
                    at.y + drift.velocity.y * amount);

            if (drift.life <= 0f) {
                Misc.fadeAndExpire(ghost, 1f);
                drifts.remove(ghost);
            }
        }
    }

    // a stalker keeps its speed but bends its course toward the fleet, rate-limited so
    // it reads as something holding contact rather than a homing missile
    protected void steer(SectorEntityToken ghost, Drift drift, float amount) {
        float speed = drift.velocity.length();
        if (speed <= 0f) return;

        float current = Misc.getAngleInDegrees(new Vector2f(), drift.velocity);
        float wanted = Misc.getAngleInDegrees(ghost.getLocation(),
                player().getLocation());
        float turn = MathUtils.getShortestRotation(current, wanted);
        float step = Math.min(Math.abs(turn), STALK_TURN_DEG_PER_SECOND * amount)
                * Math.signum(turn);

        drift.velocity = MathUtils.getPointOnCircumference(null, speed, current + step);
    }

    protected void spawnGhost() {
        SectorEntityToken ghost = track(system.addCustomEntity(
                null, null, Entities.SENSOR_GHOST, Factions.NEUTRAL));
        ghost.setDiscoverable(true);
        ghost.addTag(Tags.NON_CLICKABLE);

        Vector2f at = nearPlayer(SPAWN_RANGE_MIN, SPAWN_RANGE_MAX);
        ghost.setLocation(at.x, at.y);

        Drift drift = new Drift();
        drift.stalks = random.nextFloat() < STALK_CHANCE;

        Vector2f aim = player() == null ? at : player().getLocation();
        if (drift.stalks) {
            float bearing = Misc.getAngleInDegrees(at, aim)
                    + MathUtils.getRandomNumberInRange(-10f, 10f);
            float speed = MathUtils.getRandomNumberInRange(STALK_MIN_SPEED, STALK_MAX_SPEED);
            drift.velocity = MathUtils.getPointOnCircumference(null, speed, bearing);
        } else {
            // aimed loosely past the player, so contacts cross the bubble rather than orbit it
            float bearing = Misc.getAngleInDegrees(at, aim)
                    + MathUtils.getRandomNumberInRange(-30f, 30f);
            float speed = MathUtils.getRandomNumberInRange(DRIFT_MIN_SPEED, DRIFT_MAX_SPEED);
            drift.velocity = MathUtils.getPointOnCircumference(null, speed, bearing);
        }

        drift.life = MathUtils.getRandomNumberInRange(LIFE_MIN_SECONDS, LIFE_MAX_SECONDS);
        drifts.put(ghost, drift);
    }
}
