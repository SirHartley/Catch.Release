package catchrelease.combat.lights;

import catchrelease.config.FishingSounds;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import java.util.ArrayList;
import java.util.List;

// one stats script per ship system, so the fields belong to one ship
public class BreachLightsStats extends BaseShipSystemScript {

    public static final String SYSTEM_ID = "catchrelease_breach_lights";
    public static final int MOTE_CAP = 8;

    private final List<MissileAPI> motes = new ArrayList<>();
    private List<BreachLightEffect> lamps;
    private boolean on;

    public static float levelOf(ShipAPI ship) {
        ShipSystemAPI system = systemOf(ship);
        return system == null ? 0f : system.getEffectLevel();
    }

    // the lamps light once the charge-up ends and fade with the system on the way out
    public static float lightOf(ShipAPI ship) {
        ShipSystemAPI system = systemOf(ship);
        if (system == null || system.getState() == ShipSystemAPI.SystemState.IN) return 0f;
        return system.getEffectLevel();
    }

    private static ShipSystemAPI systemOf(ShipAPI ship) {
        if (!ship.isAlive() || ship.isHulk()) return null;
        ShipSystemAPI system = ship.getSystem();
        return system != null && SYSTEM_ID.equals(system.getId()) ? system : null;
    }

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI ship)) return;
        CombatEngineAPI engine = Global.getCombatEngine();

        if (!on) {
            on = true;
            if (ship == engine.getPlayerShip()) FishingSounds.playUISound("catchrelease_ui_searchlight_on", 1f, 1f);
        }
        BreachShipGlow.apply(ship, state, effectLevel);

        if (state != State.ACTIVE || engine.isPaused()) return;
        spawn(engine, ship, engine.getElapsedInLastFrame());
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        if (!on) return;
        on = false;
        if (stats.getEntity() instanceof ShipAPI ship && ship == Global.getCombatEngine().getPlayerShip()) {
            FishingSounds.playUISound("catchrelease_ui_searchlight_off", 1f, 1f);
        }
    }

    private void spawn(CombatEngineAPI engine, ShipAPI ship, float amount) {
        if (lamps == null) lamps = BreachLightEffect.lampsOf(ship);
        motes.removeIf(mote -> !engine.isEntityInPlay(mote));

        for (BreachLightEffect lamp : lamps) {
            if (!lamp.tickSpawn(amount) || motes.size() >= MOTE_CAP) continue;
            MissileAPI mote = BreachMoteAI.spawn(engine, ship, lamp.randomPoint());
            if (mote != null) motes.add(mote);
        }
    }
}
