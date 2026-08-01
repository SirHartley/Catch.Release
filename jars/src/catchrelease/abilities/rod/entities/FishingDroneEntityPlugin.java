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
 * One fishing drone. Flies out to the spot the rod was aimed at, holds a slot on the ring around it,
 * and eventually flies home - carrying a mote if it caught one.
 * <p>
 * The drone owns its movement; {@link catchrelease.abilities.rod.scripts.FishingDroneSwarmScript}
 * owns the decisions and just tells it which of the three things to be doing.
 */
public class FishingDroneEntityPlugin extends BaseCustomEntityPlugin {

    public static final String ENTITY_ID = "catchrelease_FishingDrone";

    public enum Mode {
        /** On the way out to the ring. */
        SEEKING,
        /** Holding station, going round. */
        ORBITING,
        /** On the way back to the fleet, with or without a catch. */
        RETURNING
    }

    public static class Params {
        public final Vector2f orbitCenter;
        public final float orbitAngle;
        public final Color color;

        /**
         * @param orbitAngle this drone's slot on the ring, in degrees - the swarm spreads its drones
         *                   evenly so they do not fly on top of each other
         */
        public Params(Vector2f orbitCenter, float orbitAngle, Color color) {
            this.orbitCenter = orbitCenter;
            this.orbitAngle = orbitAngle;
            this.color = color;
        }
    }

    protected Mode mode = Mode.SEEKING;
    protected Vector2f orbitCenter;
    protected float orbitAngle;
    protected Color color;

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
        this.orbitAngle = p.orbitAngle;
        this.color = p.color;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        flicker.advance(amount);
        renderTrail();

        switch (mode) {
            case SEEKING:
                advanceTowards(getOrbitSlot(), amount);
                if (Misc.getDistance(entity.getLocation(), getOrbitSlot()) <= RodConstants.DRONE_ARRIVAL_DISTANCE) {
                    mode = Mode.ORBITING;
                }
                break;

            case ORBITING:
                orbitAngle += RodConstants.DRONE_ORBIT_SPEED * amount;
                setLocation(getOrbitSlot());
                break;

            case RETURNING:
                advanceHome(amount);
                break;
        }

        //a caught mote rides along rather than being left behind mid-pond
        if (carried != null && !carried.isExpired()) {
            carried.setLocation(entity.getLocation().x, entity.getLocation().y);
        }
    }

    /** Where this drone's slot on the ring currently is. */
    public Vector2f getOrbitSlot() {
        return MathUtils.getPointOnCircumference(orbitCenter, RodConstants.DRONE_ORBIT_RADIUS, orbitAngle);
    }

    protected void advanceHome(float amount) {
        SectorEntityToken fleet = Global.getSector().getPlayerFleet();

        //nothing to fly back to - just go
        if (fleet == null) {
            expire();
            return;
        }

        advanceTowards(fleet.getLocation(), amount);

        if (Misc.getDistance(entity.getLocation(), fleet.getLocation()) <= RodConstants.DRONE_ARRIVAL_DISTANCE) {
            expire();
        }
    }

    protected void advanceTowards(Vector2f target, float amount) {
        float step = RodConstants.DRONE_SPEED * amount;
        float distance = Misc.getDistance(entity.getLocation(), target);

        if (step >= distance) {
            setLocation(target);
            return;
        }

        float angle = Misc.getAngleInDegrees(entity.getLocation(), target);
        setLocation(MathUtils.getPointOnCircumference(entity.getLocation(), step, angle));
    }

    protected void setLocation(Vector2f location) {
        entity.setLocation(location.x, location.y);
    }

    /** Send this one home. Pass the mote it caught, or null if it is coming back empty. */
    public void recall(SectorEntityToken carried) {
        this.carried = carried;
        this.mode = Mode.RETURNING;
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

    public boolean isReturning() {
        return mode == Mode.RETURNING;
    }

    public SectorEntityToken getCarried() {
        return carried;
    }

    protected void renderTrail() {
        MagicCampaignTrailPlugin.addTrailMemberSimple(
                entity,
                entity.getId().hashCode(),
                Global.getSettings().getSprite("catchrelease", "trail_foggy"),
                entity.getLocation(),
                0f,
                entity.getFacing() + 90f,
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
        sprite.renderAtCenter(loc.x, loc.y);
    }
}
