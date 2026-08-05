package catchrelease.abilities.rod.entities;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.skillshot.SkillshotFramework;
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
import java.util.logging.Logger;

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

    /** How long this drone has been on its way home, which is what winds its speed up. */
    protected float returnTime = 0f;

    /** This trail's own id - segments sharing an id are strung together, so it must not be shared. */
    protected float trailId;

    /** The mote being run down, if any. */
    protected SectorEntityToken chaseTarget;

    /** The mote being carried home, if this drone caught something. */
    protected SectorEntityToken carried;

    protected boolean arrivedHome = false;

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

    /** The drone's own speed, upgraded. Read per frame so a purchase applies to drones already out. */
    protected float getSpeed() {
        return UpgradeManager.getValue(StatIds.DRONE_SPEED, RodConstants.DRONE_SPEED);
    }

    /** How hard it can change direction. Same story. */
    protected float getSteerResponse() {
        return UpgradeManager.getValue(StatIds.DRONE_ACCELERATION, RodConstants.DRONE_STEER_RESPONSE);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        //the circle turns whatever this drone is up to, so anything off it rejoins where its share
        //has got to rather than where it left
        ringPhase += RodConstants.DRONE_ORBIT_SPEED * amount;

        //a drone on its way home winds up the longer it has been running, so a long haul back does
        //not crawl - reset the moment it is doing anything else
        returnTime = mode == Mode.RETURNING ? returnTime + amount : 0f;

        if (mode == Mode.CHASING && !isChaseTargetValid()) returnToOrbit();

        if (mode == Mode.ORBITING) {
            flyCircle(amount);
        } else {
            steerTowards(getGoal(), amount);
            applyVelocity(amount);

            if (mode == Mode.LAUNCHING && isOnTheRing()) joinCircle();
            if (mode == Mode.RETURNING) checkArrivedHome(amount);
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
        float speed = getApproachSpeed(distance);

        //0 at the goal, 1 while still going flat out - what is left of the approach
        float approach = Math.min(1f, speed / Math.max(1f, getTravelSpeed()));

        Vector2f desired = new Vector2f();

        if (distance > 0.001f) {
            offset.scale(1f / distance);
            desired.set(offset.x * speed, offset.y * speed);

            //two out-of-step sines: a drift that never quite repeats, and never jitters
            wanderPhase += amount * RodConstants.DRONE_NOISE_FREQUENCY;

            //eased out along with the speed: at full strength the drift is a sizeable fraction of
            //what is left of the approach, and it pushes the drone off the goal it is settling onto
            float wander = (float) (Math.sin(wanderPhase) + 0.5f * Math.sin(wanderPhase * 2.3f))
                    * RodConstants.DRONE_NOISE_STRENGTH * approach;

            //push sideways, so the drift bends the path instead of changing how fast it gets there
            desired.x += -offset.y * wander;
            desired.y += offset.x * wander;
        }

        //settle onto what the goal is doing rather than onto a standstill - easing to a stop on a
        //fleet under way leaves the drone trailing it at whatever distance the two speeds match at,
        //which is short of arriving
        Vector2f goalVelocity = getGoalVelocity();
        if (goalVelocity != null) {
            desired.x += goalVelocity.x;
            desired.y += goalVelocity.y;
        }

        float response = 1f - (float) Math.exp(-amount / getSteerResponse());

        velocity.x += (desired.x - velocity.x) * response;
        velocity.y += (desired.y - velocity.y) * response;
    }

    /**
     * Flight speed. Flat everywhere except on the way home, where it builds with how long the drone
     * has been running for - a drone recalled from across the pond should be moving by the time it
     * gets there, not still ambling.
     */
    protected float getTravelSpeed() {
        if (mode != Mode.RETURNING) return getSpeed();

        float gain = Math.min(returnTime * RodConstants.DRONE_RETURN_ACCELERATION,
                RodConstants.DRONE_RETURN_MAX_MULT - 1f);

        return getSpeed() * (1f + gain);
    }

    /**
     * How fast to be going with this much left to cover.
     * <p>
     * A returning drone works it out from the gap rather than from a fixed distance: it asks for the
     * closing speed that would cover what is left over {@link RodConstants#DRONE_BRAKE_MARGIN} of
     * its steering response, capped at what it can actually do. A drone wound up to
     * {@link RodConstants#DRONE_RETURN_MAX_MULT} closes on a fleet several times faster than the
     * flat distance was tuned for, and the fleet it is chasing is usually burning away from it -
     * both of which had it swinging past and coming round again.
     * <p>
     * The speed asked for here is only ever eased onto, never taken up at once, and the margin is
     * what settles whether those two together arrive or oscillate - see
     * {@link RodConstants#DRONE_BRAKE_MARGIN}. Reading this on its own and picking a margin that
     * looks brisk is how it ended up overshooting.
     * <p>
     * Everything else keeps the flat ease-off: those fly at {@link RodConstants#DRONE_SPEED}, which
     * is the speed that distance was tuned against.
     */
    protected float getApproachSpeed(float distance) {
        float speed = getTravelSpeed();

        if (mode == Mode.RETURNING) {
            return Math.min(speed,
                    distance / (getSteerResponse() * RodConstants.DRONE_BRAKE_MARGIN));
        }

        if (distance >= RodConstants.DRONE_SLOWING_DISTANCE) {
            return speed;
        }

        return speed * distance / RodConstants.DRONE_SLOWING_DISTANCE;
    }

    /**
     * What the thing being flown at is doing, for goals worth matching speed with - null for the
     * ones where it makes no odds. Motes drift slowly and get caught on contact, and a slot on the
     * ring is somewhere to be rather than something to keep pace with.
     */
    protected Vector2f getGoalVelocity() {
        if (mode != Mode.RETURNING) return null;

        SectorEntityToken fleet = Global.getSector().getPlayerFleet();

        return fleet == null ? null : fleet.getVelocity();
    }

    public float getReturnTime() {
        return returnTime;
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

    protected void checkArrivedHome(float amount) {
        SectorEntityToken fleet = Global.getSector().getPlayerFleet();

        if (fleet == null) {
            expire();
            return;
        }

        //at least this frame's worth of travel: a drone moving faster than the arrival radius per
        //frame would otherwise step straight over it without ever being inside it
        float arrival = Math.max(RodConstants.DRONE_ARRIVAL_DISTANCE, velocity.length() * amount);

        if (Misc.getDistance(entity.getLocation(), fleet.getLocation()) <= arrival) expire();
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
                10f,
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

        if (sprite == null) sprite = SpriteLoader.getSprite("drone");
        if (sprite == null) return;

        float alpha = viewport.getAlphaMult()
                * entity.getSensorFaderBrightness()
                * entity.getSensorContactFaderBrightness();

        if (alpha <= 0f) return;

        Vector2f loc = entity.getLocation();

        sprite.setWidth(RodConstants.DRONE_SPRITE_SIZE * sprite.getTexWidth());
        sprite.setHeight(RodConstants.DRONE_SPRITE_SIZE * sprite.getTexHeight());
        sprite.setAngle(entity.getFacing() - 90f);
        sprite.renderAtCenter(loc.x, loc.y);
    }
}
