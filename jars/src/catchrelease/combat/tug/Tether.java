package catchrelease.combat.tug;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lwjgl.util.vector.Vector2f;

// One harpoon line: in flight from the mount to the shot, then attached at a point fixed in the target's frame with
// the length it had at impact.
public class Tether {

    public enum State { FLIGHT, ATTACHED, RELEASED }

    public static final float MIN_LENGTH = 60f;

    private final CombatEngineAPI engine;
    private final WeaponAPI weapon;
    private final ShipAPI tug;
    private State state = State.FLIGHT;
    private DamagingProjectileAPI shot;

    private ShipAPI target;
    private final Vector2f localPoint = new Vector2f();
    private float length;
    private boolean targetWasHulk;

    Tether(CombatEngineAPI engine, WeaponAPI weapon, DamagingProjectileAPI shot) {
        this.engine = engine;
        this.weapon = weapon;
        this.tug = weapon.getShip();
        this.shot = shot;
    }

    void attach(ShipAPI target, Vector2f point) {
        if (state != State.FLIGHT) return;
        this.target = target;
        toLocal(target, point, localPoint);
        length = Math.max(MIN_LENGTH, Vector2f.sub(point, getMountPoint(), null).length());
        targetWasHulk = target.isHulk();
        shot = null;
        state = State.ATTACHED;
    }

    void release() {
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
        if (!engine.isEntityInPlay(target) || target.isExpired() || target.isHulk() != targetWasHulk) release();
    }

    public Vector2f getMountPoint() {
        return new Vector2f(weapon.getFirePoint(0));
    }

    public Vector2f getTargetPoint() {
        return toWorld(target, localPoint, new Vector2f());
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
}
