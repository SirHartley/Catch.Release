package catchrelease.abilities.searchlight.scripts;

import catchrelease.helper.math.CircularArc;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.LocationAPI;
import catchrelease.helper.math.TrigHelper;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.rendering.distortion.CampaignDistortionRenderer;
import catchrelease.rendering.renderers.RippleRingRenderer;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.rendering.SearchlightBreachRenderer;
import catchrelease.abilities.searchlight.rendering.SearchlightFanBreachRenderer;
import catchrelease.abilities.searchlight.rendering.SearchlightFanRenderer;
import catchrelease.abilities.searchlight.rendering.SearchlightGlowRenderer;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
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

    /** Beam and ring colour. Every lamp is a breach lamp now, burning a window through the pond fabric. */
    public static final Color COLOR = new Color(185, 80, 255, 255);
    public static final Color RING_COLOR = new Color(255, 120, 255);

    /** The beam's lens: how much wider than the light it bends, and how hard. */
    public static final float LENS_SIZE_MULT = 0.8f;
    public static final float LENS_INTENSITY = 9f;
    public static final float LENS_FADE_IN = 0.6f;

    public static final float SINE_CADENCE = 90f; //how far the sine wave carries off the arc
    public static final float OSCILLATION_TIME_MULT = 0.7f; //sine speed

    /** The beam's radius without a row in the upgrade sheet. */
    public static final float AREA_FALLBACK = 240f;

    /**
     * Furthest from the fleet a light can reach, upgrades included: arc radius (2x beam size) +
     * sine wander + beam size. Anything seeding entities just outside light range must use this.
     */
    public static float getMaxReach() {
        float size = getArea();

        return size * 2f + SINE_CADENCE + size;
    }

    /** Beam radius; single source of truth so the arc, lens, and reach can't disagree. */
    public static float getArea() {
        return UpgradeManager.getValue(StatIds.SEARCHLIGHT_AREA, AREA_FALLBACK);
    }

    /**
     * The faces a light can wear. All transient: LunaCampaignRenderer's registration does not
     * survive a load, so a non-transient field would come back non-null but undrawn. Left null,
     * {@link #advanceLook()} rebuilds whichever is needed on the first frame after load.
     */
    private transient SearchlightGlowRenderer glow;
    private transient SearchlightBreachRenderer breach;
    private transient SearchlightFanRenderer fan;
    private transient SearchlightFanBreachRenderer fanBreach;

    /** Seconds one face takes to hand over to the other when the fleet crosses. */
    public static final float LOOK_SWAP_FADE = 1f;

    private final List<RippleRingRenderer> rings = new ArrayList<>();
    private final Vector2f currentRenderLoc = new Vector2f();

    private CircularArc arc;
    private float baseArcAngle;
    private int travelDirection = 1; //1 or -1, flips on each limit
    private float oscillationTime = 0f;

    private final IntervalUtil ringInterval = new IntervalUtil(1, 3);
    private boolean expired = false;

    /** What the beam is locked on. Transient - a lock is brief, so it just resumes sweeping on load. */
    private transient SectorEntityToken lockTarget;
    private float lockLeft = 0f;
    private float lockBlend = 0f;
    private float lockCooldown = 0f;

    /** Where the held thing is, kept separately so the lean-off has somewhere to lean from. */
    private final Vector2f lockLoc = new Vector2f();

    /** Which side of the held thing the light stopped on, fixed at the moment it stopped. */
    private float lockBearing = 0f;

    /** Fan half-angle at base radius, and minimum strength kept at its tip (never fades to nothing). */
    public static final float FAN_HALF_ANGLE = 11f;
    public static final float FAN_TIP_STRENGTH = 0.35f;

    /** Fan sweep rate relative to a spot's, reduced since the fan's far end covers more sky per degree. */
    public static final float FAN_SWEEP_MULT = 0.7f;

    /** Hold offset from target, as a share of beam radius - off-centre because the beam's centre is brightest, hiding the target. */
    public static final float LOCK_HOLD_RADIUS_SHARE = 0.5f;

    /** Seconds spent leaning onto what it found, and the wait before it may stop for anything else. */
    public static final float LOCK_EASE_TIME = 1.6f;
    public static final float LOCK_COOLDOWN = 4f;

    /** GraphicsLib distortion for the beam's bend; moved not respawned. Transient - holds GL state. */
    private transient WaveDistortion lens;

    @Override
    public boolean isDone() {
        return expired;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    /**
     * The arc sweeps in world-fixed degrees, not hull heading - tying it to a manually-piloted
     * fleet's constantly-correcting facing never lets the sweep settle.
     */
    public void init(CircularArc circularArc) {
        this.arc = circularArc;
        baseArcAngle = arc.startAngle;

        //set before the faces are built, or they'd read the vector's (0,0) default for one frame
        updateRenderLoc(arc.getPointForAngle(baseArcAngle));

        advanceLook();

        Global.getSoundPlayer().playSound("catchrelease_ui_searchlight_toggle", 1.1f, 1.3f, arc.getPointForAngle(baseArcAngle), new Vector2f(0,0));
    }

    @Override
    public void advance(float amt) {
        if (expired || arc == null) return;

        advanceMovement(amt);
        advanceLook();
        advanceLens();

        //spot only - a fan has no single landing point for a ripple to make sense at
        ringInterval.advance(amt);
        if (ringInterval.intervalElapsed() && fan == null) {
            float size = getArea();
            RippleRingRenderer ring = new RippleRingRenderer(currentRenderLoc, size, RING_COLOR);
            rings.add(ring);
            LunaCampaignRenderer.addTransientRenderer(ring);
        }

        rings.removeIf(RippleRingRenderer::isExpired);
    }

    public void advanceMovement(float amt) {
        advanceLock(amt);

        //sweep pauses while locked, resuming from where it left off rather than the elapsed clock
        if (lockTarget == null) {
            oscillationTime += amt;

            float speed = UpgradeManager.getValue(StatIds.SEARCHLIGHT_SPEED, 30f);
            if (isFanned()) speed *= FAN_SWEEP_MULT;
            float progress = arc.getTraversalProgress(baseArcAngle);
            float normalizedProgress = (travelDirection < 0) ? 1f - progress : progress;

            if (normalizedProgress > 0.99f) travelDirection *= -1; //flip dir on last percent so it doesn't go 0

            float degPerSec = arc.convertToDegreesPerSecond(speed);
            baseArcAngle = Misc.normalizeAngle(baseArcAngle + degPerSec * amt * travelDirection);
        }

        Vector2f basePos = arc.getPointForAngle(baseArcAngle);

        float sine = (float) Math.sin(oscillationTime * OSCILLATION_TIME_MULT);
        float offset = sine * SINE_CADENCE;

        float tangentAngle = baseArcAngle + 90f;
        Vector2f renderPos = MathUtils.getPointOnCircumference(basePos, offset, tangentAngle);

        //eased both ways - a hard snap on lock/release reads as two lights, not one
        if (lockBlend > 0f) {
            float t = TrigHelper.smootherStep(lockBlend);

            renderPos = new Vector2f(
                    renderPos.x + (lockLoc.x - renderPos.x) * t,
                    renderPos.y + (lockLoc.y - renderPos.y) * t);
        }

        updateRenderLoc(renderPos);
    }

    /**
     * Picks spot vs. fan face for the current fit. Checked every frame since the fan module can
     * be refitted under a running light. Draw order is registration order, so window is registered
     * before light. Also heals load: transient faces come back null and get rebuilt here.
     */
    protected void advanceLook() {
        boolean fanned = isFanned();

        if (fanned) {
            if (breach != null) {
                breach.fadeAndExpire(LOOK_SWAP_FADE);
                breach = null;
            }
            if (glow != null) {
                glow.fadeAndExpire(LOOK_SWAP_FADE);
                glow = null;
            }
        } else {
            if (fanBreach != null) {
                fanBreach.fadeAndExpire(LOOK_SWAP_FADE);
                fanBreach = null;
            }
            if (fan != null) {
                fan.fadeAndExpire(LOOK_SWAP_FADE);
                fan = null;
            }
        }

        if (fanned) {
            if (fanBreach == null) {
                fanBreach = new SearchlightFanBreachRenderer(getOrigin(), currentRenderLoc);
                LunaCampaignRenderer.addTransientRenderer(fanBreach);
            }
            if (fan == null) {
                //needs both the pivot (fleet) and aim (sweep) vectors
                fan = new SearchlightFanRenderer(getOrigin(), currentRenderLoc, getArea(), COLOR);
                LunaCampaignRenderer.addTransientRenderer(fan);
            }
        } else {
            if (breach == null) {
                breach = new SearchlightBreachRenderer(currentRenderLoc, getArea());
                LunaCampaignRenderer.addTransientRenderer(breach);
            }
            if (glow == null) {
                glow = new SearchlightGlowRenderer(currentRenderLoc, getArea(), COLOR);
                LunaCampaignRenderer.addTransientRenderer(glow);
            }
        }
    }

    /** Acquires, holds, and releases a lock target - one at a time, with a cooldown between locks. */
    protected void advanceLock(float amt) {
        if (lockCooldown > 0f) lockCooldown -= amt;

        if (lockTarget != null) {
            lockLeft -= amt;

            if (lockTarget.isExpired() || lockLeft <= 0f) {
                lockTarget = null;
                lockCooldown = LOCK_COOLDOWN;
            } else {
                //re-tracked each frame - the target is swimming, not stationary
                lockLoc.set(holdPointFor(lockTarget.getLocation()));
            }
        }

        //not while still leaning off the last lock, or the two blends fight over the same beam
        if (lockTarget == null && lockBlend <= 0f && lockCooldown <= 0f) acquire();

        float step = LOCK_EASE_TIME <= 0f ? 1f : amt / LOCK_EASE_TIME;
        lockBlend = MathUtils.clamp(lockBlend + (lockTarget != null ? step : -step), 0f, 1f);
    }

    /** The nearest thing under the beam worth stopping for, if the rig has been taught to stop. */
    protected void acquire() {
        float lockTime = TackleManager.get(Tackle.Fit.SEARCHLIGHT).lockTime;
        if (lockTime <= 0f) return;

        if (Global.getSector() == null) return;
        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) return;

        SectorEntityToken best = null;
        float bestLit = 0f;

        //best lit, not nearest - under a fan those differ (centre line beats off-axis)
        for (SectorEntityToken buried : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {
            if (buried.isExpired()) continue;

            float lit = getLitStrength(buried.getLocation());
            if (lit <= bestLit) continue;

            bestLit = lit;
            best = buried;
        }

        if (best == null) return;

        lockTarget = best;
        lockLeft = lockTime;

        //bearing fixed here, not recomputed per frame, or it would chase the beam chasing it
        lockBearing = Misc.getAngleInDegrees(best.getLocation(), currentRenderLoc);
        lockLoc.set(holdPointFor(best.getLocation()));
    }

    /** Hold point beside the target, offset by {@link #LOCK_HOLD_RADIUS_SHARE} of beam radius. */
    protected Vector2f holdPointFor(Vector2f target) {
        return MathUtils.getPointOnCircumference(target,
                getArea() * LOCK_HOLD_RADIUS_SHARE, lockBearing);
    }

    /** Keeps the GraphicsLib distortion lens tracking the beam. */
    protected void advanceLens() {
        if (!CampaignDistortionRenderer.isSupported()) return;

        float size = getArea();

        if (lens == null) {
            lens = new WaveDistortion(new Vector2f(currentRenderLoc), new Vector2f());

            lens.setSize(size * LENS_SIZE_MULT);
            lens.setIntensity(LENS_INTENSITY);
            lens.setLifetime(Float.MAX_VALUE);
            lens.fadeInSize(LENS_FADE_IN);

            CampaignDistortionRenderer.addDistortion(lens);
            return;
        }

        lens.setLocation(new Vector2f(currentRenderLoc));

        //skipped during fade-in, which owns size itself; otherwise picks up area upgrades
        if (!lens.isFading()) lens.setSize(size * LENS_SIZE_MULT);
    }

    public Vector2f getRenderLoc() {
        return currentRenderLoc;
    }

    /** Where the light is thrown from - the fleet, via the arc's own location vector. */
    public Vector2f getOrigin() {
        return arc == null ? currentRenderLoc : arc.center;
    }

    /** Whether the fan is fitted, which changes the shape of everything the light is doing. */
    public static boolean isFanned() {
        return TackleManager.get(Tackle.Fit.SEARCHLIGHT).fanBeam;
    }

    /** Fan half-angle scaled with the current beam radius, so upgrading area widens the fan too. */
    public static float getFanHalfAngle() {
        return FAN_HALF_ANGLE * (getArea() / AREA_FALLBACK);
    }

    /** How lit a point is by this light (0-1), handling both spot and fan shapes. Single source of truth. */
    public float getLitStrength(Vector2f at) {
        float size = getArea();

        if (!isFanned()) {
            float distance = Misc.getDistance(currentRenderLoc, at);
            if (distance > size) return 0f;

            float inBeam = 1f - MathUtils.clamp(distance / Math.max(1f, size), 0f, 1f);

            return inBeam * inBeam;
        }

        Vector2f origin = getOrigin();

        //extends past the aim point by beam size, so fan and spot reach match {@link #getMaxReach()}
        float length = Misc.getDistance(origin, currentRenderLoc) + size;
        if (length <= 1f) return 0f;

        float distance = Misc.getDistance(origin, at);
        if (distance > length) return 0f;

        float off = Math.abs(Misc.getAngleDiff(
                Misc.getAngleInDegrees(origin, currentRenderLoc),
                Misc.getAngleInDegrees(origin, at)));

        if (off > getFanHalfAngle()) return 0f;

        float across = 1f - off / getFanHalfAngle();
        float along = 1f - MathUtils.clamp(distance / length, 0f, 1f);

        return across * across * (FAN_TIP_STRENGTH + (1f - FAN_TIP_STRENGTH) * along);
    }

    public float getSize() {
        return getArea();
    }

    public void updateRenderLoc(Vector2f newLoc){
        currentRenderLoc.x = newLoc.x;
        currentRenderLoc.y = newLoc.y;
    }

    public void expire(boolean withFade) {
        float fadeSeconds = withFade ? 1f : 0f;
        for (RippleRingRenderer ring : rings) ring.fadeAndExpire(fadeSeconds);
        if (glow != null) glow.fadeAndExpire(fadeSeconds);

        if (breach != null) {
            breach.fadeAndExpire(fadeSeconds);
            breach = null;
        }
        if (fan != null) {
            fan.fadeAndExpire(fadeSeconds);
            fan = null;
        }
        if (fanBreach != null) {
            fanBreach.fadeAndExpire(fadeSeconds);
            fanBreach = null;
        }

        rings.clear();

        if (lens != null) {
            CampaignDistortionRenderer.removeDistortion(lens);
            lens = null;
        }

        expired = true;
    }

}
