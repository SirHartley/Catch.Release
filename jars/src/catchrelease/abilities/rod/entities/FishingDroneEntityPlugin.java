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

public class FishingDroneEntityPlugin extends BaseCustomEntityPlugin {

    public enum Mode {

        LAUNCHING,
        ORBITING,
        CHASING,
        RETURNING
    }

    public static final String ENTITY_ID = "catchrelease_FishingDrone";

    protected Mode mode = Mode.LAUNCHING;

    protected Vector2f orbitCenter;
    protected Color color;
    protected boolean roaming;
    protected float ringPhase;
    protected float slotOffset;
    protected float currentAngle;
    protected float currentRadius;

    protected float facingOffset;
    protected Vector2f velocity = new Vector2f();
    protected float wanderPhase;

    protected float returnTime = 0f;
    protected float chaseTime = 0f;

    protected float trailId;

    protected SectorEntityToken chaseTarget;
    protected SectorEntityToken carried;
    protected boolean arrivedHome = false;

    transient protected SpriteAPI sprite;

    public static class Params {

        public final Vector2f orbitCenter;
        public final float orbitAngle;

        public final Color color;
        public final boolean roaming;

        public Params(Vector2f orbitCenter, float orbitAngle, Color color) {
            this(orbitCenter, orbitAngle, color, false);
        }

        public Params(Vector2f orbitCenter, float orbitAngle, Color color, boolean roaming) {
            this.orbitCenter = orbitCenter;
            this.orbitAngle = orbitAngle;
            this.color = color;
            this.roaming = roaming;
        }
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        Params p = (Params) pluginParams;
        this.orbitCenter = p.orbitCenter;
        this.slotOffset = p.orbitAngle;
        this.color = p.color;
        this.roaming = p.roaming;

        this.currentRadius = getOrbitRadius();

        this.wanderPhase = MathUtils.getRandomNumberInRange(0f, (float) (Math.PI * 2f));

        this.trailId = MagicCampaignTrailPlugin.getUniqueID();
    }

    protected Vector2f getOrbitCenter() {
        if (!roaming) return orbitCenter;

        SectorEntityToken fleet = Global.getSector().getPlayerFleet();
        if (fleet != null) return fleet.getLocation();

        // no fleet and no fallback centre: hold current position rather than orbit the origin
        return orbitCenter == null ? entity.getLocation() : orbitCenter;
    }

    protected float getOrbitRadius() {
        return roaming ? RodConstants.DRONE_ROAM_RADIUS : RodConstants.DRONE_ORBIT_RADIUS;
    }

    protected float getSpeed() {
        return UpgradeManager.getValue(StatIds.DRONE_SPEED, RodConstants.DRONE_SPEED);
    }

    protected float getSteerResponse() {
        return UpgradeManager.getValue(StatIds.DRONE_ACCELERATION, RodConstants.DRONE_STEER_RESPONSE);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        // ring keeps turning regardless of mode, so a drone rejoins at its current slot, not where it left
        ringPhase += RodConstants.DRONE_ORBIT_SPEED * amount;

        // speeds up the longer it's been returning; reset when not returning
        returnTime = mode == Mode.RETURNING ? returnTime + amount : 0f;

        if (mode == Mode.CHASING) {
            chaseTime += amount;

            float limit = UpgradeManager.getValue(
                    StatIds.DRONE_CHASE_TIME, RodConstants.CHASE_TIME_FALLBACK);
            if (!isChaseTargetValid() || (limit > 0f && chaseTime >= limit)) returnToOrbit();
        }

        if (mode == Mode.ORBITING) {
            flyCircle(amount);
        } else {
            steerTowards(getGoal(), amount);
            applyVelocity(amount);

            if (mode == Mode.LAUNCHING && isOnTheRing()) joinCircle();
            if (mode == Mode.RETURNING) checkArrivedHome(amount);
        }

        if (carried != null && !carried.isExpired()) {
            carried.setLocation(entity.getLocation().x, entity.getLocation().y);
        }

        renderTrail();
    }

    protected Vector2f getGoal() {
        switch (mode) {
            case CHASING:
                return chaseTarget.getLocation();

            case RETURNING:
                SectorEntityToken fleet = Global.getSector().getPlayerFleet();
                return fleet == null ? entity.getLocation() : fleet.getLocation();

            default:
                // aim ahead of the slot (lead angle) so the drone joins already moving with the ring, instead of meeting it head-on and turning in place
                return MathUtils.getPointOnCircumference(getOrbitCenter(), getOrbitRadius(),
                        getSlotAngle() + RodConstants.DRONE_JOIN_LEAD_ANGLE);
        }
    }

    public float getSlotAngle() {
        return ringPhase + slotOffset;
    }

    public Vector2f getOrbitSlot() {
        return MathUtils.getPointOnCircumference(getOrbitCenter(), getOrbitRadius(), getSlotAngle());
    }

    protected boolean isOnTheRing() {
        Vector2f center = getOrbitCenter();

        float radius = Misc.getDistance(center, entity.getLocation());
        if (Math.abs(radius - getOrbitRadius()) > RodConstants.DRONE_JOIN_DISTANCE) return false;

        float tangent = Misc.getAngleInDegrees(center, entity.getLocation()) + 90f;

        return Math.abs(getAngleDifference(getHeading(), tangent)) <= RodConstants.DRONE_JOIN_ALIGNMENT;
    }

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

    protected static float getAngleDifference(float a, float b) {
        return ((a - b + 540f) % 360f) - 180f;
    }

