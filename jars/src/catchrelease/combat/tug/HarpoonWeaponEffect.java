package catchrelease.combat.tug;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

// Registered as the weapon's everyFrameEffect: the engine also calls onFire on that instance, so the .proj needs no
// onFireEffect. One instance per weapon.
public class HarpoonWeaponEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        TugTethers.get(engine).fire(weapon, projectile);
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
    }
}
