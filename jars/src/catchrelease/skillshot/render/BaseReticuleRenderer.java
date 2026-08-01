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
import java.util.EnumSet;
import java.util.List;

/**
 * Draws the ring around the player fleet, rotated to face the cursor and tinted by
 * {@link #isValidPosition()}, then hands off to {@link #renderCursorBoundObject} for whatever the
 * concrete reticule wants to draw at the cursor.
 * <p>
 * Subclass this to make a new reticule; return it from
 * {@link catchrelease.skillshot.ability.SkillshotAbility#createReticule()}.
 * <p>
 * Any reticule can optionally show guide lines out from the fleet along the aim direction - a single
 * line for the trajectory, a pair for the bounds of a spread, or all three. They are off by default,
 * so a reticule that does not ask for them looks exactly as it did before:
 *
 * <pre>
 * //straight shot, one line showing where it goes
 * return new DirectionReticuleRenderer().withTrajectory();
 *
 * //30 degree spread out to a fixed range, two lines showing what it can hit
 * return new AreaReticuleRenderer(400f).withBounds(30f).withLength(2000f);
 * </pre>
 *
 * The lines share the reticule's valid/invalid tint.
 */
public abstract class BaseReticuleRenderer implements SkillshotRenderer {

    transient private SpriteAPI fleetReticule;

    private boolean done = false;

    /** Aim point of the frame currently being rendered. Safe to read from isValidPosition(). */
    protected Vector2f cursorPos = new Vector2f();

    protected boolean showTrajectory = false;
    protected boolean showBounds = false;
    protected float boundsSpread = 0f;
    protected float length = 0f;

    /** Draws one line from the fleet along the aim direction - where the shot is going. */
    public BaseReticuleRenderer withTrajectory() {
        showTrajectory = true;
        return this;
    }

    /**
     * Draws two lines marking the edges of the shot.
     *
     * @param spreadDegrees total angle between the two lines. 0 gives a pair on top of each other, so
     *                      pass the ability's actual spread - the arc the shot can end up in
     */
    public BaseReticuleRenderer withBounds(float spreadDegrees) {
        showBounds = true;
        boundsSpread = spreadDegrees;
        return this;
    }

    /**
     * Fixed line length in world units - pass the ability's range so the lines stop where the shot
     * does. Without this they run out to the cursor, however far away it is.
     */
    public BaseReticuleRenderer withLength(float worldUnits) {
        length = worldUnits;
        return this;
    }

    /**
     * How far short of the cursor the guide lines stop, so they do not run underneath whatever
     * {@link #renderCursorBoundObject} draws there. Only applies while the lines end at the cursor -
     * a {@link #withLength(float)} line is showing a range and gets to keep its full length.
     */
    protected float getGuideLineEndPadding() {
        return 0f;
    }

    @Override
    public void setDone() {
        this.done = true;
    }

    @Override
    public boolean isExpired() {
        return done;
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public Vector2f getCursorPosition() {
        return cursorPos;
    }

    @Override
    public boolean isValidPosition() {
        return true;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isExpired()) return;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        if (fleetReticule == null) {
            fleetReticule = Global.getSettings().getSprite(SkillshotSettings.SPRITE_CATEGORY, SkillshotSettings.SPRITE_FLEET_RETICULE);
        }

        //update the aim point before anything reads it - isValidPosition() below depends on it
        cursorPos = SkillshotUtils.getCursorWorldPosition();

        float angleToCursor = Misc.getAngleInDegrees(fleet.getLocation(), cursorPos);
        float reticuleSize = fleet.getRadius() + SkillshotSettings.FLEET_RETICULE_PADDING;
        Color color = isValidPosition() ? fleet.getIndicatorColor() : SkillshotSettings.INVALID_COLOR;

        fleetReticule.setAlphaMult(SkillshotSettings.RETICULE_ALPHA);
        fleetReticule.setWidth(reticuleSize);
        fleetReticule.setHeight(reticuleSize);
        fleetReticule.setAngle(angleToCursor - 90f);
        fleetReticule.setColor(color);
        fleetReticule.renderAtCenter(fleet.getLocation().x, fleet.getLocation().y);

        //under the cursor object, so that stays the thing the eye lands on
        renderGuideLines(fleet, reticuleSize, angleToCursor, cursorPos, color);

        renderCursorBoundObject(layer, viewport, angleToCursor, cursorPos, color);
    }

    /**
     * The optional trajectory / bounds lines, drawn out from the fleet along the aim direction. Does
     * nothing unless {@link #withTrajectory()} or {@link #withBounds(float)} asked for them.
     */
    protected void renderGuideLines(CampaignFleetAPI fleet, float reticuleSize, float angleToCursor, Vector2f cursorPos, Color colour) {
        if (!showTrajectory && !showBounds) return;

        Vector2f origin = fleet.getLocation();

        //start outside the fleet reticule ring instead of at the fleet centre, where the lines would
        //just cross the ring sprite
        float from = reticuleSize * 0.5f;
        float to = length > 0f
                ? length
                : Misc.getDistance(origin, cursorPos) - getGuideLineEndPadding();

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

    /**
     * @param angleToCursor degrees from the player fleet to the cursor
     * @param cursorPos     aim point in world coordinates
     * @param colour        already resolved from {@link #isValidPosition()}
     */
    public abstract void renderCursorBoundObject(CampaignEngineLayers layer, ViewportAPI viewport, float angleToCursor, Vector2f cursorPos, Color colour);
}
