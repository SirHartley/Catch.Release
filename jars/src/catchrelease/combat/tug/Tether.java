package catchrelease.combat.tug;

import catchrelease.config.Setting;
import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.CollisionUtils;
import org.lwjgl.util.vector.Vector2f;

// One harpoon line: in flight from the mount to the shot, then attached at a point fixed in the target's frame with
// the length it had at impact.
public class Tether {

    public enum State { FLIGHT, ATTACHED, RELEASED }

    public static final float MIN_LENGTH = 60f;
    public static final float DURATION = 20f;
    // A stretched rope closes at this rate per unit of stretch, capped, instead of snapping back in one frame.
    public static final float CORRECTION_RATE = 4f;
    public static final float MAX_CORRECTION_SPEED = 200f;
    public static final float MAX_SPIN_CHANGE = 30f;
    // The mount sits inside the hull bounds, so the rope always enters the hull just before it; only an entry
    // further from the mount than this means the rope runs through the ship.
    public static final float HULL_MARGIN = 40f;
    public static final float SPEED_MARGIN = 5f;
    public static final float SPEED_ALLOWANCE_DECAY = 200f;

    private static int nextId;

    private final CombatEngineAPI engine;
    private final WeaponAPI weapon;
    private final ShipAPI tug;
    private State state = State.FLIGHT;
    private DamagingProjectileAPI shot;

    private ShipAPI target;
    private final Vector2f localPoint = new Vector2f();
    private final Vector2f mount = new Vector2f();
    private final Vector2f point = new Vector2f();
    private final Vector2f normal = new Vector2f();
    private float length;
    private float headAngle;
    private boolean targetWasHulk;
    private float age;

    // The engine brakes a ship above its top speed whatever it is doing, so a dragged ship gets a top-speed
    // allowance that follows the speed the rope gave it and decays once the rope stops pulling.
    private final String speedKey = "catchrelease_tether_" + nextId++;
    private float tugAllowance;
    private float targetAllowance;

    Tether(CombatEngineAPI engine, WeaponAPI weapon, DamagingProjectileAPI shot) {
        this.engine = engine;
        this.weapon = weapon;
        this.tug = weapon.getShip();
        this.shot = shot;
        CombatEntityAPI rendered = engine.addLayeredRenderingPlugin(new TetherRenderer(this));
        getMountPoint(rendered.getLocation());
    }

    void attach(ShipAPI target, Vector2f point, float facing) {
        if (state != State.FLIGHT) return;
        this.target = target;
        toLocal(target, point, localPoint);
        headAngle = facing - target.getFacing();
        length = Math.max(MIN_LENGTH, Misc.getDistance(point, getMountPoint(mount)));
        targetWasHulk = target.isHulk();
        shot = null;
        state = State.ATTACHED;
    }

    void release() {
        if (state == State.RELEASED) return;
        if (weapon.getEffectPlugin() instanceof HarpoonWeaponEffect effect) effect.onReleased();
        if (state == State.ATTACHED) {
            tug.getMutableStats().getMaxSpeed().unmodifyFlat(speedKey);
            target.getMutableStats().getMaxSpeed().unmodifyFlat(speedKey);
        }
        state = State.RELEASED;
        shot = null;
    }

    void advance(float amount) {
        if (state == State.RELEASED) return;
        if (!engine.isEntityInPlay(tug) || tug.isHulk()) {
            release();
            return;
        }
        if (state == State.FLIGHT) {
            if (shot == null || shot.isExpired() || shot.didDamage() || !engine.isEntityInPlay(shot)) release();
            return;
        }
        age += amount;
        if (age > DURATION || !engine.isEntityInPlay(target) || target.isExpired()
                || target.isHulk() != targetWasHulk) {
            release();
            return;
        }

        getMountPoint(mount);
        getTargetPoint(point);
        boolean taut = Misc.getDistance(point, mount) > length;
        if (taut && Setting.HARPOON_ROPE_HULL_BREAK.enabled() && crossesTugHull(point, mount)) {
            release();
            return;
        }

        float impulse = solve(mount, point, tug.getVelocity(), target.getVelocity(),
                inverseMass(tug), inverseMass(target), length, normal);
        if (impulse > 0f) {
            applyImpulse(tug, mount, normal, impulse);
            applyImpulse(target, point, normal, -impulse);
        }
        tugAllowance = updateAllowance(tug, tugAllowance, impulse > 0f, amount);
        targetAllowance = updateAllowance(target, targetAllowance, impulse > 0f, amount);
    }

