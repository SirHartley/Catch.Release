package catchrelease.combat.lights;

import catchrelease.campaign.fish.data.FishRarity;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.Iterator;

public class BreachMoteAI implements MissileAIPlugin {

    public static final String WEAPON_ID = "catchrelease_breach_mote";

    public static final float LIFETIME = 6f;
    public static final float FADE_IN = 0.4f;
    public static final float FADE_OUT = 0.6f;
    public static final float DRIFT_SPEED = 25f;
    public static final float WANDER_TURN = 60f;
    public static final float CONTACT_INTERVAL = 0.1f;
    public static final float RADIUS = 14f;
    // how far ship ai keeps clear; the mine flags arm nothing
    public static final float AVOID_RANGE = 75f;

    private static final WeightedRandomPicker<FishRarity> RARITIES = new WeightedRandomPicker<>();

    static {
        RARITIES.add(FishRarity.COMMON, 70f);
        RARITIES.add(FishRarity.UNCOMMON, 20f);
        RARITIES.add(FishRarity.RARE, 8f);
        RARITIES.add(FishRarity.EPIC, 2f);
    }

    private final MissileAPI mote;
    private final Color color;
    private float age;
    private float heading;
    private float turn;
    private float contactTimer;

    private BreachMoteAI(MissileAPI mote, float heading) {
        this.mote = mote;
        this.heading = heading;
        this.color = RARITIES.pick().color;
    }

    public static MissileAPI spawn(CombatEngineAPI engine, ShipAPI ship, Vector2f at) {
        float heading = MathUtils.getRandomNumberInRange(0f, 360f);
        if (!(engine.spawnProjectile(ship, null, WEAPON_ID, at, heading, null) instanceof MissileAPI mote)) return null;

        BreachMoteAI ai = new BreachMoteAI(mote, heading);
        mote.setWeaponSpec(WEAPON_ID);
        mote.setMissileAI(ai);
        mote.getActiveLayers().remove(CombatEngineLayers.FF_INDICATORS_LAYER);
        mote.setEmpResistance(10000);
        mote.setMine(true);
        mote.setNoMineFFConcerns(true);
        mote.setMineExplosionRange(AVOID_RANGE);
        mote.setMinePrimed(true);
        mote.setUntilMineExplosion(0f);

        BreachMoteRenderer.get(engine).add(mote, ai);
        return mote;
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        age += amount;
        if (age >= LIFETIME) {
            engine.removeEntity(mote);
            return;
        }

        turn = MathUtils.clamp(turn + MathUtils.getRandomNumberInRange(-4f, 4f) * WANDER_TURN * amount,
                -WANDER_TURN, WANDER_TURN);
        heading += turn * amount;
        Vector2f drift = Misc.getUnitVectorAtDegreeAngle(heading);
        mote.getVelocity().set(drift.x * DRIFT_SPEED, drift.y * DRIFT_SPEED);
        mote.setFacing(heading);

        contactTimer -= amount;
        if (contactTimer > 0f) return;
        contactTimer = CONTACT_INTERVAL;
        if (!touchPhased(engine)) touchMissiles(engine);
    }

    // phased hulls never collide, so the engine's own hit never reaches them
    private boolean touchPhased(CombatEngineAPI engine) {
        Vector2f at = mote.getLocation();
        for (ShipAPI ship : engine.getShips()) {
            if (!ship.isPhased() || ship.isHulk() || ship.getOwner() == mote.getOwner()) continue;
            if (Misc.getDistance(at, ship.getLocation()) > ship.getCollisionRadius() + RADIUS) continue;
            if (!CollisionUtils.isPointWithinBounds(at, ship)) continue;

            BreachDefence.drop(ship);
            engine.removeEntity(mote);
            return true;
        }
        return false;
    }

    private void touchMissiles(CombatEngineAPI engine) {
        Vector2f at = mote.getLocation();
        float box = (RADIUS + 50f) * 2f;
        Iterator<Object> nearby = engine.getMissileGrid().getCheckIterator(at, box, box);
        while (nearby.hasNext()) {
            if (!(nearby.next() instanceof MissileAPI missile) || missile == mote) continue;
            if (missile.getOwner() == mote.getOwner() || missile.isFading() || !engine.isEntityInPlay(missile)) continue;
            if (Misc.getDistance(at, missile.getLocation()) > RADIUS + missile.getCollisionRadius()) continue;

            BreachMoteRenderer.get(engine).pop(missile.getLocation(), color);
            engine.removeEntity(missile);
            engine.removeEntity(mote);
            return;
        }
    }

    public static float alphaAt(float age) {
        if (age <= 0f || age >= LIFETIME) return 0f;
        return Math.min(1f, Math.min(age / FADE_IN, (LIFETIME - age) / FADE_OUT));
    }

    public float getAlpha() {
        return alphaAt(age);
    }

    public Color getColor() {
        return color;
    }
}
