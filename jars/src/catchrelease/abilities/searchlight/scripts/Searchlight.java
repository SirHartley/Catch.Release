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
    public static final Color COLOR = new Color(255, 180, 50, 255);

    /**
     * The lamp with the breach lamp fitted: the same light in purple, with hyperspace showing
     * through wherever it lands. The ring colour is the pond rim's hot purple, for the pond's
     * reason - a splash on a surface takes the surface's colour, and under this lamp the surface
     * is the same fabric the ponds are torn out of.
     */
    public static final Color BREACH_COLOR = new Color(185, 80, 255, 255);
    public static final Color BREACH_RING_COLOR = new Color(255, 120, 255);

    /** The beam's lens: how much wider than the light it bends, and how hard. */
    public static final float LENS_SIZE_MULT = 0.8f;
    public static final float LENS_INTENSITY = 9f;
    public static final float LENS_FADE_IN = 0.6f;

    public static final float SINE_CADENCE = 90f; //distance the sine wave takes off the arc
    public static final float OSCILLATION_TIME_MULT = 0.7f; //this affects how nervous the searchlights feel

    /** The beam's radius without a row in the upgrade sheet. */
    public static final float AREA_FALLBACK = 240f;

    /**
     * The furthest from the fleet a light can light anything up, upgrades included.
     * <p>
     * The beam rides an arc drawn at twice its own size, wanders a sine off that, and finds anything
     * within its size of wherever it ended up - so this is those three added together. Anything that
     * wants to seed the world just outside what the lights can see needs the same number, and
     * guessing at it is what left the buried motes sitting in a band the lights never reached.
     */
    public static float getMaxReach() {
        float size = getArea();

        return size * 2f + SINE_CADENCE + size;
    }

    /** The beam's own radius. One read, so the arc, the lens and the reach cannot disagree. */
    public static float getArea() {
        return UpgradeManager.getValue(StatIds.SEARCHLIGHT_AREA, AREA_FALLBACK);
    }

    /**
     * The faces a light can wear, and all of them transient for the same reason.
     * <p>
     * Each is registered with LunaCampaignRenderer's transient list, which does not survive a
     * load. Written into the save, a renderer comes back as an orphan: a live object nothing draws,
     * with the field it lives in non-null, so the rebuild never fires and the beam is simply
     * invisible for the rest of the session. Null on load instead, and {@link #advanceLook()} builds
     * whichever one is wanted on the first frame that wants it.
     */
    private transient SearchlightGlowRenderer glow;
    private transient SearchlightBreachRenderer breach;
    private transient SearchlightFanRenderer fan;

    /** What the glow was built in, so a lamp refitted under a running light can be caught
     * changing colour - the glow takes its colour at construction and holds it. */
    private transient Color glowColor;

    /** Seconds one face takes to hand over to the other when the fleet crosses. */
    public static final float LOOK_SWAP_FADE = 1f;

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
     * What the beam has stopped on, how long it has left on it, and how far over it is leaning.
     * <p>
     * The target is transient on purpose. A lock is a second and a half of a light looking at
     * something, and carrying a reference to a wandering mote through a save to resume that is more
     * bookkeeping than the moment is worth - on load the light simply goes back to sweeping.
     */
    private transient SectorEntityToken lockTarget;
    private float lockLeft = 0f;
    private float lockBlend = 0f;
    private float lockCooldown = 0f;

    /** Where the held thing is, kept separately so the lean-off has somewhere to lean from. */
    private final Vector2f lockLoc = new Vector2f();

    /** Which side of the held thing the light stopped on, fixed at the moment it stopped. */
    private float lockBearing = 0f;

    /**
     * How wide the fan opens either side of where it is aimed at the base beam radius, and how much
     * of its strength is left out at the tip.
     * <p>
     * The tip never reaches nothing on purpose: a fan is sold as covering more sky at once, and one
     * that faded away before its own reach would cover less than the spot it replaced.
     */
    public static final float FAN_HALF_ANGLE = 11f;
    public static final float FAN_TIP_STRENGTH = 0.35f;

    /**
     * How much of its sweep rate a fan keeps.
     * <p>
     * A fan pivots on the hull, so the same degrees per second throws the far end of it across far
     * more sky than a spot travelling the same arc - it reads as hurried at a rate that reads as
     * steady on a spot. Slower, so the two feel like the same rig wearing different optics.
     */
    public static final float FAN_SWEEP_MULT = 0.7f;

    /**
     * Where the light settles when it is holding something, as a share of its own radius.
     * <p>
     * Not on top of it. A mote directly under the middle of a beam is the worst place to look at
     * one - it is the brightest part of the light and the thing is a dent in it. Held off to one
     * side it sits where the light has fallen off enough to see the shape in it.
     */
    public static final float LOCK_HOLD_RADIUS_SHARE = 0.5f;

    /** Seconds spent leaning onto what it found, and the wait before it may stop for anything else. */
    public static final float LOCK_EASE_TIME = 1.6f;
    public static final float LOCK_COOLDOWN = 4f;

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

    /**
     * The arc rides the fleet's position and not its facing.
     * <p>
     * Bolting the sweep to the hull's heading is the more sensible-sounding of the two, and it plays
     * badly: a fleet under manual control turns constantly, and every correction dragged all the
     * lights round with it, so the sweep never settled and nothing stayed lit long enough to be
     * worth looking at. Left in the world's own degrees the lights slide along with the fleet while
     * the patch of sky they are working stays where it was, which is what makes a sweep readable.
     * <p>
     * The arc holds the fleet's own location vector, so following the fleet is already its job.
     */
    public void init(CircularArc circularArc) {
        this.arc = circularArc;
        baseArcAngle = arc.startAngle;

        //born where the sweep starts rather than at the vector's default (0,0). The faces hold the
        //live location and read it the moment they are made, so without this the light spent its
        //first frame at the world origin - a spot flashing in the wrong place, and a fan drawn as
        //a spear across half the map before jumping to where it was meant to be
        updateRenderLoc(arc.getPointForAngle(baseArcAngle));

        //picks the face for the fit as it already is, so a light switched on under a breach lamp
        //is born purple rather than flashing orange for a frame and then correcting itself
        advanceLook();

        Global.getSoundPlayer().playSound("catchrelease_ui_searchlight_toggle", 1.1f, 1.3f, arc.getPointForAngle(baseArcAngle), new Vector2f(0,0));
    }

    @Override
    public void advance(float amt) {
        if (expired || arc == null) return;

        advanceMovement(amt);
        advanceLook();
        advanceLens();

        // splash - but not off a fan. A ripple is a round thing landing on the fabric at a point,
        // which is what a spot is; a fan is a wedge thrown off the hull and has no point to land at,
        // so the rings only ever read as a second, rounder light following the first one about
        ringInterval.advance(amt);
        if (ringInterval.intervalElapsed() && fan == null) {
            float size = getArea();
            //a splash takes the colour of the surface it lands on - orange light on the fabric,
            //the rim's hot purple where the lamp has burned a window through it
            RippleRingRenderer ring = new RippleRingRenderer(currentRenderLoc, size,
                    breach != null ? BREACH_RING_COLOR : COLOR);
            rings.add(ring);
            LunaCampaignRenderer.addTransientRenderer(ring);
        }

        rings.removeIf(RippleRingRenderer::isExpired);
    }

    public void advanceMovement(float amt) {
        advanceLock(amt);

        //the sweep stops while the light is holding on something, rather than running on underneath
        //it. Held, the beam is not sweeping - and coming off a lock it should carry on from where it
        //broke off rather than from wherever the clock got to while it was looking elsewhere
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

        //eased both ways, so the light leans over onto what it found and leans back off it. Snapping
        //to the mote and snapping back reads as two lights rather than one changing its mind
        if (lockBlend > 0f) {
            float t = TrigHelper.smootherStep(lockBlend);

            renderPos = new Vector2f(
                    renderPos.x + (lockLoc.x - renderPos.x) * t,
                    renderPos.y + (lockLoc.y - renderPos.y) * t);
        }

        updateRenderLoc(renderPos);
    }

    /**
     * Which face the light wears for the fit as it stands: with the breach lamp, the ordinary lamp
     * in purple over a window of hyperspace; the fan when that module is fitted; the spot
     * otherwise.
     * <p>
     * The burn outranks the fan on purpose - a lamp that burns through the fabric is doing
     * something a wedge of thin light cannot, and a fan of it would spread the burn over more sky
     * than it can open.
     * <p>
     * Checked every frame rather than once, because the modules can be refitted under a running
     * light - the look has to follow the fit, not the toggle. The old face fades on its way out
     * rather than vanishing, and a face that has faded is done for good, so coming back means
     * building a fresh one - which also replays the glow's switch-on flash, and a light re-lighting
     * as its optics are swapped is the right thing for it to do.
     * <p>
     * This is also what heals a load: the faces are transient, so they come back null, and the
     * first frame out here simply makes new ones.
     */
    protected void advanceLook() {
        boolean burning = isBurning();
        boolean fanned = !burning && isFanned();
        Color wanted = burning ? BREACH_COLOR : COLOR;

        if (breach != null && !burning) {
            breach.fadeAndExpire(LOOK_SWAP_FADE);
            breach = null;
        }
        if (fan != null && !fanned) {
            fan.fadeAndExpire(LOOK_SWAP_FADE);
            fan = null;
        }
        //the glow goes under a fan, and goes when it is the wrong colour - rebuilt rather than
        //recoloured, which both replays the switch-on flash and lands the new one after the
        //window below, since within a layer draw order is registration order
        if (glow != null && (fanned || !wanted.equals(glowColor))) {
            glow.fadeAndExpire(LOOK_SWAP_FADE);
            glow = null;
        }

        if (burning && breach == null) {
            breach = new SearchlightBreachRenderer(currentRenderLoc, getArea());
            LunaCampaignRenderer.addTransientRenderer(breach);
        }
        if (fanned && fan == null) {
            //the fan pivots on the fleet and follows the sweep, so it takes both live vectors:
            //where the light is thrown from and where it is looking
            fan = new SearchlightFanRenderer(getOrigin(), currentRenderLoc, getArea(), COLOR);
            LunaCampaignRenderer.addTransientRenderer(fan);
        }
        if (!fanned && glow == null) {
            glowColor = wanted;
            glow = new SearchlightGlowRenderer(currentRenderLoc, getArea(), wanted);
            LunaCampaignRenderer.addTransientRenderer(glow);
        }
    }

    /**
     * Whether this light burns a window through rather than only shining, which is the whole of
     * what the breach lamp is fitted for.
     * <p>
     * Fitted is the only condition. It used to also require the fleet to be standing in
     * hyperspace, and the result was a module that changed nothing whatsoever anywhere the player
     * normally is, which is not a subtle upgrade, it is a broken one. The lamp works where all the
     * fishing gear works - from realspace, through the fabric - and what it opens onto is
     * hyperspace, which is the point of fitting it.
     */
    protected boolean isBurning() {
        return SearchlightAbilityPlugin.burnsIntoHyperspace();
    }

    /**
     * Picking something up, holding it, and letting it go again.
     * <p>
     * Only ever one at a time and never the instant after the last one, because a light that grabs
     * whatever is nearest the moment it is free stops sweeping altogether in a crowded patch - it
     * just walks from mote to mote, and the sweep is the part the player is actually playing.
     */
    protected void advanceLock(float amt) {
        if (lockCooldown > 0f) lockCooldown -= amt;

        if (lockTarget != null) {
            lockLeft -= amt;

            if (lockTarget.isExpired() || lockLeft <= 0f) {
                lockTarget = null;
                lockCooldown = LOCK_COOLDOWN;
            } else {
                //followed rather than pinned where it was found - the thing is swimming, and a light
                //that stayed on the spot would lose it immediately and look like it had jammed
                lockLoc.set(holdPointFor(lockTarget.getLocation()));
            }
        }

        //not while still leaning off the last one, or the two blends fight over the same beam
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

        //the best lit rather than the nearest, because under a fan those are not the same thing -
        //something out at the tip on the centre line is more found than something off to one side
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

        //which side it is held from is settled here and then left alone. Worked out per frame from
        //wherever the beam currently is, the bearing would chase the beam that is chasing it, and
        //the light would crawl round the mote for the whole hold instead of stopping beside it
        lockBearing = Misc.getAngleInDegrees(best.getLocation(), currentRenderLoc);
        lockLoc.set(holdPointFor(best.getLocation()));
    }

    /**
     * Where the beam sits to hold something: beside it, by half the light's own radius.
     * <p>
     * Centred on the mote is where this used to put it, and that is the one place the dent cannot be
     * read - the middle of a beam is its brightest part, and the thing being looked at is a hole in
     * the light. Off to the side by half a radius it sits where the beam has fallen off enough to
     * show a shape, and still comfortably inside what the light counts as lit.
     */
    protected Vector2f holdPointFor(Vector2f target) {
        return MathUtils.getPointOnCircumference(target,
                getArea() * LOCK_HOLD_RADIUS_SHARE, lockBearing);
    }

    /**
     * A lens under the beam, following it. The light is looking through the fabric, so it ought to
     * bend what is behind it - and a distortion that moves with the light is what says the beam is
     * the thing doing the looking.
     */
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

        //moved rather than respawned - one lens travelling, not a trail of them left behind
        lens.setLocation(new Vector2f(currentRenderLoc));

        //the size is only here to pick up upgrades, and it has to keep off while the fade-in owns it
        if (!lens.isFading()) lens.setSize(size * LENS_SIZE_MULT);
    }

    public Vector2f getRenderLoc() {
        return currentRenderLoc;
    }

    /**
     * Where the light is thrown from, which is the fleet - the arc is drawn around it and holds its
     * own location vector, so this follows the fleet without anything having to be told.
     */
    public Vector2f getOrigin() {
        return arc == null ? currentRenderLoc : arc.center;
    }

    /** Whether the fan is fitted, which changes the shape of everything the light is doing. */
    public static boolean isFanned() {
        return TackleManager.get(Tackle.Fit.SEARCHLIGHT).fanBeam;
    }

    /**
     * How wide the fan opens either side of its aim, at the radius the light is currently bought to.
     * <p>
     * A bigger lamp throws a bigger fan. The reach already grew with the radius and the width did
     * not, so buying area made the wedge longer and thinner - the upgrade that is sold as covering
     * more sky was narrowing it. Scaled off the base radius so an un-upgraded rig is exactly the
     * fan it always was.
     */
    public static float getFanHalfAngle() {
        return FAN_HALF_ANGLE * (getArea() / AREA_FALLBACK);
    }

    /**
     * How lit a spot is by this light, from nothing to full.
     * <p>
     * The one place that answers what a light is touching. It used to be asked and answered
     * separately by the thing that draws the dents and the thing that decides what to stop on, both
     * assuming a circle - which is fine while a light is a circle, and wrong the moment one is a
     * fan. Asked here, a light that changes shape changes shape for everything at once.
     */
    public float getLitStrength(Vector2f at) {
        float size = getArea();

        if (!isFanned()) {
            float distance = Misc.getDistance(currentRenderLoc, at);
            if (distance > size) return 0f;

            float inBeam = 1f - MathUtils.clamp(distance / Math.max(1f, size), 0f, 1f);

            return inBeam * inBeam;
        }

        Vector2f origin = getOrigin();

        //the fan is aimed wherever the sweep is aimed, and reaches past the aim point by the beam's
        //own radius, so the two shapes cover the same ground and the reach the world is seeded
        //against still holds
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

        //squared across the fan so the edges are soft, and only leaned on down its length - a fan
        //that faded to nothing at the tip would be a fan that cannot find anything at its own reach
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

        //the other faces go the way the glow goes - the same light in different clothes
        if (breach != null) {
            breach.fadeAndExpire(fadeSeconds);
            breach = null;
        }
        if (fan != null) {
            fan.fadeAndExpire(fadeSeconds);
            fan = null;
        }

        rings.clear();

        //the lens has no fade of its own worth waiting on: it goes with the light
        if (lens != null) {
            CampaignDistortionRenderer.removeDistortion(lens);
            lens = null;
        }

        expired = true;
    }

}
