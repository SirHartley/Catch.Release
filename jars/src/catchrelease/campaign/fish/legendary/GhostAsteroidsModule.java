package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.GhostAsteroidEntityPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * A temporary asteroid field with nothing solid in it: a slow-drifting cluster of ghost
 * rocks seeded off the player's position, topped up while the chase runs.
 */
public class GhostAsteroidsModule extends BaseHauntModule {

    public static final int FIELD_SIZE = 40;
    public static final float TOP_UP_SECONDS = 15f;
    public static final float FIELD_RANGE_MIN = 400f;
    public static final float FIELD_RANGE_MAX = 900f;
    public static final float FIELD_RADIUS = 900f;
    public static final float RESEED_RANGE = 2200f;
    public static final float FAR_FADE_RANGE = 3000f;
    public static final float FAR_FADE_SECONDS = 2.5f;
    public static final float ROCK_SIZE_MIN = 26f;
    public static final float ROCK_SIZE_MAX = 90f;
    public static final float SPIN_MAX_DEG = 24f;
    public static final float DRIFT_SPEED_MIN = 8f;
    public static final float DRIFT_SPEED_MAX = 26f;

    protected float topUpTimer = 0f;
    protected Vector2f fieldCenter;
    protected float fieldDriftAngle;

    public GhostAsteroidsModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        prune();

        topUpTimer -= amount;
        if (topUpTimer <= 0f && atFullIntensity()) {
            topUpTimer = TOP_UP_SECONDS;

            // the chase outruns a static field: rocks left far behind fade out to
            // free the cap, and the field reseeds itself around the fleet - pop-in
            // is fine, it is a haunt. Checked on the tick so a fading rock is not
            // handed a new fade script every frame
            for (SectorEntityToken rock : spawned) {
                if (!rock.isExpired() && distanceToPlayer(rock) > FAR_FADE_RANGE) {
                    Misc.fadeAndExpire(rock, FAR_FADE_SECONDS);
                }
            }

            CampaignFleetAPI player = player();
            if (fieldCenter == null || (player != null && MathUtils.getDistance(
                    player.getLocation(), fieldCenter) > RESEED_RANGE)) {
                fieldCenter = nearPlayer(FIELD_RANGE_MIN, FIELD_RANGE_MAX);
                fieldDriftAngle = random.nextFloat() * 360f;
            }

            while (spawned.size() < FIELD_SIZE) {
                spawnRock();
            }
        }
    }

    protected void spawnRock() {
        Vector2f at = MathUtils.getRandomPointInCircle(fieldCenter, FIELD_RADIUS);

        // the field drifts as one body, each rock a little off the shared heading
        float bearing = fieldDriftAngle + MathUtils.getRandomNumberInRange(-25f, 25f);
        Vector2f drift = MathUtils.getPointOnCircumference(null,
                MathUtils.getRandomNumberInRange(DRIFT_SPEED_MIN, DRIFT_SPEED_MAX), bearing);

        GhostAsteroidEntityPlugin.Params params = new GhostAsteroidEntityPlugin.Params(
                1 + random.nextInt(3),
                MathUtils.getRandomNumberInRange(ROCK_SIZE_MIN, ROCK_SIZE_MAX),
                MathUtils.getRandomNumberInRange(-SPIN_MAX_DEG, SPIN_MAX_DEG),
                drift);

        SectorEntityToken rock = track(system.addCustomEntity(
                Misc.genUID(), null, "catchrelease_GhostAsteroid", null, params));
        rock.setLocation(at.x, at.y);
    }
}
