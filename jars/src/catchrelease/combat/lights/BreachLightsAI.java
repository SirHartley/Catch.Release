package catchrelease.combat.lights;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

// rebuilt with the ship ai, so it keeps only timers
public class BreachLightsAI implements ShipSystemAIScript {

    public static final float INTERVAL = 0.5f;
    public static final float ON_RANGE = BreachLightEffect.REACH;
    public static final float OFF_RANGE = BreachLightEffect.REACH + 600f;
    public static final float OFF_DELAY = 3f;
    public static final float ON_FLUX = 0.6f;
    public static final float OFF_FLUX = 0.85f;

    private ShipAPI ship;
    private ShipSystemAPI system;
    private CombatEngineAPI engine;
    private float timer;
    private float idle;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.engine = engine;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        timer -= amount;
        if (timer > 0f) return;
        float elapsed = INTERVAL - timer;
        timer = INTERVAL;

        float flux = ship.getFluxTracker().getFluxLevel();
        if (system.isOn()) {
            idle = hasEnemyWithin(OFF_RANGE) ? 0f : idle + elapsed;
            if (flux >= OFF_FLUX || idle >= OFF_DELAY) ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0);
            return;
        }

        idle = 0f;
        if (system.isActive() || system.getCooldownRemaining() > 0f || flux >= ON_FLUX
                || ship.getFluxTracker().isOverloadedOrVenting() || ship.getCurrentCR() <= 0f) {
            return;
        }
        if (hasEnemyWithin(ON_RANGE)) ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0);
    }

    private boolean hasEnemyWithin(float range) {
        for (ShipAPI other : engine.getShips()) {
            if (!other.isAlive() || other.isHulk() || other.getOwner() == ship.getOwner() || other.getOwner() == 100) {
                continue;
            }
            if (Misc.getDistance(ship.getLocation(), other.getLocation()) - other.getCollisionRadius() <= range) {
                return true;
            }
        }
        return false;
    }
}
