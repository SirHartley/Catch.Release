package catchrelease.combat.tug;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

// Owns every harpoon line in one battle. Added to the engine by the first harpoon shot, so battles without a
// Stigmata never run it; the engine and its custom data are new for each battle.
public class TugTethers extends BaseEveryFrameCombatPlugin {

    private static final String KEY = "catchrelease_tugTethers";

    private final CombatEngineAPI engine;
    private final List<Tether> tethers = new ArrayList<>();

    private TugTethers(CombatEngineAPI engine) {
        this.engine = engine;
    }

    public static TugTethers get(CombatEngineAPI engine) {
        Object existing = engine.getCustomData().get(KEY);
        if (existing instanceof TugTethers tethers) return tethers;
        TugTethers tethers = new TugTethers(engine);
        engine.getCustomData().put(KEY, tethers);
        engine.addPlugin(tethers);
        return tethers;
    }

    // Firing again releases the weapon's current line, whether it is still in flight or attached.
    void fire(WeaponAPI weapon, DamagingProjectileAPI shot) {
        for (Tether tether : tethers) {
            if (tether.getWeapon() == weapon) tether.release();
        }
        tethers.add(new Tether(engine, weapon, shot));
    }

    void replaceShot(DamagingProjectileAPI shot, DamagingProjectileAPI copy) {
        Tether tether = inFlight(shot);
        if (tether != null) tether.setShot(copy);
    }

    void missed(DamagingProjectileAPI shot) {
        Tether tether = inFlight(shot);
        if (tether != null) tether.release();
    }

    void attach(DamagingProjectileAPI shot, ShipAPI target, Vector2f point) {
        Tether tether = inFlight(shot);
        if (tether != null) tether.attach(target, point);
    }

    public Tether getTether(ShipAPI tug) {
        for (Tether tether : tethers) {
            if (tether.getTug() == tug && tether.isAttached()) return tether;
        }
        return null;
    }

    private Tether inFlight(DamagingProjectileAPI shot) {
        for (Tether tether : tethers) {
            if (tether.getShot() == shot) return tether;
        }
        return null;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) return;
        for (int i = tethers.size() - 1; i >= 0; i--) {
            Tether tether = tethers.get(i);
            tether.advance(amount);
            if (tether.isDone()) tethers.remove(i);
        }
    }
}
