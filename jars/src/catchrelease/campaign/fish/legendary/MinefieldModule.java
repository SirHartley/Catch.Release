package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.HauntMineEntityPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * The minelayer's work: waves of blinking mines seeded across the player's course -
 * red shoves, blue interdicts, yellow implodes and pulls. All of it non-lethal, all of
 * it gone without a trace when the chase ends.
 */
public class MinefieldModule extends BaseHauntModule {

    public static final int MAX_ALIVE = 12;
    public static final float SPAWN_MIN_SECONDS = 9f;
    public static final float SPAWN_MAX_SECONDS = 16f;
    public static final int WAVE_MIN = 2;
    public static final int WAVE_MAX = 4;
    public static final float SPAWN_RANGE_MIN = 600f;
    public static final float SPAWN_RANGE_MAX = 1700f;

    protected float spawnTimer = 3f;

    public MinefieldModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        prune();

        spawnTimer -= amount;
        if (spawnTimer <= 0f && spawned.size() < MAX_ALIVE && atFullIntensity()) {
            spawnTimer = MathUtils.getRandomNumberInRange(
                    SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);

            int wave = WAVE_MIN + random.nextInt(WAVE_MAX - WAVE_MIN + 1);
            for (int i = 0; i < wave && spawned.size() < MAX_ALIVE; i++) {
                spawnMine();
            }
        }
    }

    protected void spawnMine() {
        HauntMineEntityPlugin.Kind kind = rollKind();

        SectorEntityToken mine = track(system.addCustomEntity(
                Misc.genUID(), null, "catchrelease_HauntMine", null,
                new HauntMineEntityPlugin.Params(kind)));

        Vector2f at = nearPlayer(SPAWN_RANGE_MIN, SPAWN_RANGE_MAX);
        mine.setLocation(at.x, at.y);
    }

    protected HauntMineEntityPlugin.Kind rollKind() {
        float roll = random.nextFloat();
        if (roll < 0.4f) return HauntMineEntityPlugin.Kind.BLAST;
        if (roll < 0.7f) return HauntMineEntityPlugin.Kind.INTERCEPT;

        return HauntMineEntityPlugin.Kind.IMPLOSION;
    }

    @Override
    public void cleanup() {
        InterdictionPulse.release(player());

        super.cleanup();
    }
}
