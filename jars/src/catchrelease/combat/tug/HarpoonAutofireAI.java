package catchrelease.combat.tug;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.IdentityHashMap;
import java.util.Map;

// Harpoon targeting, picked in ModPlugin.pickWeaponAutofireAI. The engine only asks it while the weapon's group is
// on autofire; TugPilot stops the ship AI firing the harpoon by hand when this plugin would not.
public class HarpoonAutofireAI implements AutofireAIPlugin {

    public enum Shield { DOWN, FLANKED, FACING }

    public static final float RESCORE_INTERVAL = 0.3f;
    public static final float MIN_SCORE = 0.6f;
    public static final float AIM_TOLERANCE = 3f;
    public static final float HELPER_RANGE = 2000f;
    public static final float REFERENCE_MASS = 600f;
    public static final float RECENT_HIT = 1f;
    // Friendly ships and wrecks closer than this share of their radius to the line of fire block the shot; the
    // weapon itself has no friendly-fire check, and the vanilla autofire AI's is not reachable.
    public static final float BLOCK_SHARE = 0.8f;
    // A blocked line is checked again after this long; a clear one fires at once and puts a line out.
    public static final float BLOCKED_RECHECK = 0.1f;

    private final WeaponAPI weapon;
    private final Map<ShipAPI, Integer> helpers = new IdentityHashMap<>();
    private ShipAPI target;
    private Vector2f aim;
    private float rescore;
    private float blocked;
    private boolean fire;

    public HarpoonAutofireAI(WeaponAPI weapon) {
        this.weapon = weapon;
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        ShipAPI ship = weapon.getShip();
        if (engine == null || ship == null) return;
        rescore -= amount;
        if (rescore <= 0f || (target != null && !isCandidate(engine, ship, target))) {
            rescore = RESCORE_INTERVAL;
            target = pickTarget(engine, ship);
        }
        aim = target == null ? null
                : engine.getAimPointWithLeadForAutofire(ship, 1f, target, weapon.getProjectileSpeed());
        TugTethers tethers = TugTethers.peek(engine);
        boolean lineOut = tethers != null && tethers.getLine(weapon) != null;
        blocked -= amount;
        fire = aim != null && !lineOut && isAimed(aim) && blocked <= 0f;
        if (fire && !isClear(engine, ship, aim)) {
            fire = false;
            blocked = BLOCKED_RECHECK;
        }
    }

