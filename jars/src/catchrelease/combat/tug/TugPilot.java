package catchrelease.combat.tug;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;

// AI-side control of an AI-flown Stigmata, added by its Tug Drive Field hullmod. An AdvanceableListener runs at the
// start of Ship.advance, after the ship AI and before commands and weapons are processed, so what it gives, blocks or
// forces acts in the same frame. Wrapping the default ship AI instead is not possible: BasicShipAI.advance throws
// when the ship's AI is not itself. Does nothing while the player flies the ship without autopilot.
public class TugPilot implements AdvanceableListener {

    public static final float DANGER_HULL = 0.35f;
    public static final float DANGER_FLUX = 0.85f;
    public static final float HOPELESS_MASS_RATIO = 3f;
    public static final float HOPELESS_TIME = 4f;

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
        if (tether != null && !isWorthHolding(tether)) tethers.release(tether);
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
