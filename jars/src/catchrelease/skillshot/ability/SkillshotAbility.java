package catchrelease.skillshot.ability;

import com.fs.starfarer.api.characters.AbilityPlugin;
import catchrelease.skillshot.render.SkillshotRenderer;

/**
 * What the input layer needs from an ability. Implement this on top of whatever ability base class
 * your mod already uses, or just extend {@link BaseSkillshotAbility}.
 * <p>
 * The ability additionally has to carry {@link catchrelease.skillshot.SkillshotSettings#TAG_SKILLSHOT} in
 * abilities.csv - that tag is how the hotkey listener recognises it in the ability bar.
 */
public interface SkillshotAbility extends AbilityPlugin {

    /**
     * A fresh reticule for one targeting session. Called every time targeting starts, so returning
     * a new instance is expected.
     */
    SkillshotRenderer createReticule();

    /**
     * Fire. Called by the input layer once the player commits to a valid aim point, bypassing the
     * normal activation path (which would fire the moment the button is pressed, before aiming).
     */
    void forceActivation();

    /**
     * Whether pressing this ability should open a targeting session at all. May change over the
     * ability's life - it is asked every time the player presses the key or the button.
     * <p>
     * When false the framework stays out of the way entirely: no reticule, no input interception, no
     * targeting-blocked restrictions. The press runs the plain vanilla ability path instead, so the
     * ability behaves like any other {@code BaseDurationAbility} until it returns true again.
     */
    boolean showReticuleOnActivation();
}
