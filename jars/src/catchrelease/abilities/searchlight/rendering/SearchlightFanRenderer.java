package catchrelease.abilities.searchlight.rendering;

import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.helper.math.TrigHelper;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;

/**
 * The fanned face: the searchlight opened into a wedge thrown from the hull, worn while the fan
 * module is fitted. The spot and the burn are set down where the beam is aimed; this one has to
 * reach from the fleet out past the aim point, pivoting and stretching as the sweep moves, and no
 * sprite squashed and turned every frame survives that looking like light - so it is cut as
 * geometry instead, the way {@link catchrelease.rendering.helper.Disc} cuts its circles.
 * <p>
 * The shape is not this renderer's to choose. {@link Searchlight#getLitStrength(Vector2f)} already
 * decides what a fanned light touches - the module's half-angle either side of the aim, reaching
 * the aim distance plus the beam's own radius - and a wedge drawn wider or shorter than that would
 * show the player a light finding things it visibly is not touching. The far end is cut as an arc
 * rather than a chord for the same reason: the reach is a distance, not a baseline.
 * <p>
 * The falloff is where drawing and finding part ways. A wedge with a straight ramp on it is a
 * cardboard cutout: hard-rimmed, with a crease down the middle where the ramp peaks. What sells a
 * beam is that nothing about it is linear - the brightness holds a shoulder at the emitter before
 * dying off down the length, and the sides ease out over the last few degrees instead of stopping
 * - so both runs are smootherStep here rather than the gameplay's own straight lines. Down the
 * length the gameplay's tip strength stays under the curve as a floor, so nothing short of the aim
 * point ever dims below what the light still finds there; past the aim - the overshoot the light
 * keeps beyond where it is looking - the floor eases the rest of the way to nothing, and the wedge
 * ends by fading instead of at a visible rim.
 * <p>
 * The colour is the one part of the drawing the window underneath does not mirror, and it is
 * where the lamp look lives. A single flat rgb over those alphas still reads as a tidy gradient;
 * a real lamp puts a rim of scattered light at the edge of its throw. So the rgb leans into a
 * harder purple in a band down each side - and only the rgb, with both falloffs left exactly the
 * shared curves above, so the window still opens precisely where the light over it is bright.
 */
public class SearchlightFanRenderer implements LunaCampaignRenderingPlugin {

    /**
     * How finely the wedge is cut. GL runs colour straight across a triangle, so the curves above
     * are only as curved as the mesh is fine - this is about the coarsest cut at which the shoulder
     * and the eased edges survive, and a few hundred untextured vertices a frame cost nothing.
     */
    public static final int STEPS_ACROSS = 16;
    public static final int STEPS_ALONG = 24;

    public static final float SUPERLUMINAL_TIME = 0.4f;

    /**
     * The rim bands' colour: the beam's own purple leant harder - red up, green down, the same
     * energy - so the edges read as the lamp's light scattering at the rim rather than a second
     * lamp behind it. Private where the shared constants above are not: the breach window
     * deliberately takes no part of the colour.
     */
    private static final Color EDGE_TINT = new Color(230, 35, 255);

    /**
     * Where each band sits across the half-width and how far it spreads: centred out where the
     * across-ease has mostly emptied the fill - any nearer the crease and the core drowns it -
     * and wide enough to arrive and leave gradually, a band of light rather than a painted line.
     */
    private static final float BAND_CENTER = 0.62f;
    private static final float BAND_WIDTH = 0.38f;

    /** Where the light is thrown from and where it is aimed - the live vectors, not copies, which
     * is all it takes for the wedge to ride the fleet and follow the sweep. */
    private final Vector2f origin;
    private final Vector2f aim;

    private final float size;
    private final Color color;

    private boolean expired = false;

    //fadeAndExpire
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    private float timePassed = 0f;
    private float extraAlphaMult = 1f;

    //flicker
    private FlickerUtilV2 flicker = new FlickerUtilV2(8f);

