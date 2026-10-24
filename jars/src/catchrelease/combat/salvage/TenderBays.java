package catchrelease.combat.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;

// Restores a Reliquary's recovered fighters during combat, as a bay replaces a fighter: the wing's refit time times
// the ship's fighter refit modifier, at its shared replacement rate, paused while it is overloaded or venting or has
// no CR. Added by SalvageBaysHullmod; an AdvanceableListener only runs while the battle is unpaused.
public class TenderBays implements AdvanceableListener {

    private final ShipAPI tender;

    public TenderBays(ShipAPI tender) {
        this.tender = tender;
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || !tender.isAlive() || tender.getFluxTracker().isOverloadedOrVenting()
                || tender.getCurrentCR() <= 0f) {
            return;
        }
        float refitMult = tender.getMutableStats().getFighterRefitTimeMult().getModifiedValue();
        FighterStore store = FighterStore.forBattle(engine, tender.getFleetMemberId());
        store.advance(amount * tender.getSharedFighterReplacementRate(), wingId -> {
            FighterWingSpecAPI spec = FighterStore.getWingSpec(wingId);
            return spec == null ? 1f : spec.getRefitTime() * refitMult;
        });
    }
}
