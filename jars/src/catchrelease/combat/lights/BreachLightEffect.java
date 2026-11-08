package catchrelease.combat.lights;

import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.config.FishingSounds;
import catchrelease.helper.math.TrigHelper;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class BreachLightEffect implements EveryFrameWeaponEffectPlugin {

    public static final String WEAPON_ID = "catchrelease_breach_light";

    public static final float REACH = 900f;
    public static final float HALF_ANGLE = Searchlight.FAN_HALF_ANGLE;
    // the engine pivots a hardpoint sprite a quarter up its height; the lens is the top of the body
    public static final float LENS_OFFSET = 14f;

    public static final float SWEEP_SPEED = 18f;
    public static final float WOBBLE = 6f;
    public static final float WOBBLE_RATE = 0.7f;
    public static final float RETURN_RATE = 4f;

    public static final float LOCK_SCAN = 0.5f;
    public static final float LOCK_EASE_TIME = 1.6f;
    public static final float LOCK_HOLD = 3f;
    public static final float LOCK_COOLDOWN = 4f;

    public static final float SPAWN_MIN = 0.8f;
    public static final float SPAWN_MAX = 1.2f;
    public static final float SPAWN_NEAR = 0.15f;
    public static final float SPAWN_SPREAD = 0.85f;

    private float sweep;
    private int sweepDir = Math.random() < 0.5 ? -1 : 1;
    private float wobbleTime;

    private ShipAPI lockTarget;
    private float lockLeft;
    private float lockBlend;
    private float lockCooldown;
    private float lockScan;
    private float lockOffset;

    private float level;
    private boolean lit;
    private float spawnTimer = SPAWN_MAX;
    private final Vector2f origin = new Vector2f();
    private float direction;

    private BreachLightRenderer renderer;

    public static List<BreachLightEffect> lampsOf(ShipAPI ship) {
        List<BreachLightEffect> lamps = new ArrayList<>();
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (WEAPON_ID.equals(weapon.getId()) && weapon.getEffectPlugin() instanceof BreachLightEffect lamp) {
                lamps.add(lamp);
            }
        }
        return lamps;
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (ship == null) return;
        if (renderer == null) renderer = BreachLightRenderer.attach(engine, this, ship);

        level = BreachLightsStats.lightOf(ship);
        if (!engine.isPaused()) {
            float half = weapon.getArc() / 2f;
            if (level > 0f) {
                // the sweep holds while a lock leans the light away, and resumes from where it stopped
                if (lockTarget == null) advanceSweep(half, amount);
                wobbleTime += amount;
                advanceLock(amount, engine, weapon, half);
            } else {
                sweep -= sweep * Math.min(1f, RETURN_RATE * amount);
                lockTarget = null;
                lockBlend = Math.max(0f, lockBlend - amount / LOCK_EASE_TIME);
            }

            float offset = sweep + WOBBLE * level * (float) Math.sin(wobbleTime * WOBBLE_RATE);
            offset += (lockOffset - offset) * TrigHelper.smootherStep(lockBlend);
            weapon.setFacing(ship.getFacing() + weapon.getSlot().getAngle() + MathUtils.clamp(offset, -half, half));
        }

        updateCone(weapon);
        advanceSound(ship);
    }

    private void advanceSweep(float half, float amount) {
        sweep += SWEEP_SPEED * sweepDir * amount;
        if (Math.abs(sweep) < half) return;
        sweep = MathUtils.clamp(sweep, -half, half);
        sweepDir = -sweepDir;
    }

    private void advanceLock(float amount, CombatEngineAPI engine, WeaponAPI weapon, float half) {
        if (lockCooldown > 0f) lockCooldown -= amount;

        if (lockTarget != null) {
            lockLeft -= amount;
            float bearing = bearingOf(weapon, lockTarget.getLocation());
            if (lockLeft <= 0f || !isLockable(weapon, lockTarget, bearing, half)) {
                lockTarget = null;
                lockCooldown = LOCK_COOLDOWN;
            } else {
                lockOffset = bearing;
            }
        }

        lockScan -= amount;
        if (lockTarget == null && lockBlend <= 0f && lockCooldown <= 0f && lockScan <= 0f) {
            lockScan = LOCK_SCAN;
            acquire(engine, weapon, half);
        }

        float step = amount / LOCK_EASE_TIME;
        lockBlend = MathUtils.clamp(lockBlend + (lockTarget != null ? step : -step), 0f, 1f);
    }

    // nearest to the beam's axis, not the nearest ship, so the light leans rather than snaps
    private void acquire(CombatEngineAPI engine, WeaponAPI weapon, float half) {
        float current = sweep;
        ShipAPI best = null;
        float bestTurn = Float.MAX_VALUE;
        for (ShipAPI other : engine.getShips()) {
            float bearing = bearingOf(weapon, other.getLocation());
            if (!isLockable(weapon, other, bearing, half)) continue;

            float turn = Math.abs(bearing - current);
            if (turn >= bestTurn) continue;
            bestTurn = turn;
            best = other;
        }
        if (best == null) return;

        lockTarget = best;
        lockLeft = LOCK_HOLD;
        lockOffset = bearingOf(weapon, best.getLocation());
    }

    private boolean isLockable(WeaponAPI weapon, ShipAPI other, float bearing, float half) {
        ShipAPI ship = weapon.getShip();
        if (!other.isAlive() || other.isHulk() || other.isFighter() || other.getOwner() == ship.getOwner()) {
            return false;
        }
        if (Math.abs(bearing) > half) return false;
        return Misc.getDistance(origin, other.getLocation()) - other.getCollisionRadius() <= REACH;
    }

    // relative to the slot's own angle, as the sweep is
    private float bearingOf(WeaponAPI weapon, Vector2f point) {
        float slotFacing = weapon.getShip().getFacing() + weapon.getSlot().getAngle();
        return MathUtils.getShortestRotation(slotFacing, Misc.getAngleInDegrees(weapon.getLocation(), point));
    }

    private void updateCone(WeaponAPI weapon) {
        direction = weapon.getCurrAngle();
        Vector2f unit = Misc.getUnitVectorAtDegreeAngle(direction);
        Vector2f at = weapon.getLocation();
        origin.set(at.x + unit.x * LENS_OFFSET, at.y + unit.y * LENS_OFFSET);
    }

    private void advanceSound(ShipAPI ship) {
        if (level <= 0f) {
            lit = false;
            return;
        }
        if (lit) return;
        lit = true;
        FishingSounds.playSound("catchrelease_ui_searchlight_toggle", 1.1f, 1.3f, origin, ship.getVelocity());
    }

    public boolean tickSpawn(float amount) {
        spawnTimer -= amount;
        if (spawnTimer > 0f) return false;
        spawnTimer = MathUtils.getRandomNumberInRange(SPAWN_MIN, SPAWN_MAX);
        return true;
    }

    public Vector2f randomPoint() {
        return pointInCone(origin, direction, (float) Math.random(), (float) Math.random());
    }

    // area-uniform over the cone past SPAWN_NEAR, so the wide far end gets most of them
    public static Vector2f pointInCone(Vector2f origin, float direction, float u, float v) {
        float near = SPAWN_NEAR * SPAWN_NEAR;
        float distance = REACH * (float) Math.sqrt(near + (1f - near) * u);
        float angle = direction + (2f * v - 1f) * HALF_ANGLE * SPAWN_SPREAD;
        return MathUtils.getPointOnCircumference(origin, distance, angle);
    }

    public float getLevel() {
        return level;
    }

    public Vector2f getOrigin() {
        return origin;
    }

    public float getDirection() {
        return direction;
    }
}
