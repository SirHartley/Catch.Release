package catchrelease.campaign.fish.entities;

import catchrelease.rendering.distortion.CampaignDistortionRenderer;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin.ExplosionParams;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.distortion.RippleDistortion;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * The False Dawn's mines: blinking lights with three tempers. Red bursts and shoves the
 * fleet away, blue delivers an interdiction pulse, yellow implodes - no damage, just an
 * inward ripple and a pull toward where it was. None of them harm the hull.
 */
public class HauntMineEntityPlugin extends BaseCustomEntityPlugin {

    public enum Kind {

        BLAST(new Color(255, 90, 60), 10f),
        INTERCEPT(new Color(90, 150, 255), 8f),
        IMPLOSION(new Color(255, 210, 90), 6.5f);

        public final Color color;
        public final float blinkRate;

        Kind(Color color, float blinkRate) {
            this.color = color;
            this.blinkRate = blinkRate;
        }
    }

    public static class Params {

        public final Kind kind;

        public Params(Kind kind) {
            this.kind = kind;
        }
    }

    public static final String MINE_TAG = "catchrelease_haunt_mine";

    public static final float TRIGGER_RANGE = 400f;
    public static final float EFFECT_RANGE = 700f;
    public static final float ARM_SECONDS = 2f;
    public static final float GLOW_SIZE = 34f;
    public static final float PULSE_PERIOD = 2.2f;
    public static final float PULSE_SECONDS = 1.1f;

    public static final float BLAST_PUSH_SPEED = 700f;
    public static final float BLAST_RADIUS = 320f;
    public static final float INTERCEPT_STUN_SECONDS = 2f;
    public static final float IMPLOSION_RIPPLE_SECONDS = 3f;

    protected Kind kind = Kind.BLAST;
    protected float time;
    protected float blinkOffset;
    protected boolean triggered;
    protected float stunLeft;
    protected boolean fading;

    protected transient SpriteAPI sprite;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        if (pluginParams instanceof Params params) kind = params.kind;
        blinkOffset = (float) (Math.random() * 10f);
        entity.addTag(MINE_TAG);
    }

    // the pulse ring reaches the trigger radius; without this the base render range
    // clips it whenever the mine itself sits just off-screen
    @Override
    public float getRenderRange() {
        return TRIGGER_RANGE + 500f;
    }

    /** A harpoon strike sets it off from range: the full show, but the shove, the
     *  interdict and the pull only land on a fleet close enough to deserve them. */
    public void detonate() {
        if (triggered) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null
                || player.getContainingLocation() != entity.getContainingLocation()) {
            return;
        }

        triggered = true;
        fire(player, Misc.getDistance(player.getLocation(), entity.getLocation())
                <= EFFECT_RANGE);
    }

    @Override
    public void advance(float amount) {
        if (amount <= 0f || Global.getSector().isPaused()) return;
        time += amount;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != entity.getContainingLocation()) {
            return;
        }

        if (stunLeft > 0f) {
            stunLeft = Math.max(0f, stunLeft - amount);
            stopFleet(player);
        }

        if (triggered) {
            if (!fading && stunLeft <= 0f) {
                fading = true;
                Misc.fadeAndExpire(entity, 0.5f);
            }
            return;
        }

        if (time < ARM_SECONDS) return;
        if (Misc.getDistance(player.getLocation(), entity.getLocation()) > TRIGGER_RANGE) {
            return;
        }

        triggered = true;
        fire(player, true);
    }

    protected void fire(CampaignFleetAPI player, boolean close) {
        switch (kind) {
            case BLAST -> {
                explode(kind.color, BLAST_RADIUS);

                if (close) applyImpulse(player, BLAST_PUSH_SPEED);
            }
            case INTERCEPT -> {
                explode(kind.color, BLAST_RADIUS * 0.55f);
                if (close) {
                    catchrelease.campaign.fish.legendary.InterdictionPulse.fire(player);
                    stunLeft = INTERCEPT_STUN_SECONDS;
                    stopFleet(player);
                }
            }
            case IMPLOSION -> {
                if (close) applyImpulse(player, -BLAST_PUSH_SPEED);
                implode();
            }
        }
    }

    protected void stopFleet(CampaignFleetAPI player) {
        player.setVelocity(0f, 0f);
        player.setMoveDestination(player.getLocation().x, player.getLocation().y);
        player.goSlowOneFrame(true);
    }

    protected void applyImpulse(CampaignFleetAPI player, float speed) {
        Vector2f away = Vector2f.sub(player.getLocation(), entity.getLocation(), null);
        float distance = away.length();
        if (distance <= 1f) return;
        Vector2f velocity = player.getVelocityFromMovementModule();
        // getVelocity() is a displayed sample; setVelocity writes the movement module used by steering.
        player.setVelocity(velocity.x + away.x / distance * speed,
                velocity.y + away.y / distance * speed);
    }

    protected void implode() {
        RippleDistortion ripple = new RippleDistortion(
                new Vector2f(entity.getLocation()), new Vector2f());
        ripple.setSize(450f);
        ripple.setIntensity(90f);
        ripple.setFrameRate(60f);
        ripple.flip(true);
        ripple.setLifetime(IMPLOSION_RIPPLE_SECONDS);
        ripple.fadeOutIntensity(IMPLOSION_RIPPLE_SECONDS);
        CampaignDistortionRenderer.addDistortion(ripple);
    }

    protected void explode(Color color, float radius) {
        ExplosionParams params = new ExplosionParams(color, entity.getContainingLocation(),
                new Vector2f(entity.getLocation()), radius, 1f);
        params.damage = com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin
                .ExplosionFleetDamage.NONE;

        SectorEntityToken explosion = entity.getContainingLocation().addCustomEntity(
                Misc.genUID(), null, Entities.EXPLOSION, Factions.NEUTRAL, params);
        explosion.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        float alpha = viewport.getAlphaMult() * entity.getSensorFaderBrightness();
        if (alpha <= 0f || triggered) return;

        if (sprite == null) {
            sprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
            if (sprite == null) return;
        }

        // hard strobing, harder still once the fleet is close enough to matter
        float rate = kind.blinkRate;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player != null && Misc.getDistance(player.getLocation(),
                entity.getLocation()) < TRIGGER_RANGE * 2.5f) {
            rate *= 3f;
        }
        float blink = 0.15f + 0.85f * (0.5f + 0.5f * (float) Math.sin((time + blinkOffset) * rate));

        Vector2f loc = entity.getLocation();
        sprite.setColor(kind.color);
        sprite.setAdditiveBlend();

        float size = GLOW_SIZE * (0.85f + 0.3f * blink);
        for (int i = 0; i < 3; i++) {
            sprite.setSize(size, size);
            sprite.setAlphaMult(alpha * blink * (i == 0 ? 0.9f : 0.6f));
            sprite.renderAtCenter(loc.x, loc.y);
            size *= 0.45f;
        }

        // the position pulse: a ring breathing out to the trigger radius on a cycle,
        // so an armed mine's location and reach read from across the field
        float cycle = time % PULSE_PERIOD;
        if (time >= ARM_SECONDS && cycle < PULSE_SECONDS) {
            float p = cycle / PULSE_SECONDS;
            float fade = (1f - p) * alpha;
            Disc.drawOutline(loc.x, loc.y, TRIGGER_RANGE * p, kind.color, fade * 0.5f, 2f);
            Disc.drawOutline(loc.x, loc.y, TRIGGER_RANGE * p * 0.85f, kind.color,
                    fade * 0.25f, 1.2f);
        }
    }
}
