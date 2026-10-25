package catchrelease.combat.tug;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import org.lwjgl.util.vector.Vector2f;

// The harpoon does no damage to friendly ships, so a tug can drag one out of trouble. Added to the tug: a projectile
// hit passes the projectile as param and its source ship as the attacker, whose DamageDealtModifiers apply. A shield
// hit keeps a sliver of its damage, since the engine passes onHit a shieldHit of damage to shields above zero and
// HarpoonOnHit glances off on it.
public class HarpoonFriendlyFire implements DamageDealtModifier {

    public static final float SHIELD_SHARE = 0.01f;

    private static final String ID = "catchrelease_harpoonFriendlyFire";

    private final ShipAPI tug;

    public HarpoonFriendlyFire(ShipAPI tug) {
        this.tug = tug;
    }

    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point,
                                    boolean shieldHit) {
        if (!(param instanceof DamagingProjectileAPI shot) || !(target instanceof ShipAPI ship)) return null;
        if (shot.getWeapon() == null || !HarpoonOnHit.WEAPON_ID.equals(shot.getWeapon().getId())) return null;
        if (ship.isHulk() || ship.getOwner() != tug.getOwner()) return null;
        damage.getModifier().modifyMult(ID, shieldHit ? SHIELD_SHARE : 0f);
        return ID;
    }
}
