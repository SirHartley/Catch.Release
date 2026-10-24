package catchrelease.combat.tug;

import catchrelease.config.Setting;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

// The Stigmata's Haul Drive. The engine creates one stats script per ship system, so the fields belong to one ship.
public class TugDriveStats extends BaseShipSystemScript {

    public static final float MASS_BONUS = 1.5f;
    public static final float ACCELERATION_BONUS = 150f;
    public static final float DECELERATION_BONUS = 100f;
    public static final float TURN_ACCELERATION_BONUS = 150f;
    public static final float TURN_RATE_BONUS = 50f;

    private static final Color FLAME = new Color(255, 140, 70, 255);
    private static final Color CONTRAIL = new Color(70, 55, 45, 90);
    private static final Color SMOKE = new Color(30, 26, 24, 150);
    private static final float SMOKE_INTERVAL = 0.05f;

    // Mass is a plain field, not a stat, so the script keeps the ship's own value and puts it back.
    private float baseMass = -1f;
    private float smokeTimer;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI ship)) return;
        if (baseMass < 0f) baseMass = ship.getMass();
        ship.setMass(baseMass * (1f + MASS_BONUS * effectLevel));

        stats.getAcceleration().modifyPercent(id, ACCELERATION_BONUS * effectLevel);
        stats.getDeceleration().modifyPercent(id, DECELERATION_BONUS * effectLevel);
        stats.getTurnAcceleration().modifyPercent(id, TURN_ACCELERATION_BONUS * effectLevel);
        stats.getMaxTurnRate().modifyPercent(id, TURN_RATE_BONUS * effectLevel);

        ShipEngineControllerAPI engines = ship.getEngineController();
        engines.extendFlame(id, 0.8f * effectLevel, 0.3f * effectLevel, 0.5f * effectLevel);
        engines.fadeToOtherColor(id, FLAME, CONTRAIL, effectLevel, 0.6f);
        emitSmoke(ship, effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        if (stats.getEntity() instanceof ShipAPI ship && baseMass >= 0f) ship.setMass(baseMass);
        baseMass = -1f;
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
    }

    // Dark smoke blown out along each working nozzle's flame.
    private void emitSmoke(ShipAPI ship, float effectLevel) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused() || effectLevel <= 0f) return;
        smokeTimer += engine.getElapsedInLastFrame();
        float interval = Setting.REDUCED_EFFECTS.enabled() ? SMOKE_INTERVAL * 3f : SMOKE_INTERVAL;
        if (smokeTimer < interval) return;
        smokeTimer = 0f;
        for (ShipEngineAPI nozzle : ship.getEngineController().getShipEngines()) {
            if (nozzle.isDisabled()) continue;
            float angle = ship.getFacing() + nozzle.getEngineSlot().getAngle()
                    + (float) (Math.random() * 20f - 10f);
            float speed = 80f + (float) Math.random() * 60f;
            Vector2f velocity = new Vector2f(
                    ship.getVelocity().x * 0.5f + (float) Math.cos(Math.toRadians(angle)) * speed,
                    ship.getVelocity().y * 0.5f + (float) Math.sin(Math.toRadians(angle)) * speed);
            float size = (20f + (float) Math.random() * 20f) * (0.5f + 0.5f * effectLevel);
            engine.addNebulaSmokeParticle(new Vector2f(nozzle.getLocation()), velocity, size, 2.5f, 0.1f, 0.3f,
                    1.5f + (float) Math.random(), SMOKE);
        }
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        return switch (index) {
            case 0 -> new StatusData("mass and thrust increased", false);
            case 1 -> new StatusData("improved maneuverability", false);
            default -> null;
        };
    }
}
