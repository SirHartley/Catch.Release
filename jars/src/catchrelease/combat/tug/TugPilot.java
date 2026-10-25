package catchrelease.combat.tug;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

// AI-side control of an AI-flown Stigmata, added by its Tug Drive Field hullmod. An AdvanceableListener runs at the
// start of Ship.advance, after the ship AI and before commands and weapons are processed, so what it gives, blocks or
// forces acts in the same frame. Wrapping the default ship AI instead is not possible: BasicShipAI.advance throws
// when the ship's AI is not itself. Does nothing while the player flies the ship without autopilot.
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
    // With this much friendly ship weight within ARRIVE_RANGE of it the catch is in friendly guns, and the tug holds
    // it there: it brakes, and pulls against the catch only while the rope is taut and the catch moves away along it
    // faster than PULL_SPEED by its own velocity (on a taut rope the two move together, so their relative speed reads
    // near zero while the catch drags the tug).
    public static final float ARRIVE_RANGE = 800f;
    public static final float ARRIVE_WEIGHT = 2f;
    public static final float TAUT_SHARE = 0.97f;
    public static final float PULL_SPEED = 20f;
    public static final float TURN_GAIN = 2f;
    public static final float TURN_DEADBAND = 2f;
    public static final float THRUST_ARC = 60f;
    // How often the pull of nearby ships is re-summed; the heading itself follows the catch every frame.
    public static final float BIAS_INTERVAL = 0.25f;
    // The closest the default AI may hold to its target, centre to centre: this share of harpoon range plus the
    // target's radius.
    public static final float STANDOFF_SHARE = 0.8f;
    // The friend the tug keeps with is the heaviest combat ship, discounted by distance over ESCORT_FALLOFF; it is
    // picked again every ESCORT_INTERVAL and replaced only by one ESCORT_SWITCH times as good.
    public static final float ESCORT_INTERVAL = 2f;
    public static final float ESCORT_SWITCH = 1.5f;
    public static final float ESCORT_FALLOFF = 2000f;

    private final ShipAPI ship;
    private WeaponAPI harpoon;
    private final Vector2f pull = new Vector2f();
    private final Vector2f catchPoint = new Vector2f();
    private final Vector2f mountPoint = new Vector2f();
    private float biasAge = BIAS_INTERVAL;
    private boolean arrived;
    private float swingSide = 1f;
    private ShipAPI escort;
    private float escortAge = ESCORT_INTERVAL;

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
        if (tether != null && !isWorthHolding(tether)) {
            tethers.release(tether);
            tether = null;
        }
        if (tether == null) {
            biasAge = BIAS_INTERVAL;
            keepBack(engine, weapon, amount);
            Vector2f aim = getReadyAim(weapon, tethers);
            if (aim != null) aimWithHull(weapon, aim);
            return;
        }
        tow(engine, tether, amount);
    }

    // Without a line out the default ship AI moves the tug. It sizes its engagement range from the weapons without
    // NO_MANUAL_FIRE (AttackAIModule), and the harpoon has that hint, so with nothing else fitted, or only point
    // defence, it closes to ramming range. Both flags are set again every frame and lapse a second after the ship
    // stops being AI-flown. BACK_OFF_MIN_RANGE floors the strafe maneuver's distance to its target. ESCORT_OTHER_SHIP
    // is read before any fleet order (the order module skips it only when retreating), so it is left off while the
    // tug has an assignment; TIMID_ESCORT keeps the escort defensive. The AI reads the escort flag only when it
    // picks a new maneuver, hence the cancel on a change.
    private void keepBack(CombatEngineAPI engine, WeaponAPI weapon, float amount) {
        ShipwideAIFlags flags = ship.getAIFlags();
        ShipAPI target = ship.getShipTarget();
        float standoff = weapon.getRange() * STANDOFF_SHARE + (target == null ? 0f : target.getCollisionRadius());
        flags.setFlag(AIFlags.BACK_OFF_MIN_RANGE, 1f, standoff);

        escortAge += amount;
        if (escortAge >= ESCORT_INTERVAL || (escort != null && !isEscortable(escort))) {
            escortAge = 0f;
            ShipAPI picked = hasAssignment(engine) ? null : pickEscort(engine);
            if (picked != escort) {
                escort = picked;
                if (escort == null) {
                    flags.unsetFlag(AIFlags.ESCORT_OTHER_SHIP);
                    flags.unsetFlag(AIFlags.TIMID_ESCORT);
                }
                ship.getShipAI().cancelCurrentManeuver();
            }
        }
        if (escort != null) {
            flags.setFlag(AIFlags.ESCORT_OTHER_SHIP, 1f, escort);
            flags.setFlag(AIFlags.TIMID_ESCORT, 1f);
        }
    }

    private ShipAPI pickEscort(CombatEngineAPI engine) {
        ShipAPI best = null;
        float bestScore = 0f;
        float currentScore = 0f;
        for (ShipAPI other : engine.getShips()) {
            if (!isEscortable(other)) continue;
            float score = Misc.getShipWeight(other)
                    / (1f + Misc.getDistance(ship.getLocation(), other.getLocation()) / ESCORT_FALLOFF);
            if (other == escort) currentScore = score;
            if (score > bestScore) {
                bestScore = score;
                best = other;
            }
        }
        if (currentScore > 0f && bestScore < currentScore * ESCORT_SWITCH) return escort;
        return best;
    }

    // Other tugs are left out, so two never keep with each other away from the fight.
    private boolean isEscortable(ShipAPI other) {
        return other != ship && other.getOwner() == ship.getOwner() && other.isAlive() && !other.isHulk()
                && !other.isFighter() && !other.isStationModule() && !other.isRetreating()
                && !other.isNonCombat(false) && !other.hasListenerOfClass(TugPilot.class);
    }

    private boolean hasAssignment(CombatEngineAPI engine) {
        CombatFleetManagerAPI fleet = engine.getFleetManager(ship.getOwner());
        CombatTaskManagerAPI tasks = fleet == null ? null : fleet.getTaskManager(ship.isAlly());
        return tasks != null && tasks.getAssignmentFor(ship) != null;
    }

    // The aim point of a loaded harpoon with a target and no line out, or null.
    private Vector2f getReadyAim(WeaponAPI weapon, TugTethers tethers) {
        if (isInTrouble() || weapon.getAmmo() <= 0 || (tethers != null && tethers.getLine(weapon) != null)) {
            return null;
        }
        WeaponGroupAPI group = ship.getWeaponGroupFor(weapon);
        if (group == null || !group.isAutofiring()) return null;
        return group.getAutofirePlugin(weapon) instanceof HarpoonAutofireAI ai ? ai.getTarget() : null;
    }

    // The turret turns far slower than the hull, so the hull turns to put the target on the turret's current
    // bearing while the turret closes the rest. Only turning is overridden; the ship AI keeps its thrust.
    private void aimWithHull(WeaponAPI weapon, Vector2f aim) {
        float bearing = Misc.getAngleInDegrees(weapon.getLocation(), aim);
        float turretOffset = HarpoonAutofireAI.angleDifference(ship.getFacing(), weapon.getCurrAngle());
        turnToward(bearing - turretOffset);
    }

    // Drags the catch where friendly guns are and holds it there, overriding the ship AI's movement: the wanted
    // commands are given and every other movement command blocked, since the engine drops a blocked type even
    // when given.
    private void tow(CombatEngineAPI engine, Tether tether, float amount) {
        tether.getTargetPoint(catchPoint);
        biasAge += amount;
        if (biasAge >= BIAS_INTERVAL) {
            biasAge = 0f;
            sumPull(engine, tether.getTarget());
        }
        float rope = Misc.getAngleInDegrees(catchPoint, ship.getLocation());
        if (arrived) {
            if (isPulledAway(tether)) steer(rope);
            else brake();
            return;
        }
        float want = pull.lengthSquared() > 1e-8f ? Misc.getAngleInDegrees(pull) : rope;
        float off = HarpoonAutofireAI.angleDifference(rope, want);
        if (Math.abs(off) <= SWING_HOLD && off != 0f) swingSide = Math.signum(off);
        steer(dragHeading(rope, want, swingSide));
    }

    private boolean isPulledAway(Tether tether) {
        Vector2f line = Vector2f.sub(catchPoint, tether.getMountPoint(mountPoint), null);
        float distance = line.length();
        if (distance < tether.getLength() * TAUT_SHARE || distance <= 0f) return false;
        return Vector2f.dot(tether.getTarget().getVelocity(), line) / distance > PULL_SPEED;
    }

    // Where the catch should go, as a direction from it: toward friendly ships and away from enemies, each by weight,
    // over ships within SCAN_RANGE of the catch; also whether it has arrived. Other tugs carry no guns to drag a catch
    // into. A list pass, not the ship grid: a grid query copies every object in its 300-unit cells into a new set,
    // which costs more at this range.
    private void sumPull(CombatEngineAPI engine, ShipAPI towed) {
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

    // The rope gives a dragged ship a top-speed allowance that follows its speed (see Tether), so thrust is held at the
    // hull's own top speed along the heading; past that it would build speed without end and fling the catch past
    // friendly guns.
    private void steer(float heading) {
        float difference = turnToward(heading);
        Vector2f velocity = ship.getVelocity();
        double radians = Math.toRadians(heading);
        float ahead = (float) (velocity.x * Math.cos(radians) + velocity.y * Math.sin(radians));
        boolean mayThrust = ahead < ship.getHullSpec().getEngineSpec().getMaxSpeed();
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

    // Holds still. Turning is blocked too: it moves the off-centre mount, which on a taut rope swings the catch round.
    private void brake() {
        ship.giveCommand(ShipCommand.DECELERATE, null, 0);
        ship.blockCommandForOneFrame(ShipCommand.ACCELERATE);
        ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
        ship.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT);
        ship.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT);
        ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT);
        ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);
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

    // A friendly ship or a wreck is never worth towing for the AI. Neither is a catch it cannot move while the tug is
    // in trouble or out of drive charges.
    private boolean isWorthHolding(Tether tether) {
        ShipAPI target = tether.getTarget();
        if (target.isHulk() || target.getOwner() == ship.getOwner() || isInTrouble()) return false;
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
