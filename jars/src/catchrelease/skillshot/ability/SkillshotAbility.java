package catchrelease.skillshot.ability;

import com.fs.starfarer.api.characters.AbilityPlugin;
import catchrelease.skillshot.render.SkillshotRenderer;

public interface SkillshotAbility extends AbilityPlugin {

    SkillshotRenderer createReticule();

    void forceActivation();

    boolean showReticuleOnActivation();
}
