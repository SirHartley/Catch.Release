package catchrelease.combat.salvage;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;

import java.util.LinkedHashMap;
import java.util.Map;

// Built into the Reliquary. Starts the battle's WreckTracker when one is deployed, and keeps the fighters it
// launches from leaving wrecks, so nothing is salvaged twice.
public class SalvageBaysHullmod extends BaseHullMod {

    public static final String ID = "catchrelease_salvage_bays";

    private static final String STATUS_KEY = "catchrelease_salvageStore_";

    // The refit's swapped spec (RefitStoreView) lists more built-in wings than the hull has bays. The refit trims a
    // variant's wings to the bay count of stats built without an entity, so the extra bays exist only there; a combat
    // ship has its entity set before hullmods run, and bays are made from that count, so stored wings never get one.
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (stats.getEntity() != null || stats.getVariant() == null) return;
        ShipHullSpecAPI spec = stats.getVariant().getHullSpec();
        int extra = spec.getBuiltInWings().size() - spec.getFighterBays();
        if (extra > 0) stats.getNumFighterBays().modifyFlat(id, extra);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || Global.getCurrentState() != GameState.COMBAT) return;
        WreckTracker.get(engine).addTender(ship);
        if (!ship.hasListenerOfClass(TenderBays.class)) ship.addListener(new TenderBays(ship));
    }

    // One status line per stored fighter type above the player ship's info, where terrain effects show. The list
    // fades a line that is not maintained every unpaused frame, and advanceInCombat is not called while paused.
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (Global.getCurrentState() != GameState.COMBAT || engine == null || engine.getPlayerShip() != ship) return;
        FighterStore store = FighterStore.forBattle(engine, ship.getFleetMemberId());
        Map<String, int[]> types = new LinkedHashMap<>();
        for (FighterStore.Fighter fighter : store.getFighters()) {
            int[] type = types.computeIfAbsent(fighter.wingId, wingId -> new int[3]);
            if (fighter.isReady()) {
                type[0]++;
            } else {
                type[1]++;
                type[2] = Math.max(type[2], Math.round(fighter.getProgress() * 100f));
            }
        }
        String icon = Global.getSettings().getSpriteName("ui", "icon_tactical_bdeck");
        for (Map.Entry<String, int[]> entry : types.entrySet()) {
            int[] type = entry.getValue();
            engine.maintainStatusForPlayerShip(STATUS_KEY + entry.getKey(), icon, getFighterName(entry.getKey()),
                    getStatus(type[0], type[1], type[2]), type[0] == 0);
        }
        if (store.count() >= FighterStore.getCap()) {
            engine.maintainStatusForPlayerShip(STATUS_KEY + "full", icon, "Salvage Bays", "BAYS FULL", true);
        }
    }

    static String getFighterName(String wingId) {
        FighterWingSpecAPI spec = FighterStore.getWingSpec(wingId);
        return spec == null ? wingId : spec.getVariant().getHullSpec().getHullName();
    }

    static String getStatus(int ready, int restoring, int percent) {
        if (restoring == 0) return ready + " READY";
        if (ready == 0) return restoring == 1 ? "RESTORING " + percent + "%" : restoring + " RESTORING";
        return ready + " READY, " + restoring + " RESTORING";
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
