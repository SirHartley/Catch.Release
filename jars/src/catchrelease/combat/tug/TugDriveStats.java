package catchrelease.combat.tug;

import catchrelease.config.Setting;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.EngineSlotAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicTrailPlugin;

import java.awt.Color;
import java.util.List;

// The Stigmata's Haul Drive. The engine creates one stats script per ship system, so the fields belong to one ship.
public class TugDriveStats extends BaseShipSystemScript {

    public static final float MASS_BONUS = 1.5f;
    public static final float ACCELERATION_BONUS = 150f;
    public static final float DECELERATION_BONUS = 100f;
    public static final float TURN_ACCELERATION_BONUS = 150f;
    public static final float TURN_RATE_BONUS = 50f;

    private static final Color FLAME = new Color(255, 140, 70, 255);
    // The LOW_TECH engines' own smoke contrail, which only shows while thrusting, turns sooty.
    private static final Color SOOT_CONTRAIL = new Color(40, 34, 30, 100);
    private static final Color SOOT = new Color(38, 32, 28);
    private static final Color HAZE = new Color(75, 70, 66);
    private static final float SOOT_OPACITY = 0.55f;
    private static final float SOOT_INTERVAL = 0.05f;
    // MagicLib's lumpy smoke trail texture; it runs along the ribbon every TEXTURE_LOOP units.
    private static final String SOOT_TEXTURE = "base_trail_heavySmoke";
    private static final float TEXTURE_LOOP = 256f;
    // Thousandths of the texture per second; positive moves the lumps downstream.
    private static final float TEXTURE_SCROLL = 60f;

    // Mass is a plain field, not a stat, so the script keeps the ship's own value and puts it back.
    private float baseMass = -1f;

    private SpriteAPI sootTexture;
    private float[] trailIds;
    private float sootTimer;

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
        engines.fadeToOtherColor(id, FLAME, null, effectLevel, 0.6f);
        engines.fadeToOtherColor(id + "_contrail", null, SOOT_CONTRAIL, effectLevel, 0.9f);
        emitSoot(ship, effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        if (stats.getEntity() instanceof ShipAPI ship && baseMass >= 0f) ship.setMass(baseMass);
        baseMass = -1f;
        trailIds = null;
        sootTimer = 0f;
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
    }

    // One soot ribbon per working nozzle, a MagicLib trail drawn below the ships as vanilla draws its smoke contrails,
    // so the hull and the lengthened flame cover its root. Particles cannot do this: the engine draws every API
    // particle above all ships. Pieces added under the id of a trail that is still fading join it, so each activation
    // and each restarted nozzle takes a new id. The pieces slow and widen as they age, so the plume billows out
    // behind the tug; a quarter of the ship's velocity carries over, so a dragged tug does not tow its own smoke.
    private void emitSoot(ShipAPI ship, float effectLevel) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused() || effectLevel <= 0f) return;
        sootTimer += engine.getElapsedInLastFrame();
        float interval = Setting.REDUCED_EFFECTS.enabled() ? SOOT_INTERVAL * 2f : SOOT_INTERVAL;
        if (sootTimer < interval) return;
        sootTimer = 0f;

        List<ShipEngineAPI> nozzles = ship.getEngineController().getShipEngines();
        if (trailIds == null || trailIds.length != nozzles.size()) {
            trailIds = new float[nozzles.size()];
            for (int i = 0; i < trailIds.length; i++) trailIds[i] = MagicTrailPlugin.getUniqueID();
        }
        if (sootTexture == null) sootTexture = Global.getSettings().getSprite("fx", SOOT_TEXTURE);
        for (int i = 0; i < nozzles.size(); i++) {
            ShipEngineAPI nozzle = nozzles.get(i);
            if (nozzle.isDisabled()) {
                trailIds[i] = MagicTrailPlugin.getUniqueID();
                continue;
            }
            EngineSlotAPI slot = nozzle.getEngineSlot();
            float angle = ship.getFacing() + slot.getAngle();
            Vector2f start = Misc.getUnitVectorAtDegreeAngle(angle);
            start.scale(slot.getLength() * 0.35f);
            Vector2f.add(start, nozzle.getLocation(), start);
            float width = slot.getWidth();
            MagicTrailPlugin.addTrailMemberAdvanced(ship, trailIds[i], sootTexture, start,
                    70f + 30f * (float) Math.random(), 15f, angle + 8f * (float) Math.random() - 4f,
                    20f * (float) Math.random() - 10f, 0f,
                    width * 1.2f * (0.4f + 0.6f * effectLevel), width * 5f, SOOT, HAZE,
                    SOOT_OPACITY * effectLevel, 0.15f, 0.5f, 1.8f, false,
                    TEXTURE_LOOP, TEXTURE_SCROLL, -1f,
                    new Vector2f(ship.getVelocity().x * 0.25f, ship.getVelocity().y * 0.25f), null,
                    CombatEngineLayers.BELOW_SHIPS_LAYER, 1f);
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
