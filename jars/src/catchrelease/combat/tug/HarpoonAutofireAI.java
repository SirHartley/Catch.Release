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
    // The Stigmata's own mass in ship_data.csv.
    public static final float REFERENCE_MASS = 1000f;
    // A friendly ship in a pinch scores this with its hull open to the mount, before mass and distance, which outranks
    // an exposed enemy frigate but not an overloaded or running one.
    public static final float RESCUE_SCORE = 5f;
    // Ships this close to a ship count toward its danger: the weight of enemies its side is aware of against that of
    // its friends, itself included. It is in a pinch when outweighed SWARMED times over, or at all while overloaded
    // or venting, above RESCUE_FLUX flux or below RESCUE_HULL hull; it is out of it below SAFE_SHARE of its cover.
    public static final float THREAT_RANGE = 1000f;
    public static final float SWARMED = 2f;
    public static final float RESCUE_FLUX = 0.85f;
    public static final float RESCUE_HULL = 0.4f;
    public static final float SAFE_SHARE = 0.75f;
    // Friendly ships and wrecks closer than this share of their radius to the line of fire block the shot; the
    // weapon itself has no friendly-fire check, and the vanilla autofire AI's is not reachable. Raised shields block
    // it too (see isClear).
    public static final float BLOCK_SHARE = 0.8f;
    // A blocked line is checked again after this long; a clear one fires at once and puts a line out.
    public static final float BLOCKED_RECHECK = 0.1f;

    private final WeaponAPI weapon;
    private final Vector2f threatCentre = new Vector2f();
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
        if (rescore <= 0f || (target != null && !isCandidate(engine, ship, target)
                && !isRescueCandidate(engine, ship, target))) {
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
        float support = weightNear(engine, ship, ship.getLocation(), SUPPORT_RANGE, true, null);
        Vector2f mount = weapon.getLocation();
        ShipAPI best = null;
        float bestScore = MIN_SCORE;
        float currentScore = 0f;
        for (ShipAPI other : engine.getShips()) {
            boolean enemy = isCandidate(engine, ship, other);
            if (!enemy && !isRescueCandidate(engine, ship, other)) continue;
            float distance = Misc.getDistance(other.getLocation(), mount) - other.getCollisionRadius();
            if (distance > weapon.getRange() || weapon.distanceFromArc(other.getLocation()) > 0f) continue;
            Shield shield = getShield(other, mount);
            float distanceShare = Math.max(0f, distance) / weapon.getRange();
            float score = enemy
                    ? score(shield, isDisabled(other), isFleeing(other), other.getHullLevel(), other.getMass(), support,
                            distanceShare)
                    : rescue(engine, ship, other, shield, distanceShare);
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
    // fighters excepted. With centre given, it is set to their weighted centre.
    static float weightNear(CombatEngineAPI engine, ShipAPI ship, Vector2f point, float range, boolean friendly,
                            Vector2f centre) {
        float weight = 0f;
        float x = 0f;
        float y = 0f;
        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other.isFighter() || !other.isAlive() || other.isHulk()) continue;
            if ((other.getOwner() == ship.getOwner()) != friendly || other.getOwner() == 100) continue;
            if (!friendly && !engine.isAwareOf(ship.getOwner(), other)) continue;
            Vector2f location = other.getLocation();
            float dx = location.x - point.x;
            float dy = location.y - point.y;
            if (dx * dx + dy * dy > range * range) continue;
            float shipWeight = Misc.getShipWeight(other);
            weight += shipWeight;
            x += location.x * shipWeight;
            y += location.y * shipWeight;
        }
        if (centre != null && weight > 0f) centre.set(x / weight, y / weight);
        return weight;
    }

    // Whether a friend no longer needs saving: the enemies near it weigh less than SAFE_SHARE of its cover.
    static boolean isSafe(CombatEngineAPI engine, ShipAPI ship, ShipAPI friend) {
        float threat = weightNear(engine, ship, friend.getLocation(), THREAT_RANGE, false, null);
        return threat < weightNear(engine, ship, friend.getLocation(), THREAT_RANGE, true, null) * SAFE_SHARE;
    }

    private static boolean isCandidate(CombatEngineAPI engine, ShipAPI ship, ShipAPI other) {
        return other != ship && other.isAlive() && !other.isHulk() && other.getOwner() != ship.getOwner()
                && other.getOwner() != 100 && !other.isFighter() && !other.isStation() && !other.isStationModule()
                && !other.isPhased() && engine.isEntityInPlay(other) && engine.isAwareOf(ship.getOwner(), other);
    }

    // AI-flown friends only: a ship the player flies is left to the player.
    private static boolean isRescueCandidate(CombatEngineAPI engine, ShipAPI ship, ShipAPI other) {
        return other != ship && other.isAlive() && !other.isHulk() && other.getOwner() == ship.getOwner()
                && !other.isFighter() && !other.isStation() && !other.isStationModule() && !other.isPhased()
                && !other.isRetreating() && other.getShipAI() != null && engine.isEntityInPlay(other);
    }

    // A friend's rescue score, zero unless it is in a pinch away from friendly lines (where TugPilot would let it go
    // at once) and the line, which pulls it toward the tug, would take it away from the enemies near it.
    private float rescue(CombatEngineAPI engine, ShipAPI ship, ShipAPI friend, Shield shield, float distanceShare) {
        if (shield == Shield.FACING) return 0f;
        Vector2f location = friend.getLocation();
        float lines = weightNear(engine, ship, location, TugPilot.ARRIVE_RANGE, true, null) - Misc.getShipWeight(friend);
        if (lines >= TugPilot.ARRIVE_WEIGHT) return 0f;
        float threat = weightNear(engine, ship, location, THREAT_RANGE, false, threatCentre);
        if (threat <= 0f) return 0f;
        float cover = weightNear(engine, ship, location, THREAT_RANGE, true, null);
        if (!inPinch(threat, cover, isDisabled(friend), friend.getFluxTracker().getFluxLevel(),
                friend.getHullLevel())) {
            return 0f;
        }
        Vector2f mount = weapon.getLocation();
        float away = (mount.x - location.x) * (location.x - threatCentre.x)
                + (mount.y - location.y) * (location.y - threatCentre.y);
        return away < 0f ? 0f : rescueScore(shield, friend.getMass(), distanceShare);
    }

    public static boolean inPinch(float threat, float cover, boolean disabled, float fluxLevel, float hullLevel) {
        if (threat <= 0f) return false;
        if (threat >= cover * SWARMED) return true;
        return threat >= cover && (disabled || fluxLevel > RESCUE_FLUX || hullLevel < RESCUE_HULL);
    }

    public static float rescueScore(Shield shield, float mass, float distanceShare) {
        float score = switch (shield) {
            case DOWN -> RESCUE_SCORE;
            case FLANKED -> RESCUE_SCORE * 0.75f;
            case FACING -> 0f;
        };
        return score * massFactor(mass) * (1f - 0.3f * clamp(distanceShare));
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
        score *= massFactor(mass);
        score *= clamp(support / FULL_SUPPORT);
        score *= 1f - 0.3f * clamp(distanceShare);
        return score;
    }

    private static float massFactor(float mass) {
        return Math.max(0.3f, Math.min(1.5f, REFERENCE_MASS / Math.max(1f, mass)));
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
