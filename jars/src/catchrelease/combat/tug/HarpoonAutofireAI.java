package catchrelease.combat.tug;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

// Harpoon targeting, picked in ModPlugin.pickWeaponAutofireAI. The engine only asks it while the weapon's group is
// on autofire; TugPilot stops the ship AI firing the harpoon by hand when this plugin would not.
public class HarpoonAutofireAI implements AutofireAIPlugin {

    public enum Shield { DOWN, FLANKED, FACING }

    public static final float RESCORE_INTERVAL = 0.3f;
    public static final float MIN_SCORE = 0.4f;
    // A new target must beat the current one by this factor, so the slow turret is not sent off mid-swing.
    public static final float SWITCH_MARGIN = 1.3f;
    public static final float AIM_TOLERANCE = 3f;
    // Friendly firepower within this range of the tug is where a catch gets dragged. A catch counts in proportion to
    // that ship weight (Misc.getShipWeight: a destroyer is 2) up to FULL_SUPPORT, and not at all with none.
    public static final float SUPPORT_RANGE = 1600f;
    public static final float FULL_SUPPORT = 4f;
    public static final float REFERENCE_MASS = 600f;
    // Friendly ships and wrecks closer than this share of their radius to the line of fire block the shot; the
    // weapon itself has no friendly-fire check, and the vanilla autofire AI's is not reachable. Raised shields block
    // it too (see isClear).
    public static final float BLOCK_SHARE = 0.8f;
    // A blocked line is checked again after this long; a clear one fires at once and puts a line out.
    public static final float BLOCKED_RECHECK = 0.1f;

    private final WeaponAPI weapon;
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
    // costs more at weapon and support range.
    private ShipAPI pickTarget(CombatEngineAPI engine, ShipAPI ship) {
        float support = weightNear(engine, ship, ship.getLocation(), SUPPORT_RANGE, true);
        Vector2f mount = weapon.getLocation();
        ShipAPI best = null;
        float bestScore = MIN_SCORE;
        float currentScore = 0f;
        for (ShipAPI other : engine.getShips()) {
            if (!isCandidate(engine, ship, other)) continue;
            float distance = Misc.getDistance(other.getLocation(), mount) - other.getCollisionRadius();
            if (distance > weapon.getRange() || weapon.distanceFromArc(other.getLocation()) > 0f) continue;
            float score = score(getShield(other, mount), isDisabled(other), isFleeing(other), other.getHullLevel(),
                    other.getMass(), support, Math.max(0f, distance) / weapon.getRange());
            if (other == target) currentScore = score;
            if (score > bestScore) {
                bestScore = score;
                best = other;
            }
        }
        if (currentScore >= MIN_SCORE && bestScore < currentScore * SWITCH_MARGIN) return target;
        return best;
    }

    // Misc.getShipWeight of the tug's friends (or of ships hostile to it) within range of a point, the tug and
    // fighters excepted.
    static float weightNear(CombatEngineAPI engine, ShipAPI ship, Vector2f point, float range, boolean friendly) {
        float weight = 0f;
        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other.isFighter() || !other.isAlive() || other.isHulk()) continue;
            if ((other.getOwner() == ship.getOwner()) != friendly || other.getOwner() == 100) continue;
            if (!friendly && !engine.isAwareOf(ship.getOwner(), other)) continue;
            float dx = other.getLocation().x - point.x;
            float dy = other.getLocation().y - point.y;
            if (dx * dx + dy * dy <= range * range) weight += Misc.getShipWeight(other);
        }
        return weight;
    }

    private static boolean isCandidate(CombatEngineAPI engine, ShipAPI ship, ShipAPI other) {
        return other != ship && other.isAlive() && !other.isHulk() && other.getOwner() != ship.getOwner()
                && other.getOwner() != 100 && !other.isFighter() && !other.isStation() && !other.isStationModule()
                && !other.isPhased() && engine.isEntityInPlay(other) && engine.isAwareOf(ship.getOwner(), other);
    }

    // The harpoon is there to drag ships into friendly guns, so a catch is worth most with friendly firepower near
    // the tug (support, in ship weight), and with its shield down or turned away, disabled, running or damaged,
    // light enough to drag, and near. A shield facing the mount would only deflect the harpoon.
    public static float score(Shield shield, boolean disabled, boolean fleeing, float hullLevel, float mass,
                              float support, float distanceShare) {
        float score = switch (shield) {
            case DOWN -> 2f;
            case FLANKED -> 1.5f;
            case FACING -> 0f;
        };
        if (disabled) score *= 1.5f;
        if (fleeing) score *= 1.5f;
        score *= 1f + 0.5f * (1f - clamp(hullLevel));
        score *= Math.max(0.3f, Math.min(1.5f, REFERENCE_MASS / Math.max(1f, mass)));
        score *= clamp(support / FULL_SUPPORT);
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

    private boolean isAimed(Vector2f point) {
        Vector2f mount = weapon.getLocation();
        float wanted = (float) Math.toDegrees(Math.atan2(point.y - mount.y, point.x - mount.x));
        float distance = Math.max(1f, Misc.getDistance(point, mount));
        float tolerance = Math.max(AIM_TOLERANCE,
                (float) Math.toDegrees(Math.atan(target.getCollisionRadius() * 0.5f / distance)));
        return Math.abs(angleDifference(weapon.getCurrAngle(), wanted)) <= tolerance;
    }

    // The harpoon glances off any raised shield, so besides friendly hulls and wrecks on the line, a shield of any
    // ship but the target's blocks the shot when its arc covers the point where the line would first meet it.
    private boolean isClear(CombatEngineAPI engine, ShipAPI ship, Vector2f point) {
        Vector2f mount = weapon.getLocation();
        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other == target || other.isFighter()) continue;
            boolean friendOrWreck = other.getOwner() == ship.getOwner() || other.isHulk();
            if (friendOrWreck && distanceToSegment(other.getLocation(), mount, point)
                    < other.getCollisionRadius() * BLOCK_SHARE) {
                return false;
            }
            if (!other.isHulk() && shieldBlocks(other.getShield(), mount, point)) return false;
        }
        return true;
    }

    private static boolean shieldBlocks(ShieldAPI shield, Vector2f from, Vector2f to) {
        if (shield == null || !shield.isOn()) return false;
        float share = shieldContact(shield.getLocation(), shield.getRadius(), from, to);
        if (share < 0f) return false;
        return shield.isWithinArc(new Vector2f(from.x + (to.x - from.x) * share, from.y + (to.y - from.y) * share));
    }

    // How far along a to b, as a share, the line first meets the circle, or -1 if it does not. A line that starts
    // inside meets it where it leaves.
    public static float shieldContact(Vector2f centre, float radius, Vector2f a, Vector2f b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float fx = a.x - centre.x;
        float fy = a.y - centre.y;
        float qa = dx * dx + dy * dy;
        float qb = 2f * (fx * dx + fy * dy);
        float qc = fx * fx + fy * fy - radius * radius;
        if (qa <= 0f) return -1f;
        float disc = qb * qb - 4f * qa * qc;
        if (disc < 0f) return -1f;
        float root = (float) Math.sqrt(disc);
        float share = qc <= 0f ? (-qb + root) / (2f * qa) : (-qb - root) / (2f * qa);
        return share >= 0f && share <= 1f ? share : -1f;
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
