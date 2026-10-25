package catchrelease.combat.tug;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

// AI-side control of an AI-flown Stigmata, added by its Tug Drive Field hullmod. An AdvanceableListener runs at the
// start of Ship.advance, after the ship AI and before commands and weapons are processed, so what it gives, blocks or
// forces acts in the same frame. Wrapping the default ship AI instead is not possible: BasicShipAI.advance throws
// when the ship's AI is not itself. Does nothing while the player flies the ship without autopilot. It only fires the
// harpoon and tows: with no line out the default AI flies the ship, shields and targets included.
public class TugPilot implements AdvanceableListener {

    public static final float DANGER_HULL = 0.35f;
    public static final float DANGER_FLUX = 0.85f;
    public static final float HOPELESS_MASS_RATIO = 3f;
    public static final float HOPELESS_TIME = 4f;
    public static final float SCAN_RANGE = 3000f;
    // A ship's say in where the catch goes falls off as 1 / (1 + distance / PULL_FALLOFF).
    public static final float PULL_FALLOFF = 1000f;
    // The tug heads the way the catch should go once the rope is within MAX_SWING of it; until then it swings round
    // the catch at MAX_SWING off the rope, which keeps the rope taut. Past SWING_HOLD off it keeps the side it was
    // swinging to, so a catch lying straight between the tug and its goal does not leave it dithering.
    public static final float MAX_SWING = 60f;
    public static final float SWING_HOLD = 150f;
    // A rescued friend with this much friendly ship weight within ARRIVE_RANGE of it is back at friendly lines and
    // let go.
    public static final float ARRIVE_RANGE = 800f;
    public static final float ARRIVE_WEIGHT = 2f;
    public static final float TURN_GAIN = 2f;
    public static final float TURN_DEADBAND = 2f;
    public static final float THRUST_ARC = 60f;
    // How often the pull of nearby ships is re-summed; the heading itself follows the catch every frame.
    public static final float BIAS_INTERVAL = 0.25f;

    private final ShipAPI ship;
    private WeaponAPI harpoon;
    private final Vector2f pull = new Vector2f();
    private final Vector2f catchPoint = new Vector2f();
    private float biasAge = BIAS_INTERVAL;
    private boolean arrived;
    private boolean friendSafe;
    private float swingSide = 1f;
    private Tether towing;

    public TugPilot(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship.getShipAI() == null || !ship.isAlive()) return;
        WeaponAPI weapon = getHarpoon();
        if (weapon == null) return;

        // The ship AI can also fire a group by hand, past the autofire plugin; only this flag stops every source.
        if (!wantsToFire(weapon)) weapon.setForceNoFireOneFrame(true);