    // The test runs from the target end, outside the tug: LazyLib reports a line that starts inside the bounds as a
    // hit at its start.
    private boolean crossesTugHull(Vector2f point, Vector2f mount) {
        BoundsAPI bounds = tug.getExactBounds();
        if (bounds == null) return false;
        bounds.update(tug.getLocation(), tug.getFacing());
        Vector2f entry = CollisionUtils.getCollisionPoint(point, mount, tug);
        return entry != null && Vector2f.sub(entry, mount, null).length() > HULL_MARGIN;
    }

    // Velocity constraint for a rope from a to b: the impulse to add to A along outNormal (the unit vector from A to
    // B) and take from B, or 0 while the rope is slack or the ends already close fast enough. Split by inverse mass,
    // so the lighter end moves more.
    public static float solve(Vector2f a, Vector2f b, Vector2f velocityA, Vector2f velocityB,
                              float inverseMassA, float inverseMassB, float length, Vector2f outNormal) {
        Vector2f.sub(b, a, outNormal);
        float distance = outNormal.length();
        if (distance <= length || inverseMassA + inverseMassB <= 0f) return 0f;
        outNormal.scale(1f / distance);
        float separating = (velocityB.x - velocityA.x) * outNormal.x + (velocityB.y - velocityA.y) * outNormal.y;
        float wanted = -Math.min((distance - length) * CORRECTION_RATE, MAX_CORRECTION_SPEED);
        if (separating <= wanted) return 0f;
        return (separating - wanted) / (inverseMassA + inverseMassB);
    }

    private static float inverseMass(ShipAPI ship) {
        return 1f / Math.max(1f, ship.getMass());
    }

    // Linear change plus a turn from pulling off the centre of mass, with the hull treated as a uniform disc.
    private static void applyImpulse(ShipAPI ship, Vector2f at, Vector2f normal, float impulse) {
        float inverseMass = inverseMass(ship);
        ship.getVelocity().translate(normal.x * impulse * inverseMass, normal.y * impulse * inverseMass);
        float radius = Math.max(10f, ship.getCollisionRadius());
        float rx = at.x - ship.getLocation().x;
        float ry = at.y - ship.getLocation().y;
        float torque = (rx * normal.y - ry * normal.x) * impulse;
        float spin = (float) Math.toDegrees(torque * inverseMass / (0.5f * radius * radius));
        spin = Math.max(-MAX_SPIN_CHANGE, Math.min(MAX_SPIN_CHANGE, spin));
        ship.setAngularVelocity(ship.getAngularVelocity() + spin);
    }

    private float updateAllowance(ShipAPI ship, float allowance, boolean pulled, float amount) {
        MutableStat maxSpeed = ship.getMutableStats().getMaxSpeed();
        maxSpeed.unmodifyFlat(speedKey);
        allowance = nextAllowance(allowance, ship.getVelocity().length(), maxSpeed.getModifiedValue(), pulled, amount);
        if (allowance > 0f) maxSpeed.modifyFlat(speedKey, allowance);
        return allowance;
    }

    public static float nextAllowance(float allowance, float speed, float topSpeed, boolean pulled, float amount) {
        if (pulled) return Math.max(allowance, speed - topSpeed + SPEED_MARGIN);
        return Math.max(0f, allowance - SPEED_ALLOWANCE_DECAY * amount);
    }

    public Vector2f getMountPoint(Vector2f out) {
        out.set(weapon.getFirePoint(0));
        return out;
    }

    public Vector2f getTargetPoint(Vector2f out) {
        return toWorld(target, localPoint, out);
    }

    static void toLocal(ShipAPI ship, Vector2f world, Vector2f out) {
        rotate(world.x - ship.getLocation().x, world.y - ship.getLocation().y, -ship.getFacing(), out);
    }

    static Vector2f toWorld(ShipAPI ship, Vector2f local, Vector2f out) {
        rotate(local.x, local.y, ship.getFacing(), out);
        out.x += ship.getLocation().x;
        out.y += ship.getLocation().y;
        return out;
    }

    private static void rotate(float x, float y, float degrees, Vector2f out) {
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        out.set(x * cos - y * sin, x * sin + y * cos);
    }

    public State getState() {
        return state;
    }

    public boolean isAttached() {
        return state == State.ATTACHED;
    }

    boolean isDone() {
        return state == State.RELEASED;
    }

    public WeaponAPI getWeapon() {
        return weapon;
    }

    public ShipAPI getTug() {
        return tug;
    }

    public ShipAPI getTarget() {
        return target;
    }

    public DamagingProjectileAPI getShot() {
        return shot;
    }

    void setShot(DamagingProjectileAPI shot) {
        this.shot = shot;
    }

    public float getLength() {
        return length;
    }

    // The head's angle at impact, relative to the target's facing.
    public float getHeadAngle() {
        return headAngle;
    }

    public float getAge() {
        return age;
    }
}
