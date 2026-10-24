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
// when the ship's AI is not itself. Does nothing while the player flies the ship without autopilot.
public class TugPilot implements AdvanceableListener {

    public static final float DANGER_HULL = 0.35f;
    public static final float DANGER_FLUX = 0.85f;
    public static final float HOPELESS_MASS_RATIO = 3f;
    public static final float HOPELESS_TIME = 4f;
    public static final float SCAN_RANGE = 3000f;
    // Straight away from the catch counts this much against the pull of friends and push of enemies, so the
    // heading always keeps the rope taut.
    public static final float AWAY_WEIGHT = 1.5f;
    public static final float TURN_GAIN = 2f;
    public static final float TURN_DEADBAND = 2f;
    public static final float THRUST_ARC = 60f;

    private final ShipAPI ship;
    private WeaponAPI harpoon;

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
        if (tether != null) tow(engine, tether);
    }

    // Drags the catch toward friendly ships and away from enemies, overriding the ship AI's movement: the wanted
    // commands are given and every other movement command blocked, since the engine drops a blocked type even
    // when given.
    private void tow(CombatEngineAPI engine, Tether tether) {
        Vector2f bias = new Vector2f();
        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other == tether.getTarget() || !other.isAlive() || other.isHulk()
                    || other.isFighter()) {
                continue;
            }
            Vector2f offset = Vector2f.sub(other.getLocation(), ship.getLocation(), null);
            float distance = offset.length();
            if (distance > SCAN_RANGE || distance < 1f) continue;
            boolean friendly = other.getOwner() == ship.getOwner();
            if (!friendly && !engine.isAwareOf(ship.getOwner(), other)) continue;
            offset.scale((friendly ? 1f : -1f) * Misc.getShipWeight(other) / distance);
            Vector2f.add(bias, offset, bias);
        }
        Vector2f heading = dragDirection(ship.getLocation(), tether.getTargetPoint(), bias);
        steer((float) Math.toDegrees(Math.atan2(heading.y, heading.x)));
    }

    // The unit heading for the tug: away from the catch, bent toward the bias (friendly weight minus enemy weight,
    // by direction). The away term outweighs a unit bias, so the heading never closes on the catch.
    public static Vector2f dragDirection(Vector2f tug, Vector2f catchPoint, Vector2f bias) {
        Vector2f away = Vector2f.sub(tug, catchPoint, null);
        if (away.lengthSquared() < 1f) away.set(1f, 0f);
        away.normalise();
        away.scale(AWAY_WEIGHT);
        if (bias.lengthSquared() > 1e-6f) {
            Vector2f unitBias = new Vector2f(bias);
            unitBias.normalise();
            Vector2f.add(away, unitBias, away);
        }
        return (Vector2f) away.normalise();
    }

    private void steer(float heading) {
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
        if (Math.abs(difference) < THRUST_ARC) {
            ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
        } else {
            ship.blockCommandForOneFrame(ShipCommand.ACCELERATE);
        }
        ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
        ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
        ship.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT);
        ship.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT);
    }

    // The plugin only advances while its group is on autofire, so off autofire its answer is stale.
    private boolean wantsToFire(WeaponAPI weapon) {
        WeaponGroupAPI group = ship.getWeaponGroupFor(weapon);
        if (group == null || !group.isAutofiring()) return false;
        AutofireAIPlugin plugin = group.getAutofirePlugin(weapon);
        return plugin instanceof HarpoonAutofireAI && plugin.shouldFire();
    }

    // A friendly ship or a wreck is never worth towing for the AI. Neither is a catch it cannot move while the tug is
    // in trouble or out of drive charges.
    private boolean isWorthHolding(Tether tether) {
        ShipAPI target = tether.getTarget();
        if (target.isHulk() || target.getOwner() == ship.getOwner()) return false;
        if (ship.getHullLevel() < DANGER_HULL || ship.getFluxTracker().getFluxLevel() > DANGER_FLUX) return false;
        boolean hopeless = target.getMass() > ship.getMass() * HOPELESS_MASS_RATIO
                && (ship.getSystem() == null || ship.getSystem().isOutOfAmmo());
        return !(hopeless && tether.getAge() > HOPELESS_TIME);
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
