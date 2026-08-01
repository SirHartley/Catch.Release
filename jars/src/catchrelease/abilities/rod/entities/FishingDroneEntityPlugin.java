package catchrelease.abilities.rod.entities;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicCampaignTrailPlugin;

import java.awt.Color;

/**
 * One fishing drone.
 * <p>
 * On station it simply flies the circle - its angle advances and it sits on the ring, so the drones
 * stay evenly spaced with nothing to drift or lag. No orbiting mechanics involved.
 * <p>
 * Everywhere else it steers: it works out the velocity it would like and eases its actual velocity
 * towards that over {@link RodConstants#DRONE_STEER_RESPONSE} seconds, with a slow lateral wander on
 * top. That lag is what curves the paths - launching out to the ring, diving on a mote, and heading
 * home all come out as arcs rather than straight lines. Leaving the circle hands the tangent over as
 * the starting velocity, so a dive peels off the ring instead of turning on the spot.
 * <p>
 * The angle keeps advancing whatever the drone is doing, so one that launches or breaks off to chase
 * something rejoins where its share of the circle has got to, not where it left.
 * <p>
 * The drone owns its movement; {@link catchrelease.abilities.rod.scripts.FishingDroneSwarmScript}
 * owns the decisions and just says which of the four things it should be doing.
 */
public class FishingDroneEntityPlugin extends BaseCustomEntityPlugin {

    public static final String ENTITY_ID = "catchrelease_FishingDrone";

    public enum Mode {
        /** Flying out to join the circle, or rejoining it after a dive. */
        LAUNCHING,
        /** On the circle, flying it. */
        ORBITING,
        /** Diving on a mote inside the circle. */
        CHASING,
        /** On the way back to the fleet, with or without a catch. */
        RETURNING
    }

    public static class Params {
        public final Vector2f orbitCenter;
        public final float orbitAngle;
        public final Color color;

        /**
         * @param orbitAngle this drone's share of the ring, in degrees - the swarm spreads its drones
         *                   evenly so they do not fly on top of each other
         */
        public Params(Vector2f orbitCenter, float orbitAngle, Color color) {
            this.orbitCenter = orbitCenter;
            this.orbitAngle = orbitAngle;
            this.color = color;
        }
    }

    protected Mode mode = Mode.LAUNCHING;
    protected Vector2f orbitCenter;
    protected Color color;

    /** The circle's own rotation, and this drone's share of it. Its slot is the two added together. */
    protected float ringPhase;
    protected float slotOffset;

    /** Where the drone actually is in polar terms, eased towards its slot rather than snapped to it. */
    protected float currentAngle;
    protected float currentRadius = RodConstants.DRONE_ORBIT_RADIUS;

    /** Heading error carried over from joining the circle, bled off rather than snapped out. */
    protected float facingOffset;

    protected Vector2f velocity = new Vector2f();
    protected float wanderPhase;

    /** This trail's own id - segments sharing an id are strung together, so it must not be shared. */
    protected float trailId;

    /** The mote being run down, if any. */
    protected SectorEntityToken chaseTarget;

    /** The mote being carried home, if this drone caught something. */
    protected SectorEntityToken carried;

    protected boolean arrivedHome = false;

    protected final FlickerUtilV2 flicker = new FlickerUtilV2(0.4f);
    transient protected SpriteAPI sprite;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        Params p = (Params) pluginParams;
        this.orbitCenter = p.orbitCenter;
        this.slotOffset = p.orbitAngle;
        this.color = p.color;

        //so the drones do not all wander in step
        this.wanderPhase = MathUtils.getRandomNumberInRange(0f, (float) (Math.PI * 2f));

        this.trailId = MagicCampaignTrailPlugin.getUniqueID();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        flicker.advance(amount);

        //the circle turns whatever this drone is up to, so anything off it rejoins where its share
        //has got to rather than where it left
        ringPhase += RodConstants.DRONE_ORBIT_SPEED * amount;

        if (mode == Mode.CHASING && !isChaseTargetValid()) returnToOrbit();

        if (mode == Mode.ORBITING) {
            flyCircle(amount);
        } else {
            steerTowards(getGoal(), amount);
            applyVelocity(amount);

            if (mode == Mode.LAUNCHING && isOnTheRing()) joinCircle();
            if (mode == Mode.RETURNING) checkArrivedHome();
        }

        //a caught mote rides along rather than being left behind mid-pond
        if (carried != null && !carried.isExpired()) {
            carried.setLocation(entity.getLocation().x, entity.getLocation().y);
        }

