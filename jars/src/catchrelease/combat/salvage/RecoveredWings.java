package catchrelease.combat.salvage;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

// Recovered fighters out on a launch, one per battle in the engine's custom data. They have no source bay, because a
// bay builds whatever wing asks it and would rebuild them endlessly, so vanilla never brings them home. When one
// needs a refit, or its Reliquary pulls fighters back, it gets ReturnAI, lands at LB 1 and goes back in the store.
// One that dies is gone.
public class RecoveredWings extends BaseEveryFrameCombatPlugin {

    private static final String KEY = "catchrelease_recoveredWings";

    private final CombatEngineAPI engine;
    private final List<Recovered> out = new ArrayList<>();

    private static final class Recovered {

        private final ShipAPI fighter;
        private final ShipAPI tender;
        private final String wingId;
        private final FighterStore store;
        private boolean returning;
        private boolean landed;

        private Recovered(ShipAPI fighter, ShipAPI tender, String wingId, FighterStore store) {
            this.fighter = fighter;
            this.tender = tender;
            this.wingId = wingId;
            this.store = store;
        }
    }

    private RecoveredWings(CombatEngineAPI engine) {
        this.engine = engine;
    }

    public static RecoveredWings get(CombatEngineAPI engine) {
        if (engine.getCustomData().get(KEY) instanceof RecoveredWings wings) return wings;
        RecoveredWings wings = new RecoveredWings(engine);
        engine.getCustomData().put(KEY, wings);
        engine.addPlugin(wings);
        return wings;
    }

    void add(ShipAPI fighter, ShipAPI tender, String wingId, FighterStore store) {
        out.add(new Recovered(fighter, tender, wingId, store));
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) return;
        out.removeIf(recovered -> {
            if (recovered.landed) return true;
            ShipAPI fighter = recovered.fighter;
            if (!engine.isEntityInPlay(fighter) || fighter.isHulk() || !fighter.isAlive()) {
                recovered.store.destroyed(recovered.wingId);
                return true;
            }
            if (!recovered.returning && shouldReturn(recovered)) {
                recovered.returning = true;
                fighter.setShipAI(new ReturnAI(recovered));
            }
            return false;
        });
    }

    private boolean shouldReturn(Recovered recovered) {
        ShipAPI tender = recovered.tender;
        if (!tender.isAlive() || !engine.isEntityInPlay(tender)) return false;
        if (tender.isPullBackFighters()) return true;
        ShipAIPlugin ai = recovered.fighter.getShipAI();
        return ai != null && ai.needsRefit();
    }

    // Flies the fighter to one of LB 1's points and lands it there, as a LYNE drone lands.
    private final class ReturnAI implements ShipAIPlugin {

        private static final float DOCK_DISTANCE = 40f;

        private final Recovered recovered;
        private final ShipwideAIFlags flags = new ShipwideAIFlags();
        private final int point;

        private ReturnAI(Recovered recovered) {
            this.recovered = recovered;
            this.point = System.identityHashCode(recovered.fighter);
        }

        @Override
        public void advance(float amount) {
            ShipAPI fighter = recovered.fighter;
            ShipAPI tender = recovered.tender;
            if (!tender.isAlive() || !engine.isEntityInPlay(tender)) return;
            Vector2f dock = SalvageFlight.bayPoint(tender, SalvageFlight.LANDING_BAY, point);
            if (!fighter.isLanding()) {
                SalvageFlight.moveTo(fighter, dock, tender.getVelocity(), 1f);
                if (Vector2f.sub(dock, fighter.getLocation(), null).length() <= DOCK_DISTANCE) {
                    fighter.beginLandingAnimation(tender);
                }
                return;
            }
            fighter.getLocation().set(dock);
            fighter.getVelocity().set(tender.getVelocity());
            if (!fighter.isFinishedLanding()) return;
            recovered.landed = true;
            recovered.store.returned(recovered.wingId);
            engine.removeEntity(fighter);
        }

        @Override
        public void setDoNotFireDelay(float amount) {
        }

        @Override
        public void forceCircumstanceEvaluation() {
        }

        @Override
        public boolean needsRefit() {
            return false;
        }

        @Override
        public ShipwideAIFlags getAIFlags() {
            return flags;
        }

        @Override
        public void cancelCurrentManeuver() {
        }

        @Override
        public ShipAIConfig getConfig() {
            return null;
        }
    }
}
