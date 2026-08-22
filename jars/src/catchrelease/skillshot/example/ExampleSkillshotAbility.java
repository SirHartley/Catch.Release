package catchrelease.skillshot.example;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import catchrelease.skillshot.SkillshotFramework;
import catchrelease.skillshot.ability.BaseSkillshotAbility;
import catchrelease.skillshot.render.DirectionReticuleRenderer;
import catchrelease.skillshot.render.SkillshotRenderer;


public class ExampleSkillshotAbility extends BaseSkillshotAbility {

    @Override
    public SkillshotRenderer createReticule() {
        return new DirectionReticuleRenderer();
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        SkillshotFramework.log("Example skillshot fired at " + worldTarget + " (" + angleFromFleet + " degrees)");

        SectorEntityToken marker = getFleet().getContainingLocation().createToken(worldTarget);
        getFleet().getContainingLocation().addEntity(marker);
        Misc.fadeAndExpire(marker, 1f);
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        float opad = 10f;

        tooltip.addTitle(spec.getName());
        tooltip.addPara("Hold the ability key to aim, release to fire. Clicking the ability works too - "
                + "click again on the map to commit.", opad);
    }
}
