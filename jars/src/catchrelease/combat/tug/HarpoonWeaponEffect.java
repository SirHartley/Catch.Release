package catchrelease.combat.tug;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;

// Registered as the weapon's everyFrameEffect: the engine also calls onFire on that instance, so the .proj needs no
// onFireEffect. One instance per weapon. Its advance runs before the weapon turns, so setCurrAngle here replaces
// vanilla's turning for that frame.
public class HarpoonWeaponEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    // After a shot the mount eases back to its slot angle: a share of the remaining angle per second, at least
    // MIN_RETURN_SPEED so it finishes, at most MAX_RETURN_SPEED. Vanilla would hold the angle for 15 s, then turn
    // back at a constant rate.
    public static final float RETURN_DELAY = 0.4f;
    public static final float RETURN_RATE = 8f;
    public static final float MIN_RETURN_SPEED = 30f;
    public static final float MAX_RETURN_SPEED = 360f;
    public static final float SETTLED = 0.5f;

    private float returnDelay = -1f;

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        TugTethers.get(engine).fire(weapon, projectile);
        returnDelay = RETURN_DELAY;
    }

    // A line ending any other way also sends the mount back.
    void onReleased() {
        if (returnDelay < 0f) returnDelay = 0f;
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (returnDelay < 0f || engine.isPaused()) return;
        ShipAPI ship = weapon.getShip();
        if (ship == null || isAimedByPlayer(engine, ship, weapon) || hasAutofireTarget(ship, weapon)) {
            returnDelay = -1f;
            return;
        }
        returnDelay -= amount;
        if (returnDelay > 0f) return;

        float slotAngle = ship.getFacing() + weapon.getArcFacing();
        float remaining = angleDifference(weapon.getCurrAngle(), slotAngle);
        if (Math.abs(remaining) <= SETTLED) {
            weapon.setCurrAngle(slotAngle);
            returnDelay = -1f;
            return;
        }
        weapon.setCurrAngle(weapon.getCurrAngle() + returnStep(remaining, amount));
    }

    public static float returnStep(float remaining, float amount) {
        float speed = Math.abs(remaining) * RETURN_RATE;
        speed = Math.max(MIN_RETURN_SPEED, Math.min(MAX_RETURN_SPEED, speed));
        float step = Math.min(Math.abs(remaining), speed * amount);
        return Math.signum(remaining) * step;
    }

    // The player's own ship with this weapon's group selected and not on autofire: the mouse aims it.
    private static boolean isAimedByPlayer(CombatEngineAPI engine, ShipAPI ship, WeaponAPI weapon) {
        if (ship != engine.getPlayerShip() || engine.isUIAutopilotOn()) return false;
        WeaponGroupAPI group = ship.getWeaponGroupFor(weapon);
        return group != null && group == ship.getSelectedGroupAPI() && !group.isAutofiring();
    }

    // The autofire plugin aims the turret through the tracker, which this return would override.
    private static boolean hasAutofireTarget(ShipAPI ship, WeaponAPI weapon) {
        WeaponGroupAPI group = ship.getWeaponGroupFor(weapon);
        return group != null && group.isAutofiring()
                && group.getAutofirePlugin(weapon) instanceof HarpoonAutofireAI ai && ai.getTargetShip() != null;
    }

    private static float angleDifference(float from, float to) {
        float difference = (to - from) % 360f;
        if (difference > 180f) difference -= 360f;
        if (difference < -180f) difference += 360f;
        return difference;
    }
}
