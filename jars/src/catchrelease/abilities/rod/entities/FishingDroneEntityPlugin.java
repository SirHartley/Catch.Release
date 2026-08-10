package catchrelease.abilities.rod.entities;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
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
 * One fishing drone. On the ring it flies a fixed circle by angle; off it, it steers toward a goal
 * by easing velocity over {@link RodConstants#DRONE_STEER_RESPONSE} seconds, so paths curve rather
 * than snap. Leaving the circle carries the tangent over as starting velocity.
 * <p>
 * {@link #getOrbitCenter()} is read live (not cached) so a roaming drone's circle follows the
 * player fleet. {@link catchrelease.abilities.rod.scripts.FishingDroneSwarmScript} owns the
 * decisions; this class only owns movement.
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
        public final boolean roaming;

        /** @param orbitAngle this drone's share of the ring, in degrees */
        public Params(Vector2f orbitCenter, float orbitAngle, Color color) {
            this(orbitCenter, orbitAngle, color, false);
        }

        /** @param roaming if true, orbit follows the player fleet instead of the fixed {@code orbitCenter} */
        public Params(Vector2f orbitCenter, float orbitAngle, Color color, boolean roaming) {
            this.orbitCenter = orbitCenter;
            this.orbitAngle = orbitAngle;
            this.color = color;
            this.roaming = roaming;
        }
    }

    protected Mode mode = Mode.LAUNCHING;
    protected Vector2f orbitCenter;
    protected Color color;

    /** Whether the circle is flown around the fleet rather than around a fixed point. */
    protected boolean roaming;

    /** The circle's own rotation, and this drone's share of it. Its slot is the two added together. */
    protected float ringPhase;
    protected float slotOffset;

    /** Where the drone actually is in polar terms, eased towards its slot rather than snapped to it. */
    protected float currentAngle;
    protected float currentRadius;

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
        this.roaming = p.roaming;

        this.currentRadius = getOrbitRadius();

        //random start so drones don't wander in lockstep
        this.wanderPhase = MathUtils.getRandomNumberInRange(0f, (float) (Math.PI * 2f));

        this.trailId = MagicCampaignTrailPlugin.getUniqueID();
    }

    /** Middle of this drone's circle - live fleet location when roaming, else the fixed {@code orbitCenter}. */
    protected Vector2f getOrbitCenter() {
        if (!roaming) return orbitCenter;

        SectorEntityToken fleet = Global.getSector().getPlayerFleet();
        if (fleet != null) return fleet.getLocation();

        //no fleet and no fallback centre: hold current position rather than orbit the origin
        return orbitCenter == null ? entity.getLocation() : orbitCenter;
    }

    /** Orbit radius - wider when roaming, see {@link RodConstants#DRONE_ROAM_RADIUS}. */
    protected float getOrbitRadius() {
        return roaming ? RodConstants.DRONE_ROAM_RADIUS : RodConstants.DRONE_ORBIT_RADIUS;
    }

    /** Drone speed; re-read per frame so an upgrade purchase applies to drones already out. */
    protected float getSpeed() {
        return UpgradeManager.getValue(StatIds.DRONE_SPEED, RodConstants.DRONE_SPEED);
    }

    /** Turn responsiveness; same per-frame re-read as {@link #getSpeed()}. */
    protected float getSteerResponse() {
        return UpgradeManager.getValue(StatIds.DRONE_ACCELERATION, RodConstants.DRONE_STEER_RESPONSE);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        //ring keeps turning regardless of mode, so a drone rejoins at its current slot, not where it left
        ringPhase += RodConstants.DRONE_ORBIT_SPEED * amount;

        //speeds up the longer it's been returning; reset when not returning
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

        //carried mote rides along with the drone
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
                //aim ahead of the slot (lead angle) so the drone joins already moving with the ring,
                //instead of meeting it head-on and turning in place
                return MathUtils.getPointOnCircumference(getOrbitCenter(), getOrbitRadius(),
                        getSlotAngle() + RodConstants.DRONE_JOIN_LEAD_ANGLE);
        }
    }

    /** Where this drone's share of the circle currently is, in degrees. */
    public float getSlotAngle() {
        return ringPhase + slotOffset;
    }

    /** Where this drone's share of the circle currently is. */
    public Vector2f getOrbitSlot() {
        return MathUtils.getPointOnCircumference(getOrbitCenter(), getOrbitRadius(), getSlotAngle());
    }

    /** True only if on the ring AND heading the way the ring goes - just crossing it doesn't count. */
    protected boolean isOnTheRing() {
        Vector2f center = getOrbitCenter();

        float radius = Misc.getDistance(center, entity.getLocation());
        if (Math.abs(radius - getOrbitRadius()) > RodConstants.DRONE_JOIN_DISTANCE) return false;

        float tangent = Misc.getAngleInDegrees(center, entity.getLocation()) + 90f;

        return Math.abs(getAngleDifference(getHeading(), tangent)) <= RodConstants.DRONE_JOIN_ALIGNMENT;
    }

    /** Starts circle flight from the drone's current position/heading (no snap); angle, radius, and heading error then ease toward the slot. */
    protected void joinCircle() {
        Vector2f center = getOrbitCenter();

        currentAngle = Misc.getAngleInDegrees(center, entity.getLocation());
        currentRadius = Misc.getDistance(center, entity.getLocation());
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

    /** Flies the circle in polar terms: angle eases toward the slot, radius eases toward the ring radius; even spacing emerges within a second or two without teleporting. */
    protected void flyCircle(float amount) {
        float settle = 1f - (float) Math.exp(-amount / RodConstants.DRONE_SETTLE_RESPONSE);

        //advance at ring rate, then trim toward slot - pure easing would leave a fixed lag since the slot keeps moving
        currentAngle += RodConstants.DRONE_ORBIT_SPEED * amount;

        //shortest way round, so a drone never takes the long way to its own slot
        float trim = getAngleDifference(getSlotAngle(), currentAngle) * settle;

        //capped so closing a wide gap is a gradual drift, not a sprint - uncapped this can double/triple turn rate
        float cap = RodConstants.DRONE_TRIM_RATE * amount;
        currentAngle += Math.max(-cap, Math.min(cap, trim));
        currentRadius += (getOrbitRadius() - currentRadius) * settle;

        Vector2f position = MathUtils.getPointOnCircumference(getOrbitCenter(), currentRadius, currentAngle);
        entity.setLocation(position.x, position.y);

        //tangent becomes the working velocity, so a dive peels off the ring already moving
        float radians = (float) Math.toRadians(currentAngle);
        float tangential = (float) Math.toRadians(RodConstants.DRONE_ORBIT_SPEED) * currentRadius;

        velocity.set((float) -Math.sin(radians) * tangential, (float) Math.cos(radians) * tangential);

        //add centre's own velocity (e.g. fleet motion when roaming) - flying the ring only sets
        //velocity from the turn, so without this a peeling-off drone starts with the wrong velocity
        Vector2f carry = getCenterVelocity();
        if (carry != null) Vector2f.add(velocity, carry, velocity);

        //bleed off join heading error gradually - correcting it in one frame folds the trail
        facingOffset -= facingOffset * (1f - (float) Math.exp(-amount / RodConstants.DRONE_FACING_RESPONSE));

        entity.setFacing(currentAngle + 90f + facingOffset);
    }

    /** Eases velocity toward the goal via a proportional controller (frame-rate independent), producing accel/bank/settle instead of snapping. */
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

            //scaled by approach so drift fades near the goal instead of pushing off it
            float wander = (float) (Math.sin(wanderPhase) + 0.5f * Math.sin(wanderPhase * 2.3f))
                    * RodConstants.DRONE_NOISE_STRENGTH * approach;

            //push sideways, so the drift bends the path instead of changing how fast it gets there
            desired.x += -offset.y * wander;
            desired.y += offset.x * wander;
        }

        //match goal's own velocity - easing to a standstill would leave the drone trailing a moving fleet instead of arriving
        Vector2f goalVelocity = getGoalVelocity();
        if (goalVelocity != null) {
            desired.x += goalVelocity.x;
            desired.y += goalVelocity.y;
        }

        float response = 1f - (float) Math.exp(-amount / getSteerResponse());

        velocity.x += (desired.x - velocity.x) * response;
        velocity.y += (desired.y - velocity.y) * response;
    }

    /** Flight speed; flat except while returning, where it ramps up with {@link #returnTime}. */
    protected float getTravelSpeed() {
        if (mode != Mode.RETURNING) return getSpeed();

        float gain = Math.min(returnTime * RodConstants.DRONE_RETURN_ACCELERATION,
                RodConstants.DRONE_RETURN_MAX_MULT - 1f);

        return getSpeed() * (1f + gain);
    }

    /**
     * Speed to close the remaining distance. While returning, uses distance / (steer response *
     * {@link RodConstants#DRONE_BRAKE_MARGIN}), capped at travel speed - needed because wound-up
     * return speeds ({@link RodConstants#DRONE_RETURN_MAX_MULT}) chasing a fleet under burn would
     * otherwise overshoot and oscillate. Otherwise eases off linearly over
     * {@link RodConstants#DRONE_SLOWING_DISTANCE}.
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

    /** Velocity of the current goal to match speed with; null where it doesn't matter (motes are slow/caught on contact, ring slots aren't chased). */
    protected Vector2f getGoalVelocity() {
        //launching drone's slot circle may itself be moving (roaming) - match its velocity or it never converges
        if (mode == Mode.LAUNCHING) return getCenterVelocity();

        if (mode != Mode.RETURNING) return null;

        SectorEntityToken fleet = Global.getSector().getPlayerFleet();

        return fleet == null ? null : fleet.getVelocity();
    }

    /** What the middle of the circle is doing, or null when it is a spot on the water and does nothing. */
    protected Vector2f getCenterVelocity() {
        if (!roaming) return null;

        SectorEntityToken fleet = Global.getSector().getPlayerFleet();

        return fleet == null ? null : fleet.getVelocity();
    }

    public float getReturnTime() {
        return returnTime;
    }

    protected void applyVelocity(float amount) {
        Vector2f loc = entity.getLocation();
        entity.setLocation(loc.x + velocity.x * amount, loc.y + velocity.y * amount);

        //trail geometry is laid out relative to facing - must update it or a turning trail folds across itself
        if (velocity.lengthSquared() > 1f) {
            entity.setFacing((float) Math.toDegrees(Math.atan2(velocity.y, velocity.x)));
        }
    }

    protected void checkArrivedHome(float amount) {
        SectorEntityToken fleet = Global.getSector().getPlayerFleet();

        //no fleet, or fleet jumped to a different location - flying at coordinates in another system
        //would never arrive, so expire immediately instead
        if (fleet == null || fleet.getContainingLocation() != entity.getContainingLocation()) {
            expire();
            return;
        }

        //widen arrival radius to at least this frame's travel distance, or a fast drone can step over it entirely
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

    /** Send this one home. Pass the mote it caught, or null if it is coming back empty. A carried
     * mote is held for the whole return leg, which is the shared state every other retrieval rig
     * asks before taking it. */
    public void recall(SectorEntityToken carried) {
        if (this.carried != carried) setCarriedHeld(false);

        this.carried = carried;
        setCarriedHeld(true);
        this.chaseTarget = null;
        this.mode = Mode.RETURNING;
    }

    protected void setCarriedHeld(boolean held) {
        if (carried == null || !(carried.getCustomPlugin() instanceof FishEntityPlugin plugin)) {
            return;
        }

        plugin.setHeld(held);
    }

    protected boolean isChaseTargetValid() {
        return chaseTarget != null && !chaseTarget.isExpired() && chaseTarget.isAlive();
    }

    protected void expire() {
        if (arrivedHome) return;
        arrivedHome = true;

        //carried mote stays held through the fade - it remains this drone's delivery until gone
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
        //trail quads are built at angle +/-90 from what's passed in, so pass facing directly -
        //facing+90 lays quads across the path instead of along it
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