    // List passes, not the ship grid: a grid query copies every object in its 300-unit cells into a new set, which
    // costs more at weapon and helper range.
    private ShipAPI pickTarget(CombatEngineAPI engine, ShipAPI ship) {
        countHelpers(engine, ship);
        Vector2f mount = weapon.getLocation();
        ShipAPI best = null;
        float bestScore = MIN_SCORE;
        for (ShipAPI other : engine.getShips()) {
            if (!isCandidate(engine, ship, other)) continue;
            float distance = Vector2f.sub(other.getLocation(), mount, null).length() - other.getCollisionRadius();
            if (distance > weapon.getRange() || weapon.distanceFromArc(other.getLocation()) > 0f) continue;
            float score = score(getShield(other, mount), isDisabled(other), isFleeing(other), other.getHullLevel(),
                    other.getMass(), helpers.getOrDefault(other, 0),
                    other.getSinceLastDamageTaken() < RECENT_HIT, Math.max(0f, distance) / weapon.getRange());
            if (score > bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best;
    }

    private static boolean isCandidate(CombatEngineAPI engine, ShipAPI ship, ShipAPI other) {
        return other != ship && other.isAlive() && !other.isHulk() && other.getOwner() != ship.getOwner()
                && other.getOwner() != 100 && !other.isFighter() && !other.isStation() && !other.isStationModule()
                && !other.isPhased() && engine.isEntityInPlay(other) && engine.isAwareOf(ship.getOwner(), other);
    }

    // Prefers a ship with its shield down or turned away, disabled, running or damaged, light enough to drag, near,
    // and not already the target of friendly ships. A shield facing the mount would only deflect the harpoon.
    public static float score(Shield shield, boolean disabled, boolean fleeing, float hullLevel, float mass,
                              int helpers, boolean recentlyHit, float distanceShare) {
        float score = switch (shield) {
            case DOWN -> 2f;
            case FLANKED -> 1.5f;
            case FACING -> 0.25f;
        };
        if (disabled) score *= 1.5f;
        if (fleeing) score *= 1.5f;
        score *= 1f + 0.5f * (1f - clamp(hullLevel));
        score *= Math.max(0.3f, Math.min(1.5f, REFERENCE_MASS / Math.max(1f, mass)));
        score /= 1f + helpers;
        if (recentlyHit) score *= 0.75f;
        score *= 1f - 0.3f * clamp(distanceShare);
        return score;
    }

    private static Shield getShield(ShipAPI other, Vector2f mount) {
        ShieldAPI shield = other.getShield();
        if (shield == null || !shield.isOn()) return Shield.DOWN;
        return shield.isWithinArc(mount) ? Shield.FACING : Shield.FLANKED;
    }

    private static boolean isDisabled(ShipAPI other) {
        return other.getFluxTracker().isOverloadedOrVenting();
    }

    static boolean isFleeing(ShipAPI other) {
        if (other.isRetreating()) return true;
        return other.getAIFlags() != null && (other.getAIFlags().hasFlag(AIFlags.BACKING_OFF)
                || other.getAIFlags().hasFlag(AIFlags.RUN_QUICKLY));
    }

    // Friendly ships targeting each ship from within HELPER_RANGE of it.
    private void countHelpers(CombatEngineAPI engine, ShipAPI ship) {
        helpers.clear();
        for (ShipAPI ally : engine.getShips()) {
            if (ally == ship || ally.isFighter() || ally.getOwner() != ship.getOwner() || !ally.isAlive()) continue;
            ShipAPI other = ally.getShipTarget();
            if (other == null) continue;
            float dx = ally.getLocation().x - other.getLocation().x;
            float dy = ally.getLocation().y - other.getLocation().y;
            if (dx * dx + dy * dy < HELPER_RANGE * HELPER_RANGE) helpers.merge(other, 1, Integer::sum);
        }
    }

    private boolean isAimed(Vector2f point) {
        Vector2f mount = weapon.getLocation();
        float wanted = (float) Math.toDegrees(Math.atan2(point.y - mount.y, point.x - mount.x));
        float distance = Math.max(1f, Vector2f.sub(point, mount, null).length());
        float tolerance = Math.max(AIM_TOLERANCE,
                (float) Math.toDegrees(Math.atan(target.getCollisionRadius() * 0.5f / distance)));
        return Math.abs(angleDifference(weapon.getCurrAngle(), wanted)) <= tolerance;
    }

    private boolean isClear(CombatEngineAPI engine, ShipAPI ship, Vector2f point) {
        Vector2f mount = weapon.getLocation();
        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other == target || other.isFighter()) continue;
            if (other.getOwner() != ship.getOwner() && !other.isHulk()) continue;
            if (distanceToSegment(other.getLocation(), mount, point) < other.getCollisionRadius() * BLOCK_SHARE) {
                return false;
            }
        }
        return true;
    }

    public static float distanceToSegment(Vector2f p, Vector2f a, Vector2f b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float lengthSquared = dx * dx + dy * dy;
        float t = lengthSquared <= 0f ? 0f : clamp(((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared);
        float x = a.x + t * dx - p.x;
        float y = a.y + t * dy - p.y;
        return (float) Math.sqrt(x * x + y * y);
    }

    static float angleDifference(float from, float to) {
        float difference = (to - from) % 360f;
        if (difference > 180f) difference -= 360f;
        if (difference < -180f) difference += 360f;
        return difference;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @Override
    public boolean shouldFire() {
        return fire;
    }

    @Override
    public void forceOff() {
        fire = false;
    }

    @Override
    public Vector2f getTarget() {
        return aim;
    }

    @Override
    public ShipAPI getTargetShip() {
        return target;
    }

    @Override
    public WeaponAPI getWeapon() {
        return weapon;
    }

    @Override
    public MissileAPI getTargetMissile() {
        return null;
    }
}
