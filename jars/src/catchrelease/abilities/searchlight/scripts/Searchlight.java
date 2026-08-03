package catchrelease.abilities.searchlight.scripts;

import catchrelease.helper.math.CircularArc;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.rendering.distortion.CampaignDistortionRenderer;
import catchrelease.rendering.renderers.RippleRingRenderer;
import catchrelease.abilities.searchlight.rendering.SearchlightGlowRenderer;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.dark.shaders.distortion.WaveDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Searchlight implements EveryFrameScript {
    public static final Color COLOR = new Color(255, 180, 50, 255);

    /** The beam's lens: how much wider than the light it bends, and how hard. */
    public static final float LENS_SIZE_MULT = 0.8f;
    public static final float LENS_INTENSITY = 9f;
    public static final float LENS_FADE_IN = 0.6f;

    public static final float SINE_CADENCE = 90f; //distance the sine wave takes off the arc
    public static final float OSCILLATION_TIME_MULT = 0.7f; //this affects how nervous the searchlights feel

    private SearchlightGlowRenderer glow;
    private final List<RippleRingRenderer> rings = new ArrayList<>();
    private final Vector2f currentRenderLoc = new Vector2f();

    //travel
    private CircularArc arc;
    private float baseArcAngle;
    private int travelDirection = 1; //1 or -1, flips on each limit
    private float oscillationTime = 0f;

    private final IntervalUtil ringInterval = new IntervalUtil(1, 3);
    private boolean expired = false;

    /**
     * The beam's own bend, through GraphicsLib's distortion.
     * <p>
     * Kept alive and moved rather than respawned, so it is one lens travelling with the light rather
     * than a trail of them left behind it. Transient: a distortion is worth nothing on load, and the
     * renderer holds GL state that certainly is not.
     */
    private transient WaveDistortion lens;

    @Override
    public boolean isDone() {
        return expired;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    public void init(CircularArc circularArc) {
        this.arc = circularArc;
        baseArcAngle = arc.startAngle;
        float size = UpgradeManager.getValue(StatIds.SEARCHLIGHT_AREA, 350f);
        glow = new SearchlightGlowRenderer(currentRenderLoc, size, COLOR);

        LunaCampaignRenderer.addTransientRenderer(glow);
        Global.getSoundPlayer().playSound("catchrelease_ui_searchlight_toggle", 1.1f, 1.3f, arc.getPointForAngle(baseArcAngle), new Vector2f(0,0));
    }

    @Override
    public void advance(float amt) {
        if (expired || arc == null) return;

        advanceMovement(amt);
        advanceLens();

        // splash
        ringInterval.advance(amt);
        if (ringInterval.intervalElapsed()) {
            float size = UpgradeManager.getValue(StatIds.SEARCHLIGHT_AREA, 350f);
            RippleRingRenderer ring = new RippleRingRenderer(currentRenderLoc, size, COLOR);
            rings.add(ring);
            LunaCampaignRenderer.addTransientRenderer(ring);
        }

        rings.removeIf(RippleRingRenderer::isExpired);
    }

    public void advanceMovement(float amt) {
        oscillationTime += amt;

        float speed = UpgradeManager.getValue(StatIds.SEARCHLIGHT_SPEED, 30f);
        float progress = arc.getTraversalProgress(baseArcAngle);
        float normalizedProgress = (travelDirection < 0) ? 1f - progress : progress;

        if (normalizedProgress > 0.99f) travelDirection *= -1; //flip dir on last percent so it doesn't go 0

        float degPerSec = arc.convertToDegreesPerSecond(speed);
        baseArcAngle = Misc.normalizeAngle(baseArcAngle + degPerSec * amt * travelDirection);

        Vector2f basePos = arc.getPointForAngle(baseArcAngle);

        float sine = (float) Math.sin(oscillationTime * OSCILLATION_TIME_MULT);
        float offset = sine * SINE_CADENCE;

        float tangentAngle = baseArcAngle + 90f;
        Vector2f renderPos = MathUtils.getPointOnCircumference(basePos, offset, tangentAngle);

        updateRenderLoc(renderPos);
    }

    /**
     * A lens under the beam, following it. The light is looking through the fabric, so it ought to
     * bend what is behind it - and a distortion that moves with the light is what says the beam is
     * the thing doing the looking.
     */
    protected void advanceLens() {
        if (!CampaignDistortionRenderer.isSupported()) return;

        float size = UpgradeManager.getValue(StatIds.SEARCHLIGHT_AREA, 350f);

        if (lens == null) {
            lens = new WaveDistortion(new Vector2f(currentRenderLoc), new Vector2f());

            lens.setSize(size * LENS_SIZE_MULT);
            lens.setIntensity(LENS_INTENSITY);
            lens.setLifetime(Float.MAX_VALUE);
            lens.fadeInSize(LENS_FADE_IN);

            CampaignDistortionRenderer.addDistortion(lens);
            return;
        }

        //moved rather than respawned - one lens travelling, not a trail of them left behind
        lens.setLocation(new Vector2f(currentRenderLoc));
        lens.setSize(size * LENS_SIZE_MULT);
    }

    public void updateRenderLoc(Vector2f newLoc){
        currentRenderLoc.x = newLoc.x;
        currentRenderLoc.y = newLoc.y;
    }

    public void expire(boolean withFade) {
        float fadeSeconds = withFade ? 1f : 0f;
        for (RippleRingRenderer ring : rings) ring.fadeAndExpire(fadeSeconds);
        if (glow != null) glow.fadeAndExpire(fadeSeconds);

        rings.clear();

        //the lens has no fade of its own worth waiting on: it goes with the light
        if (lens != null) {
            CampaignDistortionRenderer.removeDistortion(lens);
            lens = null;
        }

        expired = true;
    }

}
