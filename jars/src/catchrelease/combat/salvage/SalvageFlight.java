package catchrelease.combat.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

// Flight and bay positions shared by the LYNE drones and returning recovered fighters.
final class SalvageFlight {

    public static final String LANDING_BAY = "LB 1";
    public static final float APPROACH_GAIN = 2.5f;

    private SalvageFlight() {
    }

    // Steers toward a point moving with the given velocity, slowing on arrival.
    static void moveTo(ShipAPI ship, Vector2f point, Vector2f pointVelocity, float speedMult) {
        Vector2f offset = Vector2f.sub(point, ship.getLocation(), null);
        float maxSpeed = ship.getMaxSpeed() * speedMult;
        float approach = Math.min(maxSpeed, offset.length() * APPROACH_GAIN);
        Vector2f wanted = new Vector2f(pointVelocity);
        if (offset.lengthSquared() > 0f) {
            offset.normalise();
            wanted.translate(offset.x * approach, offset.y * approach);
        }
        float speed = Math.min(maxSpeed, wanted.length());
        float heading = Misc.getAngleInDegrees(wanted);
        Global.getCombatEngine().headInDirectionWithoutTurning(ship, heading, speed);
        Misc.turnTowardsFacingV2(ship, heading, 0f);
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
