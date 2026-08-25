package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Salvage that never was: wreck entities seeded around the player's course, unclickable,
 * and fading out as soon as the fleet closes to working distance.
 */
public class FakeWrecksModule extends BaseHauntModule {

    public static final int MAX_ALIVE = 2;
    public static final float SPAWN_MIN_SECONDS = 50f;
    public static final float SPAWN_MAX_SECONDS = 120f;
    public static final float SPAWN_RANGE_MIN = 1800f;
    public static final float SPAWN_RANGE_MAX = 3400f;
    public static final float VANISH_RANGE = 450f;
    public static final float VANISH_SECONDS = 0.75f;
    public static final float LIFE_MIN_SECONDS = 240f;
    public static final float LIFE_MAX_SECONDS = 480f;

    public static final String[] VARIANTS = {
            "buffalo_Standard", "mule_Standard", "hound_Standard"};

    protected float spawnTimer = 10f;
    protected final Map<SectorEntityToken, Float> life = new LinkedHashMap<>();

    public FakeWrecksModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        prune();
        life.keySet().removeIf(e -> e == null || e.isExpired());

        spawnTimer -= amount;
        if (spawnTimer <= 0f && life.size() < MAX_ALIVE) {
            spawnTimer = MathUtils.getRandomNumberInRange(
                    SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);
            spawnWreck();
        }

        for (SectorEntityToken wreck : new ArrayList<>(life.keySet())) {
            float left = life.get(wreck) - amount;
            life.put(wreck, left);

            if (left <= 0f || distanceToPlayer(wreck) < VANISH_RANGE) {
                Misc.fadeAndExpire(wreck, VANISH_SECONDS);
                life.remove(wreck);
            }
        }
    }

    protected void spawnWreck() {
        String variantId = VARIANTS[random.nextInt(VARIANTS.length)];

        DerelictShipEntityPlugin.DerelictShipData params =
                new DerelictShipEntityPlugin.DerelictShipData(
                        new ShipRecoverySpecial.PerShipData(variantId,
                                DerelictShipEntityPlugin.pickBadCondition(null), 0f),
                        false);

        SectorEntityToken wreck = track(system.addCustomEntity(
                Misc.genUID(), null, Entities.WRECK, Factions.NEUTRAL, params));
        wreck.addTag(Tags.NON_CLICKABLE);

        Vector2f at = nearPlayer(SPAWN_RANGE_MIN, SPAWN_RANGE_MAX);
        wreck.setLocation(at.x, at.y);

        life.put(wreck, MathUtils.getRandomNumberInRange(
                LIFE_MIN_SECONDS, LIFE_MAX_SECONDS));
    }
}
