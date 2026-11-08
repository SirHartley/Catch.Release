package catchrelease.combat.lights;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lwjgl.util.vector.Vector2f;

public class BreachMoteOnHit implements OnHitEffectPlugin {

    public static final float FIGHTER_DAMAGE = 10000f;

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit,
                      ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (!(target instanceof ShipAPI ship) || ship.isHulk()) return;

        if (ship.isFighter()) {
            engine.applyDamage(projectile, ship, point, FIGHTER_DAMAGE, DamageType.ENERGY, 0f,
                    true, false, projectile.getSource(), true);
            return;
        }
        BreachDefence.drop(ship);
    }
}
