package catchrelease.skillshot.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;
import catchrelease.skillshot.SkillshotSettings;

import java.awt.*;

/**
 * The plain skillshot reticule: an arrow at the cursor pointing away from the fleet. Use it for
 * anything fired in a direction rather than at a spot.
 * <p>
 * Pairs well with the guide lines on {@link BaseReticuleRenderer} - {@code withTrajectory()} for a
 * line down the aim direction, {@code withBounds(30f)} for the edges of a spread.
 */
public class DirectionReticuleRenderer extends BaseReticuleRenderer {

    transient private SpriteAPI arrow;

    protected float size;

    public DirectionReticuleRenderer() {
        this(SkillshotSettings.DIRECTION_ARROW_SIZE);
    }

    public DirectionReticuleRenderer(float size) {
        this.size = size;
    }

    @Override
    public void renderCursorBoundObject(CampaignEngineLayers layer, ViewportAPI viewport, float angleToCursor, Vector2f cursorPos, Color colour) {
        if (arrow == null) arrow = Global.getSettings().getSprite(SkillshotSettings.SPRITE_CATEGORY, SkillshotSettings.SPRITE_DIRECTION_ARROW);

        arrow.setAlphaMult(SkillshotSettings.RETICULE_ALPHA * SkillshotSettings.CURSOR_SPRITE_ALPHA_MULT);
        arrow.setWidth(size);
        arrow.setHeight(size);
        arrow.setAngle(angleToCursor - 90f);
        arrow.setColor(colour);
        arrow.renderAtCenter(cursorPos.x, cursorPos.y);
    }

    /** The arrow sits on the aim point, so the lines run all the way into it rather than stopping short. */
    @Override
    protected float getGuideLineEndPadding() {
        return 0f;
    }
}
