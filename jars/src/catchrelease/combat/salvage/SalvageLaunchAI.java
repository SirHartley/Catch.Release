package catchrelease.combat.salvage;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.lwjgl.util.vector.Vector2f;

// Salvage Launch's system AI, run by the default ship AI and rebuilt with it, so it keeps only a timer. Launches when
// a target is in reach and enough fighters are ready: two, or all there are once nothing else is restoring.
public class SalvageLaunchAI implements ShipSystemAIScript {

    public static final float INTERVAL = 0.5f;
    public static final float TARGET_RANGE = 3000f;
    public static final int MIN_READY = 2;

    private ShipAPI ship;
    private ShipSystemAPI system;
    private ShipwideAIFlags flags;
    private CombatEngineAPI engine;
    private float timer;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.flags = flags;
        this.engine = engine;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        timer -= amount;
        if (timer > 0f) return;
        timer = INTERVAL;
        if (system.isActive() || system.isOutOfAmmo() || system.getCooldownRemaining() > 0f
                || ship.getFluxTracker().isOverloadedOrVenting() || ship.getCurrentCR() <= 0f) {
            return;
        }
        FighterStore store = FighterStore.forBattle(engine, ship.getFleetMemberId());
        int ready = store.countReady();
        int restoring = store.getFighters().size() - ready;
        if (shouldLaunch(ready, restoring, isInReach(pickTarget(target)))) {
            ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0);
        }
    }

    private ShipAPI pickTarget(ShipAPI target) {
        if (target != null) return target;
        Object carrierTarget = flags.getCustom(AIFlags.CARRIER_FIGHTER_TARGET);
        return carrierTarget instanceof ShipAPI ship ? ship : null;
    }

    private boolean isInReach(ShipAPI target) {
        return target != null && target.isAlive() && target.getOwner() != ship.getOwner()
                && Vector2f.sub(target.getLocation(), ship.getLocation(), null).length() <= TARGET_RANGE;
    }

    public static boolean shouldLaunch(int ready, int restoring, boolean targetInReach) {
        if (!targetInReach || ready <= 0) return false;
        return ready >= MIN_READY || restoring == 0;
    }
}
