package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Sightings that are not the fish: phantom motes in the legendary's own colours,
 * clustered around the fish itself while it is in the water (around the player only
 * when it is not). Nothing can hook, slow or hold one, and closing in dissolves it.
 */
public class DistractionMotesModule extends BaseHauntModule {

    public static final int MAX_ALIVE = 4;
    public static final float SPAWN_MIN_SECONDS = 8f;
    public static final float SPAWN_MAX_SECONDS = 16f;
    public static final float SPAWN_RANGE_MIN = 120f;
    public static final float SPAWN_RANGE_MAX = 450f;
    public static final float FALLBACK_RANGE_MIN = 900f;
    public static final float FALLBACK_RANGE_MAX = 2200f;
    public static final float DISSOLVE_RANGE = 220f;
    public static final float DISSOLVE_SECONDS = 0.6f;
    public static final float LIFE_MIN_SECONDS = 50f;
    public static final float LIFE_MAX_SECONDS = 110f;

    protected float spawnTimer = 2f;
    protected final java.util.Map<SectorEntityToken, Float> life
            = new java.util.LinkedHashMap<>();

    public DistractionMotesModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        prune();
        life.keySet().removeIf(e -> e == null || e.isExpired());

        spawnTimer -= amount;
        if (spawnTimer <= 0f && spawned.size() < MAX_ALIVE && atFullIntensity()) {
            spawnTimer = MathUtils.getRandomNumberInRange(
                    SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);
            spawnPhantom();
        }

        for (SectorEntityToken phantom : new java.util.ArrayList<>(life.keySet())) {
            float left = life.get(phantom) - amount;
            life.put(phantom, left);

            if (left <= 0f || distanceToPlayer(phantom) < DISSOLVE_RANGE) {
                Misc.fadeAndExpire(phantom, DISSOLVE_SECONDS);
                life.remove(phantom);
            }
        }
    }

    protected void spawnPhantom() {
        Vector2f at;
        FishEntityPlugin own = findOwnMote();
        if (own != null && own.getMote() != null) {
            at = MathUtils.getPointOnCircumference(own.getMote().getLocation(),
                    MathUtils.getRandomNumberInRange(SPAWN_RANGE_MIN, SPAWN_RANGE_MAX),
                    random.nextFloat() * 360f);
        } else {
            at = nearPlayer(FALLBACK_RANGE_MIN, FALLBACK_RANGE_MAX);
        }
        Vector2f drift = MathUtils.getPointOnCircumference(at,
                MathUtils.getRandomNumberInRange(400f, 900f), random.nextFloat() * 360f);

        FishEntityPlugin.Params params = new FishEntityPlugin.Params(drift, spec.id);
        params.phantom = true;

        SectorEntityToken phantom = track(system.addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null, params));
        phantom.setLocation(at.x, at.y);

        life.put(phantom, MathUtils.getRandomNumberInRange(
                LIFE_MIN_SECONDS, LIFE_MAX_SECONDS));
    }
}