        TugTethers tethers = TugTethers.peek(engine);
        Tether tether = tethers == null ? null : tethers.getTether(ship);
        if (tether != null) {
            tether.getTargetPoint(catchPoint);
            biasAge += amount;
            if (biasAge >= BIAS_INTERVAL) {
                biasAge = 0f;
                sumPull(engine, tether.getTarget());
            }
            if (!isWorthHolding(tether)) {
                tethers.release(tether);
                tether = null;
            }
        }
        towing = tether;
        if (tether == null) {
            biasAge = BIAS_INTERVAL;
            return;
        }
        tow(tether);
    }

    // The line this pilot holds and tows, or null; TugDriveAI hauls only on it.
    public Tether getTowing() {
        return towing;
    }

    // Drags the catch toward friendly guns, overriding the ship AI's movement: the wanted
    // commands are given and every other movement command blocked, since the engine drops a blocked type even
    // when given.
    private void tow(Tether tether) {
        float rope = Misc.getAngleInDegrees(catchPoint, ship.getLocation());
        float want = pull.lengthSquared() > 1e-8f ? Misc.getAngleInDegrees(pull) : rope;
        float off = HarpoonAutofireAI.angleDifference(rope, want);
        if (Math.abs(off) <= SWING_HOLD && off != 0f) swingSide = Math.signum(off);
        float heading = dragHeading(rope, want, swingSide);
        // Swinging round the catch is mostly sideways motion, and the rope's top-speed allowance (see Tether) follows
        // speed, so thrust while swinging would wind the pair up without end; a straight haul is left uncapped.
        boolean swinging = Math.abs(off) > MAX_SWING;
        steer(heading, !swinging || speedAlong(heading) < ship.getHullSpec().getEngineSpec().getMaxSpeed());
    }

    private float speedAlong(float heading) {
        double radians = Math.toRadians(heading);
        Vector2f velocity = ship.getVelocity();
        return (float) (velocity.x * Math.cos(radians) + velocity.y * Math.sin(radians));
    }

    // Where the catch should go, as a direction from it: toward friendly ships and away from enemies, each by weight,
    // over ships within SCAN_RANGE of the catch; also, for a friend, whether it is back at friendly lines or safe.
    // Other tugs carry no guns to drag a catch into. A list pass, not the ship grid: a grid query copies every object
    // in its 300-unit cells into a new set, which costs more at this range.
    private void sumPull(CombatEngineAPI engine, ShipAPI towed) {
        friendSafe = towed.getOwner() == ship.getOwner() && HarpoonAutofireAI.isSafe(engine, ship, towed);
        pull.set(0f, 0f);
        float near = 0f;
        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other == towed || other.isFighter() || !other.isAlive() || other.isHulk()) continue;
            boolean friendly = other.getOwner() == ship.getOwner();
            if (friendly ? other.hasListenerOfClass(TugPilot.class) : !engine.isAwareOf(ship.getOwner(), other)) {
                continue;
            }
            float dx = other.getLocation().x - catchPoint.x;
            float dy = other.getLocation().y - catchPoint.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance > SCAN_RANGE || distance < 1f) continue;
            float weight = Misc.getShipWeight(other);
            if (friendly && distance <= ARRIVE_RANGE) near += weight;
            float share = (friendly ? weight : -weight) / (distance * (1f + distance / PULL_FALLOFF));
            pull.x += dx * share;
            pull.y += dy * share;
        }
        arrived = near >= ARRIVE_WEIGHT;
    }

    // The tug's heading in degrees, from the rope's direction (catch to tug) and the way the catch should go: that way
    // once the rope is within MAX_SWING of it, so the catch follows the tug; otherwise round the catch on side (+1
    // counter-clockwise, -1 clockwise) at MAX_SWING off the rope, toward the goal unless past SWING_HOLD.
    public static float dragHeading(float rope, float want, float side) {
        float off = HarpoonAutofireAI.angleDifference(rope, want);
        if (Math.abs(off) <= MAX_SWING) return want;
        float turn = Math.abs(off) > SWING_HOLD ? side : Math.signum(off);
        return rope + turn * MAX_SWING;
    }

    private void steer(float heading, boolean mayThrust) {
        float difference = turnToward(heading);
        if (mayThrust && Math.abs(difference) < THRUST_ARC) {
            ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
        } else {
            ship.blockCommandForOneFrame(ShipCommand.ACCELERATE);
        }
        ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
        ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
        ship.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT);
        ship.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT);
    }

    // Turns with an angular-speed target (TURN_GAIN times the angle, capped at the turn rate) and blocks the ship
    // AI's own turning; returns the angle left.
    private float turnToward(float heading) {
        float difference = HarpoonAutofireAI.angleDifference(ship.getFacing(), heading);
        float maxTurn = ship.getMaxTurnRate();
        float wanted = Math.max(-maxTurn, Math.min(maxTurn, difference * TURN_GAIN));
        float spin = ship.getAngularVelocity();
        if (spin < wanted - TURN_DEADBAND) {
            ship.giveCommand(ShipCommand.TURN_LEFT, null, 0);
            ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);
        } else if (spin > wanted + TURN_DEADBAND) {
            ship.giveCommand(ShipCommand.TURN_RIGHT, null, 0);
            ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT);
        } else {
            ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT);
            ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);
        }
        return difference;
    }

    // The plugin only advances while its group is on autofire, so off autofire its answer is stale. A tug in trouble
    // would let go of the line at once, so it does not spend a charge on one.
    private boolean wantsToFire(WeaponAPI weapon) {
        if (isInTrouble()) return false;
        WeaponGroupAPI group = ship.getWeaponGroupFor(weapon);
        if (group == null || !group.isAutofiring()) return false;
        AutofireAIPlugin plugin = group.getAutofirePlugin(weapon);
        return plugin instanceof HarpoonAutofireAI && plugin.shouldFire();
    }

    // A wreck is never worth towing for the AI, nor a friend once it is safe or back at friendly lines, since a
    // tethered ship cannot manoeuvre (one hooked by chance is safe at once). Neither is a catch the tug cannot move
    // while it is in trouble or out of drive charges.
    private boolean isWorthHolding(Tether tether) {
        ShipAPI target = tether.getTarget();
        boolean friend = target.getOwner() == ship.getOwner();
        if (target.isHulk() || (friend && (friendSafe || arrived)) || isInTrouble()) return false;
        boolean hopeless = target.getMass() > ship.getMass() * HOPELESS_MASS_RATIO
                && (ship.getSystem() == null || ship.getSystem().isOutOfAmmo());
        return !(hopeless && tether.getAge() > HOPELESS_TIME);
    }

    private boolean isInTrouble() {
        return ship.getHullLevel() < DANGER_HULL || ship.getFluxTracker().getFluxLevel() > DANGER_FLUX;
    }

    private WeaponAPI getHarpoon() {
        if (harpoon == null) {
            for (WeaponAPI weapon : ship.getAllWeapons()) {
                if (HarpoonOnHit.WEAPON_ID.equals(weapon.getId())) harpoon = weapon;
            }
        }
        return harpoon;
    }
}
