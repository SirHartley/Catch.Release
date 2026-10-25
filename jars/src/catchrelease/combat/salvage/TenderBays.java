package catchrelease.combat.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;

import java.util.function.ToDoubleFunction;

// Restores a Reliquary's recovered fighters during combat, as a bay replaces a fighter: the wing's refit time times
// the ship's fighter refit modifier, at its shared replacement rate, paused while it is overloaded or venting or has
// no CR. Added by SalvageBaysHullmod; an AdvanceableListener only runs while the battle is unpaused.
public class TenderBays implements AdvanceableListener {

    private final ShipAPI tender;
    private final ToDoubleFunction<String> refitTime = this::getRefitTime;
    private FighterStore store;
    private float refitMult;

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
        if (store == null) store = FighterStore.forBattle(engine, tender.getFleetMemberId());
        refitMult = tender.getMutableStats().getFighterRefitTimeMult().getModifiedValue();
        store.advance(amount * tender.getSharedFighterReplacementRate(), refitTime);
    }

    private double getRefitTime(String wingId) {
        FighterWingSpecAPI spec = FighterStore.getWingSpec(wingId);
        return spec == null ? 1f : spec.getRefitTime() * refitMult;
    }
}
