package catchrelease.combat.tug;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lwjgl.util.vector.Vector2f;

// One instance per projectile, so it holds no state; tethers live in TugTethers.
public class HarpoonOnHit implements OnHitEffectPlugin {

    public static final String WEAPON_ID = "catchrelease_harpoon";
    public static final float DEFLECT_SPEED_MULT = 0.6f;
    public static final int MAX_DEFLECTIONS = 2;

    private static final String DEFLECTIONS_KEY = "catchrelease_harpoonDeflections";
    // Spawning on the shield's edge would let the copy hit the same shield again at once.
    private static final float DEFLECT_CLEARANCE = 12f;

    // shieldHit is only true when the hit dealt shield damage, which the harpoon always does.
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit,
                      ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (!(target instanceof ShipAPI ship)) return;
        TugTethers tethers = TugTethers.get(engine);
        if (shieldHit) {
            DamagingProjectileAPI copy = deflect(projectile, ship.getShield(), point, engine);
            if (copy != null) tethers.replaceShot(projectile, copy);
            else tethers.missed(projectile);
        } else {
            tethers.attach(projectile, ship, point);
        }
    }

    private static DamagingProjectileAPI deflect(DamagingProjectileAPI projectile, ShieldAPI shield, Vector2f point,
                                                 CombatEngineAPI engine) {
        Object count = projectile.getCustomData().get(DEFLECTIONS_KEY);
        int deflections = count instanceof Integer n ? n : 0;
        if (shield == null || deflections >= MAX_DEFLECTIONS) return null;

        Vector2f normal = Vector2f.sub(point, shield.getLocation(), null);
        Vector2f velocity = projectile.getVelocity();
        float speed = velocity.length();
        if (normal.lengthSquared() < 1f || speed < 1f) return null;
        normal.normalise();
        float into = Vector2f.dot(velocity, normal);
        Vector2f reflected = into < 0f
                ? new Vector2f(velocity.x - 2f * into * normal.x, velocity.y - 2f * into * normal.y)
                : new Vector2f(velocity);
        reflected.scale(DEFLECT_SPEED_MULT * speed / reflected.length());

        float clearance = shield.getRadius() + DEFLECT_CLEARANCE;
        Vector2f spawn = new Vector2f(shield.getLocation().x + normal.x * clearance,
                shield.getLocation().y + normal.y * clearance);
        float angle = (float) Math.toDegrees(Math.atan2(reflected.y, reflected.x));
        CombatEntityAPI spawned = engine.spawnProjectile(projectile.getSource(), projectile.getWeapon(), WEAPON_ID,
                spawn, angle, null);
        if (!(spawned instanceof DamagingProjectileAPI copy)) return null;
        copy.getVelocity().set(reflected);
        copy.setFacing(angle);
        copy.setCustomData(DEFLECTIONS_KEY, deflections + 1);
        return copy;
    }
}
