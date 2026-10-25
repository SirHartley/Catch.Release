package catchrelease.combat.salvage;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

// Flight and bay positions shared by the LYNE drones and returning recovered fighters.
final class SalvageFlight {

    public static final String LANDING_BAY = "LB 1";
    public static final float APPROACH_GAIN = 2.5f;
    // Velocity error, in units per second, that thrust leaves alone, so an arrived ship does not twitch.
    public static final float SPEED_DEADBAND = 10f;

    private SalvageFlight() {
    }

    // Steers toward a point moving with the given velocity, slowing on arrival. The engine's
    // headInDirectionWithoutTurning only honours its speed for ships flagged as drones, which only drone launchers and
    // DEM set (OO0O.java, 0.98a-RC8), so a bay fighter would arrive at full speed and swing around the point; the
    // velocity is steered here instead. The wanted velocity is the point's plus an approach speed the ship can still
    // stop from in the distance left; thrust and strafe close the difference.
    static void moveTo(ShipAPI ship, Vector2f point, Vector2f pointVelocity, float speedMult) {
        Vector2f offset = Vector2f.sub(point, ship.getLocation(), null);
        float distance = offset.length();
        float stopping = (float) Math.sqrt(2f * Math.max(1f, ship.getDeceleration()) * distance);
        float approach = Math.min(ship.getMaxSpeed() * speedMult, Math.min(distance * APPROACH_GAIN, stopping));
        Vector2f wanted = new Vector2f(pointVelocity);
        if (distance > 0f) wanted.translate(offset.x / distance * approach, offset.y / distance * approach);
        if (wanted.length() > SPEED_DEADBAND) Misc.turnTowardsFacingV2(ship, Misc.getAngleInDegrees(wanted), 0f);

        Vector2f error = Vector2f.sub(wanted, ship.getVelocity(), null);
        Vector2f forward = Misc.getUnitVectorAtDegreeAngle(ship.getFacing());
        float along = Vector2f.dot(error, forward);
        float across = forward.x * error.y - forward.y * error.x;
        if (along > SPEED_DEADBAND) ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
        else if (along < -SPEED_DEADBAND) ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, 0);
        if (across > SPEED_DEADBAND) ship.giveCommand(ShipCommand.STRAFE_LEFT, null, 0);
        else if (across < -SPEED_DEADBAND) ship.giveCommand(ShipCommand.STRAFE_RIGHT, null, 0);
    }

    // One of a bay slot's launch points, picked by index, in world coordinates; the ship's centre without the slot.
    static Vector2f bayPoint(ShipAPI tender, String slotId, int index) {
        WeaponSlotAPI slot = tender.getHullSpec().getWeaponSlotAPI(slotId);
        List<Vector2f> points = slot == null ? null : slot.getLaunchPointOffsets();
        if (points == null || points.isEmpty()) return new Vector2f(tender.getLocation());
        Vector2f offset = Misc.rotateAroundOrigin(points.get(Math.floorMod(index, points.size())),
                tender.getFacing());
        return Vector2f.add(tender.getLocation(), offset, null);
    }
}
