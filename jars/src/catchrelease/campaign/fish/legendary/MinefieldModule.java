package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.HauntMineEntityPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class MinefieldModule extends BaseHauntModule {

    public static final int MAX_ALIVE = 22;
    public static final float SPAWN_MIN_SECONDS = 6f;
    public static final float SPAWN_MAX_SECONDS = 11f;
    public static final int WAVE_MIN = 3;
    public static final int WAVE_MAX = 6;
    public static final float SPAWN_RANGE_MIN = 400f;
    public static final float SPAWN_RANGE_MAX = 1800f;
    public static final float PASSAGE_WIDTH = 300f;
    public static final int PLACEMENT_ATTEMPTS = 32;

    protected float spawnTimer = 1.5f;

    public MinefieldModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        prune();

        spawnTimer -= amount;
        int active = liveMines();
        if (spawnTimer <= 0f && active < MAX_ALIVE && atFullIntensity()) {
            spawnTimer = MathUtils.getRandomNumberInRange(
                    SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);

            int wave = WAVE_MIN + random.nextInt(WAVE_MAX - WAVE_MIN + 1);
            for (int i = 0; i < Math.min(wave, MAX_ALIVE - active); i++) {
                spawnMine();
            }
        }
    }

    protected int liveMines() {
        int count = 0;
        for (SectorEntityToken mine : spawned) {
            if (!mine.isExpired() && mine.hasTag(HauntMineEntityPlugin.MINE_TAG)) count++;
        }
        return count;
    }

    protected void spawnMine() {
        Vector2f at = pickPosition();
        if (at == null) return;
        HauntMineEntityPlugin.Kind kind = rollKind();

        SectorEntityToken mine = track(system.addCustomEntity(
                Misc.genUID(), null, "catchrelease_HauntMine", null,
                new HauntMineEntityPlugin.Params(kind)));

        mine.setLocation(at.x, at.y);
    }

    protected Vector2f pickPosition() {
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            Vector2f at = nearPlayer(SPAWN_RANGE_MIN, SPAWN_RANGE_MAX);
            if (isClear(at)) return at;
        }
        return null;
    }

    protected boolean isClear(Vector2f at) {
        var player = player();
        if (player == null || Misc.getDistance(player.getLocation(), at)
                < HauntMineEntityPlugin.TRIGGER_RANGE + player.getRadius() + 150f) return false;
        float gap = Math.max(PASSAGE_WIDTH, player.getRadius() * 2f + 150f);
        float separation = HauntMineEntityPlugin.TRIGGER_RANGE * 2f + gap;
        for (SectorEntityToken mine : system.getEntitiesWithTag(HauntMineEntityPlugin.MINE_TAG)) {
            if (!mine.isExpired() && Misc.getDistance(mine.getLocation(), at) < separation) return false;
        }
        for (var planet : system.getPlanets()) {
            if (planet.isStar() && Misc.getDistance(planet.getLocation(), at)
                    < planet.getRadius() + HauntMineEntityPlugin.TRIGGER_RANGE) return false;
        }
        return true;
    }

    protected HauntMineEntityPlugin.Kind rollKind() {
        float roll = random.nextFloat();
        if (roll < 0.4f) return HauntMineEntityPlugin.Kind.BLAST;
        if (roll < 0.7f) return HauntMineEntityPlugin.Kind.SHIELD;

        return HauntMineEntityPlugin.Kind.IMPLOSION;
    }
}