        renderTrail();
    }

    /** Where this drone is trying to be right now. */
    protected Vector2f getGoal() {
        switch (mode) {
            case CHASING:
                return chaseTarget.getLocation();

            case RETURNING:
                SectorEntityToken fleet = Global.getSector().getPlayerFleet();
                return fleet == null ? entity.getLocation() : fleet.getLocation();

            default:
                //aim ahead of the slot, not at it, so the drone arrives already going the way the
                //circle goes - come in at the slot itself and it would meet the ring head-on and have
                //to turn ninety degrees on the spot, which is what tears a trail
                return MathUtils.getPointOnCircumference(orbitCenter, RodConstants.DRONE_ORBIT_RADIUS,
                        getSlotAngle() + RodConstants.DRONE_JOIN_LEAD_ANGLE);
        }
    }

    /** Where this drone's share of the circle currently is, in degrees. */
    public float getSlotAngle() {
        return ringPhase + slotOffset;
    }

    /** Where this drone's share of the circle currently is. */
    public Vector2f getOrbitSlot() {
        return MathUtils.getPointOnCircumference(orbitCenter, RodConstants.DRONE_ORBIT_RADIUS, getSlotAngle());
    }

    /**
     * Whether the drone is both on the ring and going the way the ring goes. Crossing it while headed
     * somewhere else does not count - taking up circle flight there would spin the drone on the spot.
     */
    protected boolean isOnTheRing() {
        float radius = Misc.getDistance(orbitCenter, entity.getLocation());
        if (Math.abs(radius - RodConstants.DRONE_ORBIT_RADIUS) > RodConstants.DRONE_JOIN_DISTANCE) return false;

        float tangent = Misc.getAngleInDegrees(orbitCenter, entity.getLocation()) + 90f;

        return Math.abs(getAngleDifference(getHeading(), tangent)) <= RodConstants.DRONE_JOIN_ALIGNMENT;
    }

    /**
     * Takes up circle flight from wherever the drone currently is - same position, same heading - so
     * there is nothing to snap. Angle and radius then ease to its actual slot, and whatever heading
     * error is left over is eased away rather than corrected in one frame.
     */
    protected void joinCircle() {
        currentAngle = Misc.getAngleInDegrees(orbitCenter, entity.getLocation());
        currentRadius = Misc.getDistance(orbitCenter, entity.getLocation());
        facingOffset = getAngleDifference(getHeading(), currentAngle + 90f);

        mode = Mode.ORBITING;
    }

    protected float getHeading() {
        return (float) Math.toDegrees(Math.atan2(velocity.y, velocity.x));
    }

    /** Signed degrees from b to a, the short way round. */
    protected static float getAngleDifference(float a, float b) {
        return ((a - b + 540f) % 360f) - 180f;
    }

    /**
     * Flies the circle, in polar terms: the angle chases this drone's slot and the radius chases the
     * ring, both eased, and the drone is placed wherever those two currently say. Even spacing falls
     * out of it within a second or two of joining, without anything ever being teleported.
     */
    protected void flyCircle(float amount) {
        float settle = 1f - (float) Math.exp(-amount / RodConstants.DRONE_SETTLE_RESPONSE);

        //fly the circle at the ring's own rate, then trim towards this drone's slot on top - easing
        //alone would leave every drone a fixed lag behind its slot, since the slot never stops moving
        currentAngle += RodConstants.DRONE_ORBIT_SPEED * amount;

        //shortest way round, so a drone never takes the long way to its own slot
        float trim = getAngleDifference(getSlotAngle(), currentAngle) * settle;

        //and capped, so closing a wide gap is a drift back into place over several seconds rather
        //than a sprint round the ring. Uncapped this doubles or triples how fast the drone is turning
        float cap = RodConstants.DRONE_TRIM_RATE * amount;
        currentAngle += Math.max(-cap, Math.min(cap, trim));
        currentRadius += (RodConstants.DRONE_ORBIT_RADIUS - currentRadius) * settle;

        Vector2f position = MathUtils.getPointOnCircumference(orbitCenter, currentRadius, currentAngle);
        entity.setLocation(position.x, position.y);

        //hand the tangent over as the working velocity, so a dive peels off the ring already moving
        float radians = (float) Math.toRadians(currentAngle);
        float tangential = (float) Math.toRadians(RodConstants.DRONE_ORBIT_SPEED) * currentRadius;

        velocity.set((float) -Math.sin(radians) * tangential, (float) Math.cos(radians) * tangential);

        //whatever heading the drone joined with, bled off over a fraction of a second instead of
        //being corrected in one frame - a frame-long turn is exactly what folds the trail
        facingOffset -= facingOffset * (1f - (float) Math.exp(-amount / RodConstants.DRONE_FACING_RESPONSE));

        entity.setFacing(currentAngle + 90f + facingOffset);
    }

    /**
     * Eases the drone's velocity towards the one that would take it to its goal - a proportional
     * controller with a time constant, so it accelerates in, banks through turns and settles instead
     * of snapping. Frame rate does not change the shape of the path.
     */
    protected void steerTowards(Vector2f goal, float amount) {
        Vector2f offset = Vector2f.sub(goal, entity.getLocation(), null);
        float distance = offset.length();

        //ease off on the approach rather than arriving at full tilt
        float speed = RodConstants.DRONE_SPEED;
        if (distance < RodConstants.DRONE_SLOWING_DISTANCE && RodConstants.DRONE_SLOWING_DISTANCE > 0f) {
            speed *= distance / RodConstants.DRONE_SLOWING_DISTANCE;
        }

        Vector2f desired = new Vector2f();

        if (distance > 0.001f) {
            offset.scale(1f / distance);
            desired.set(offset.x * speed, offset.y * speed);

            //two out-of-step sines: a drift that never quite repeats, and never jitters
            wanderPhase += amount * RodConstants.DRONE_NOISE_FREQUENCY;
            float wander = (float) (Math.sin(wanderPhase) + 0.5f * Math.sin(wanderPhase * 2.3f))
                    * RodConstants.DRONE_NOISE_STRENGTH;

            //push sideways, so the drift bends the path instead of changing how fast it gets there
            desired.x += -offset.y * wander;
            desired.y += offset.x * wander;
        }

        float response = 1f - (float) Math.exp(-amount / RodConstants.DRONE_STEER_RESPONSE);

        velocity.x += (desired.x - velocity.x) * response;
        velocity.y += (desired.y - velocity.y) * response;
    }

    protected void applyVelocity(float amount) {
        Vector2f loc = entity.getLocation();
        entity.setLocation(loc.x + velocity.x * amount, loc.y + velocity.y * amount);

        //the trail is laid out relative to facing - leaving it at zero is what makes a turning trail
        //fold across itself, since every segment comes out at the same angle no matter which way the
        //drone is actually going
        if (velocity.lengthSquared() > 1f) {
            entity.setFacing((float) Math.toDegrees(Math.atan2(velocity.y, velocity.x)));
        }
    }

    protected void checkArrivedHome() {
        SectorEntityToken fleet = Global.getSector().getPlayerFleet();

        if (fleet == null) {
            expire();
            return;
        }

        if (Misc.getDistance(entity.getLocation(), fleet.getLocation()) <= RodConstants.DRONE_ARRIVAL_DISTANCE) {
            expire();
        }
    }

    /** Send this one after a mote. */
    public void chase(SectorEntityToken mote) {
        this.chaseTarget = mote;
        this.mode = Mode.CHASING;
    }

    /** Back to the circle - flown into, not snapped to, since it may be a long way off it by now. */
    public void returnToOrbit() {
        this.chaseTarget = null;
        this.mode = Mode.LAUNCHING;
    }

    /** Send this one home. Pass the mote it caught, or null if it is coming back empty. */
    public void recall(SectorEntityToken carried) {
        this.carried = carried;
        this.chaseTarget = null;
        this.mode = Mode.RETURNING;
    }

    protected boolean isChaseTargetValid() {
        return chaseTarget != null && !chaseTarget.isExpired() && chaseTarget.isAlive();
    }

    protected void expire() {
        if (arrivedHome) return;
        arrivedHome = true;

        //the catch goes with it - the fish is landed, the mote has done its job
        if (carried != null && !carried.isExpired()) Misc.fadeAndExpire(carried, 0.5f);

        Misc.fadeAndExpire(entity, 0.5f);
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isOrbiting() {
        return mode == Mode.ORBITING;
    }

    /** Free to be sent after something: on the circle, or still on its way to it. */
    public boolean isAvailable() {
        return mode == Mode.ORBITING || mode == Mode.LAUNCHING;
    }

    public boolean isChasing() {
        return mode == Mode.CHASING;
    }

    public boolean isReturning() {
        return mode == Mode.RETURNING;
    }

    public SectorEntityToken getChaseTarget() {
        return chaseTarget;
    }

    public SectorEntityToken getCarried() {
        return carried;
    }

    protected void renderTrail() {
        //the trail builds each quad's corners at angle +/- 90, so the angle it wants is the direction
        //of travel itself. Handing it facing + 90 lays every quad across the path instead of along it,
        //which is what tore the trail apart on every turn
        MagicCampaignTrailPlugin.addTrailMemberSimple(
                entity,
                trailId,
                Global.getSettings().getSprite("catchrelease", "trail_foggy"),
                entity.getLocation(),
                0f,
                entity.getFacing(),
                RodConstants.DRONE_TRAIL_SIZE,
                1f,
                color,
                0.5f,
                0.5f,
                true,
                new Vector2f(0, 0));
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        if (sprite == null) sprite = SpriteLoader.getSprite("placeholder");
        if (sprite == null) return;

        float alpha = viewport.getAlphaMult()
                * entity.getSensorFaderBrightness()
                * entity.getSensorContactFaderBrightness();

        if (alpha <= 0f) return;

        Vector2f loc = entity.getLocation();

        sprite.setColor(color);
        sprite.setAlphaMult(alpha * (1f - 0.4f * flicker.getBrightness()));
        sprite.setSize(RodConstants.DRONE_SPRITE_SIZE, RodConstants.DRONE_SPRITE_SIZE);
        sprite.setAngle(entity.getFacing() - 90f);
        sprite.renderAtCenter(loc.x, loc.y);
    }
}