    protected void flyCircle(float amount) {
        float settle = 1f - (float) Math.exp(-amount / RodConstants.DRONE_SETTLE_RESPONSE);

        currentAngle += RodConstants.DRONE_ORBIT_SPEED * amount;

        // shortest way round, so a drone never takes the long way to its own slot
        float trim = getAngleDifference(getSlotAngle(), currentAngle) * settle;

        // capped so closing a wide gap is a gradual drift, not a sprint - uncapped this can double/triple turn rate
        float cap = RodConstants.DRONE_TRIM_RATE * amount;
        currentAngle += Math.max(-cap, Math.min(cap, trim));
        currentRadius += (getOrbitRadius() - currentRadius) * settle;

        Vector2f position = MathUtils.getPointOnCircumference(getOrbitCenter(), currentRadius, currentAngle);
        entity.setLocation(position.x, position.y);

        float radians = (float) Math.toRadians(currentAngle);
        float tangential = (float) Math.toRadians(RodConstants.DRONE_ORBIT_SPEED) * currentRadius;

        velocity.set((float) -Math.sin(radians) * tangential, (float) Math.cos(radians) * tangential);

        Vector2f carry = getCenterVelocity();
        if (carry != null) Vector2f.add(velocity, carry, velocity);

        // bleed off join heading error gradually - correcting it in one frame folds the trail
        facingOffset -= facingOffset * (1f - (float) Math.exp(-amount / RodConstants.DRONE_FACING_RESPONSE));

        entity.setFacing(currentAngle + 90f + facingOffset);
    }

    protected void steerTowards(Vector2f goal, float amount) {
        Vector2f offset = Vector2f.sub(goal, entity.getLocation(), null);
        float distance = offset.length();

        // ease off on the approach rather than arriving at full tilt
        float speed = getApproachSpeed(distance);

        float approach = Math.min(1f, speed / Math.max(1f, getTravelSpeed()));

        Vector2f desired = new Vector2f();

        if (distance > 0.001f) {
            offset.scale(1f / distance);
            desired.set(offset.x * speed, offset.y * speed);

            // two out-of-step sines: a drift that never quite repeats, and never jitters
            wanderPhase += amount * RodConstants.DRONE_NOISE_FREQUENCY;

            // scaled by approach so drift fades near the goal instead of pushing off it
            float wander = (float) (Math.sin(wanderPhase) + 0.5f * Math.sin(wanderPhase * 2.3f))
                    * RodConstants.DRONE_NOISE_STRENGTH * approach;

            // push sideways, so the drift bends the path instead of changing how fast it gets there
            desired.x += -offset.y * wander;
            desired.y += offset.x * wander;
        }

        // match goal's own velocity - easing to a standstill would leave the drone trailing a moving fleet instead of arriving
        Vector2f goalVelocity = getGoalVelocity();
        if (goalVelocity != null) {
            desired.x += goalVelocity.x;
            desired.y += goalVelocity.y;
        }

        float response = 1f - (float) Math.exp(-amount / getSteerResponse());

        velocity.x += (desired.x - velocity.x) * response;
        velocity.y += (desired.y - velocity.y) * response;
    }

    protected float getTravelSpeed() {
        if (mode != Mode.RETURNING) return getSpeed();

        float gain = Math.min(returnTime * RodConstants.DRONE_RETURN_ACCELERATION,
                RodConstants.DRONE_RETURN_MAX_MULT - 1f);

        return getSpeed() * (1f + gain);
    }

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

    protected Vector2f getGoalVelocity() {
        // launching drone's slot circle may itself be moving (roaming) - match its velocity or it never converges
        if (mode == Mode.LAUNCHING) return getCenterVelocity();

        if (mode != Mode.RETURNING) return null;

        SectorEntityToken fleet = Global.getSector().getPlayerFleet();

        return fleet == null ? null : fleet.getVelocity();
    }

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

        // trail geometry is laid out relative to facing - must update it or a turning trail folds across itself
        if (velocity.lengthSquared() > 1f) {
            entity.setFacing((float) Math.toDegrees(Math.atan2(velocity.y, velocity.x)));
        }
    }

    protected void checkArrivedHome(float amount) {
        SectorEntityToken fleet = Global.getSector().getPlayerFleet();

        // no fleet, or fleet jumped to a different location - flying at coordinates in another system would never arrive, so expire immediately instead
        if (fleet == null || fleet.getContainingLocation() != entity.getContainingLocation()) {
            expire();
            return;
        }

        // widen arrival radius to at least this frame's travel distance, or a fast drone can step over it entirely
        float arrival = Math.max(RodConstants.DRONE_ARRIVAL_DISTANCE, velocity.length() * amount);

        if (Misc.getDistance(entity.getLocation(), fleet.getLocation()) <= arrival) expire();
    }

    public void chase(SectorEntityToken mote) {
        boolean isNewTarget = mote != null && mote != chaseTarget;
        boolean shouldReportLock = isNewTarget && mode == Mode.ORBITING;

        if (isNewTarget || mode != Mode.CHASING) chaseTime = 0f;

        this.chaseTarget = mote;
        this.mode = Mode.CHASING;

        if (shouldReportLock) {
            Global.getSoundPlayer().playSound(RodConstants.SOUND_TARGET_LOCK, 1f, 1f,
                    entity.getLocation(), velocity);
        }
    }

    public void returnToOrbit() {
        this.chaseTarget = null;
        this.chaseTime = 0f;
        this.mode = Mode.LAUNCHING;
    }

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

        if (carried != null && !carried.isExpired()) Misc.fadeAndExpire(carried, 0.5f);

        Misc.fadeAndExpire(entity, 0.5f);
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isOrbiting() {
        return mode == Mode.ORBITING;
    }

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
        // trail quads are built at angle +/-90 from what's passed in, so pass facing directly - facing+90 lays quads across the path instead of along it
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
