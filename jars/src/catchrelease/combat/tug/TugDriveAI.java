package catchrelease.combat.tug;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.lwjgl.util.vector.Vector2f;

// The Haul Drive's system AI, run by the default ship AI each frame (and rebuilt with it, so it keeps only a timer).
// Hauls when a taut line holds a catch that resists, and escapes when the tug is badly hurt and in danger.
public class TugDriveAI implements ShipSystemAIScript {

    public static final float INTERVAL = 0.2f;
    public static final float RESIST_MASS_RATIO = 0.8f;
    public static final float RESIST_SPEED = 20f;
    public static final float TAUT_SHARE = 0.99f;
    public static final float ESCAPE_HULL = 0.4f;

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
        if (!isUsable()) return;

        TugTethers tethers = TugTethers.peek(engine);
        Tether tether = tethers == null ? null : tethers.getTether(ship);
        boolean use;
        if (tether != null) {
            ShipAPI caught = tether.getTarget();
            Vector2f mount = tether.getMountPoint(new Vector2f());
            Vector2f point = tether.getTargetPoint(new Vector2f());
            Vector2f rope = Vector2f.sub(point, mount, null);
            float distance = rope.length();
            // The catch's own speed away along the rope: on a taut rope the two move together, so their relative
            // speed reads near zero while a light catch drags the tug.
            float separating = 0f;
            if (distance > 0f) {
                rope.scale(1f / distance);
                separating = Vector2f.dot(caught.getVelocity(), rope);
            }
            use = isTowing(tether) && shouldHaul(distance >= tether.getLength() * TAUT_SHARE,
                    caught.getMass() / Math.max(1f, ship.getMass()), separating);
        } else {
            use = shouldEscape(ship.getHullLevel(), flags.hasFlag(AIFlags.BACKING_OFF)
                    || flags.hasFlag(AIFlags.NEEDS_HELP) || missileDangerDir != null);
        }
        if (use) ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0);
    }

    // TugPilot lets go of a catch it does not want, a friend hooked by chance among them, in the frame it decides;
    // until it has, the drive waits. The pilot runs after this AI in the frame, so that is at most one interval.
    private boolean isTowing(Tether tether) {
        for (TugPilot pilot : ship.getListeners(TugPilot.class)) {
            if (pilot.getTowing() == tether) return true;
        }
        return false;
    }

    private boolean isUsable() {
        return system != null && !system.isActive() && !system.isOutOfAmmo() && system.getCooldownRemaining() <= 0f
                && !ship.getFluxTracker().isOverloadedOrVenting();
    }

    // A slack line needs no help. A taut one does when the catch is heavy or is pulling away.
    public static boolean shouldHaul(boolean taut, float massRatio, float separatingSpeed) {
        return taut && (massRatio >= RESIST_MASS_RATIO || separatingSpeed > RESIST_SPEED);
    }

    public static boolean shouldEscape(float hullLevel, boolean inDanger) {
        return hullLevel < ESCAPE_HULL && inDanger;
    }
}
