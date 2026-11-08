package catchrelease.combat.lights;

import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;

// one per ship, kept in its custom data, since addListener does not dedupe
public class BreachDefence implements AdvanceableListener {

    public static final float DURATION = 1f;
    private static final String KEY = "catchrelease_breachDefence";

    private final ShipAPI ship;
    private float left;

    private BreachDefence(ShipAPI ship) {
        this.ship = ship;
    }

    public static void drop(ShipAPI ship) {
        BreachDefence drop;
        if (ship.getCustomData().get(KEY) instanceof BreachDefence existing) {
            drop = existing;
        } else {
            drop = new BreachDefence(ship);
            ship.setCustomData(KEY, drop);
            ship.addListener(drop);
        }
        drop.left = DURATION;
        drop.hold();
    }

    @Override
    public void advance(float amount) {
        if (left <= 0f) return;
        left -= amount;
        hold();
    }

    private void hold() {
        ShieldAPI shield = ship.getShield();
        if (shield != null && shield.isOn()) shield.toggleOff();

        ShipSystemAPI cloak = ship.getPhaseCloak();
        if (cloak != null && cloak.isOn()) cloak.deactivate();

        ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
        if (cloak != null && cloak == ship.getSystem()) ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM);
    }
}
