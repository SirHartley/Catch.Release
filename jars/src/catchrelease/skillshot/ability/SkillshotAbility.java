package catchrelease.skillshot.ability;

import com.fs.starfarer.api.characters.AbilityPlugin;
import catchrelease.skillshot.render.SkillshotRenderer;

/**
 * What the input layer needs from an ability. Implement on top of your ability base class, or
 * extend {@link BaseSkillshotAbility}. Must also carry
 * {@link catchrelease.skillshot.SkillshotSettings#TAG_SKILLSHOT} in abilities.csv, which is how the
 * hotkey listener recognises it.
 */
public interface SkillshotAbility extends AbilityPlugin {

    /** New instance expected each targeting session. */
    SkillshotRenderer createReticule();

    /** Fires once the player commits to a valid aim point, bypassing the normal activation path. */
    void forceActivation();

    /**
     * Asked on every press; may change over the ability's life. False falls through to the plain
     * vanilla ability path (no reticule, no input interception).
     */
    boolean showReticuleOnActivation();
}
