package catchrelease.skillshot.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import catchrelease.skillshot.SkillshotSettings;
import catchrelease.skillshot.util.SkillshotUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The plain skillshot reticule: an arrow at the cursor pointing away from the fleet. Use it for
 * anything fired in a direction rather than at a spot.
 * <p>
 * Optionally draws guide lines out from the fleet along the aim direction - a single line for the
 * trajectory, a pair for the bounds of a spread, or all three:
 *
 * <pre>
 * //straight shot, one line showing where it goes
 * return new DirectionReticuleRenderer().withTrajectory();
 *
 * //30 degree spread out to a fixed range, two lines showing what it can hit
 * return new DirectionReticuleRenderer().withBounds(30f).withLength(2000f);
 * </pre>
 *
 * Both are off by default, so a reticule that does not ask for them looks exactly as it did before.
 * The lines share the reticule's valid/invalid tint.
 */
public class DirectionReticuleRenderer extends BaseReticuleRenderer {

    transient private SpriteAPI arrow;

    protected float size;

    protected boolean showTrajectory = false;
    protected boolean showBounds = false;
    protected float boundsSpread = 0f;
    protected float length = 0f;

    public DirectionReticuleRenderer() {
        this(SkillshotSettings.DIRECTION_ARROW_SIZE);
    }

    public DirectionReticuleRenderer(float size) {
        this.size = size;
    }

    /** Draws one line from the fleet along the aim direction - where the shot is going. */
    public DirectionReticuleRenderer withTrajectory() {
        showTrajectory = true;
        return this;
    }

    /**
     * Draws two lines marking the edges of the shot.
     *
     * @param spreadDegrees total angle between the two lines. 0 gives a pair on top of each other, so
     *                      pass the ability's actual spread - the arc the shot can end up in
     */
    public DirectionReticuleRenderer withBounds(float spreadDegrees) {
        showBounds = true;
        boundsSpread = spreadDegrees;
        return this;
    }

    /**
     * Fixed line length in world units - pass the ability's range so the lines stop where the shot
     * does. Without this they run out to the cursor, however far away it is.
     */
    public DirectionReticuleRenderer withLength(float worldUnits) {
        length = worldUnits;
        return this;
    }

    @Override
    public void renderCursorBoundObject(CampaignEngineLayers layer, ViewportAPI viewport, float angleToCursor, Vector2f cursorPos, Color colour) {
        //under the arrow, so the arrow stays the thing the eye lands on
        renderGuideLines(angleToCursor, cursorPos, colour);

        if (arrow == null) arrow = Global.getSettings().getSprite(SkillshotSettings.SPRITE_CATEGORY, SkillshotSettings.SPRITE_DIRECTION_ARROW);

        arrow.setAlphaMult(SkillshotSettings.RETICULE_ALPHA * SkillshotSettings.CURSOR_SPRITE_ALPHA_MULT);
        arrow.setWidth(size);
        arrow.setHeight(size);
        arrow.setAngle(angleToCursor - 90f);
        arrow.setColor(colour);
        arrow.renderAtCenter(cursorPos.x, cursorPos.y);
    }

    protected void renderGuideLines(float angleToCursor, Vector2f cursorPos, Color colour) {
        if (!showTrajectory && !showBounds) return;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        Vector2f origin = fleet.getLocation();

        //start outside the fleet reticule ring instead of at the fleet centre, where the lines would
        //just cross the ring sprite
        float from = (fleet.getRadius() + SkillshotSettings.FLEET_RETICULE_PADDING) * 0.5f;
        float to = length > 0f ? length : Misc.getDistance(origin, cursorPos);
        if (to <= from) return;

        List<Vector2f> vertices = new ArrayList<>();

        if (showTrajectory) addLine(vertices, origin, angleToCursor, from, to);

        if (showBounds) {
            addLine(vertices, origin, angleToCursor - boundsSpread * 0.5f, from, to);
            addLine(vertices, origin, angleToCursor + boundsSpread * 0.5f, from, to);
        }

        SkillshotUtils.drawLines(vertices, colour,
                SkillshotSettings.RETICULE_ALPHA * SkillshotSettings.GUIDE_LINE_ALPHA_MULT,
                SkillshotSettings.GUIDE_LINE_WIDTH);
    }

    /** Appends the two endpoints of a line running out from origin at the given angle. */
    protected void addLine(List<Vector2f> vertices, Vector2f origin, float angle, float from, float to) {
        Vector2f direction = Misc.getUnitVectorAtDegreeAngle(angle);

        vertices.add(new Vector2f(origin.x + direction.x * from, origin.y + direction.y * from));
        vertices.add(new Vector2f(origin.x + direction.x * to, origin.y + direction.y * to));
    }
}
