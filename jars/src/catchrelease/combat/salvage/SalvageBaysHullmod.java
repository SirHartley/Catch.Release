package catchrelease.combat.salvage;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

// Built into the Reliquary. Starts the battle's WreckTracker when one is deployed, and keeps the fighters it
// launches from leaving wrecks, so nothing is salvaged twice.
public class SalvageBaysHullmod extends BaseHullMod {

    public static final String ID = "catchrelease_salvage_bays";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || Global.getCurrentState() != GameState.COMBAT) return;
        WreckTracker.get(engine).addTender(ship);
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        fighter.setHulkChanceOverride(0f);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return switch (index) {
            case 0 -> Math.round(WreckTracker.HOLD_TIME) + "";
            default -> null;
        };
    }
}