    public SearchlightFanRenderer(Vector2f origin, Vector2f aim, float size, Color color) {
        this.origin = origin;
        this.aim = aim;
        this.size = size;
        this.color = color;
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    public void fadeAndExpire(float fadeSeconds) {
        if (expired) return;

        fading = true;
        fadeDuration = fadeSeconds;
        fadeElapsed = 0f;
    }

    @Override
    public void advance(float amount) {
        if (expired) return;

        // superluminance
        timePassed += amount;
        float progress = Math.min(timePassed / SUPERLUMINAL_TIME, 1f);
        extraAlphaMult = 0.8f * TrigHelper.smootherStep(1f - progress);

        //flicker
        flicker.advance(amount);

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) {
                expired = true;
            }
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        //the spot's layer, and it matters as much here: the dents are cut out of this light one
        //layer up, and drawn level with it they would subtract from the black underneath instead
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;

        //the spot's numbers exactly, flash and flicker included, so fitting the module changes the
        //shape of the light and not how much of it there is
        float alpha;
        if (extraAlphaMult > 0) alpha = extraAlphaMult;
        else alpha = 0.12f - 0.04f * flicker.getBrightness();

        if (fading) {
            float fadeT = MathUtils.clamp(1f - (fadeElapsed / fadeDuration), 0f, 1f);
            alpha *= fadeT;
        }
        if (alpha <= 0f) return;

        //with the aim on top of the fleet there is no direction to open around - the sweep rides
        //an arc twice the beam's size out, so this only catches a degenerate first frame
        float distance = Misc.getDistance(origin, aim);
        if (distance < 1f) return;

        //the radius live rather than the one this was built with. The wedge is drawn every frame and
        //the hit test is computed every frame off the current upgrade, so a cached size means a fan
        //that finds things past where it is drawn until something happens to rebuild the renderer
        drawWedge(Misc.getAngleInDegrees(origin, aim), distance + Searchlight.getArea(), distance, alpha);
    }

    /**
     * The wedge itself: bands of triangle strip walked out from the origin, each vertex placed in
     * polar coordinates off the aim direction and given its own slice of the two falloffs. Additive
     * and untextured, since it is meant to be light and not a surface.
     */
    protected void drawWedge(float direction, float length, float aimDistance, float alpha) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        float er = EDGE_TINT.getRed() / 255f;
        float eg = EDGE_TINT.getGreen() / 255f;
        float eb = EDGE_TINT.getBlue() / 255f;

        //the ease past the aim point starts wherever the aim currently is, so the fade-out always
        //covers exactly the overshoot however far the beam is leaning
        float aimFract = aimDistance / length;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (int band = 0; band < STEPS_ALONG; band++) {
            float uNear = band / (float) STEPS_ALONG;
            float uFar = (band + 1) / (float) STEPS_ALONG;

            float alongNear = along(uNear, aimFract) * alpha;
            float alongFar = along(uFar, aimFract) * alpha;

            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= STEPS_ACROSS; i++) {
                float t = i / (float) STEPS_ACROSS * 2f - 1f; //-1 at one edge, 1 at the other

                double angle = Math.toRadians(direction + Searchlight.getFanHalfAngle() * t);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);

                float across = TrigHelper.smootherStep(1f - Math.abs(t));

                float rim = TrigHelper.smootherStep(
                        1f - Math.abs(Math.abs(t) - BAND_CENTER) / BAND_WIDTH);
                float vr = r + (er - r) * rim;
                float vg = g + (eg - g) * rim;
                float vb = b + (eb - b) * rim;

                GL11.glColor4f(vr, vg, vb, across * alongFar);
                GL11.glVertex2f(origin.x + cos * length * uFar, origin.y + sin * length * uFar);
                GL11.glColor4f(vr, vg, vb, across * alongNear);
                GL11.glVertex2f(origin.x + cos * length * uNear, origin.y + sin * length * uNear);
            }
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    /**
     * The brightness left at a fraction of the beam's length: the shoulder-and-die curve over the
     * gameplay's tip floor out to the aim point, and the floor itself eased away to nothing across
     * the overshoot beyond it.
     */
    protected float along(float u, float aimFract) {
        float base = Searchlight.FAN_TIP_STRENGTH
                + (1f - Searchlight.FAN_TIP_STRENGTH) * TrigHelper.smootherStep(1f - u);

        if (u <= aimFract) return base;

        return base * TrigHelper.smootherStep((1f - u) / (1f - aimFract));
    }
}
