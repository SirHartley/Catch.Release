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
    public static final float INTERCEPT_SLOW_SECONDS = 5f;
    public static final float PULL_SECONDS = 3f;
    public static final float PULL_ACCEL = 850f;

    protected Kind kind = Kind.BLAST;
    protected float time;
    protected boolean triggered;
    protected float slowLeft;
    protected float pullLeft;
    protected boolean fading;

    protected transient SpriteAPI sprite;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        if (pluginParams instanceof Params params) kind = params.kind;
        time = (float) (Math.random() * 10f);
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
        time += amount;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != entity.getContainingLocation()) {
            return;
        }

        if (slowLeft > 0f) {
            slowLeft -= amount;
            player.goSlowOneFrame();
        }

        if (pullLeft > 0f) {
            pullLeft -= amount;

            Vector2f toMine = new Vector2f(entity.getLocation().x - player.getLocation().x,
                    entity.getLocation().y - player.getLocation().y);
            float length = toMine.length();
            if (length > 1f) {
                float pull = PULL_ACCEL * amount;
                player.getVelocity().set(player.getVelocity().x + toMine.x / length * pull,
                        player.getVelocity().y + toMine.y / length * pull);
            }
        }

        if (triggered) {
            if (!fading && slowLeft <= 0f && pullLeft <= 0f) {
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

                Vector2f away = new Vector2f(player.getLocation().x - entity.getLocation().x,
                        player.getLocation().y - entity.getLocation().y);
                float length = away.length();
                if (close && length > 1f) {
                    player.getVelocity().set(
                            player.getVelocity().x + away.x / length * BLAST_PUSH_SPEED,
                            player.getVelocity().y + away.y / length * BLAST_PUSH_SPEED);
                }
            }
            case INTERCEPT -> {
                explode(kind.color, BLAST_RADIUS * 0.55f);
                if (close) {
                    catchrelease.campaign.fish.legendary.InterdictionPulse.fire(player);
                    slowLeft = INTERCEPT_SLOW_SECONDS;
                }
            }
            case IMPLOSION -> {
                if (close) pullLeft = PULL_SECONDS;

                RippleDistortion ripple = new RippleDistortion(
                        new Vector2f(entity.getLocation()), new Vector2f());
                ripple.setSize(450f);
                ripple.setIntensity(90f);
                ripple.setFrameRate(60f);
                ripple.flip(true);
                ripple.setLifetime(PULL_SECONDS);
                ripple.fadeOutIntensity(PULL_SECONDS);
                CampaignDistortionRenderer.addDistortion(ripple);
            }
        }
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
        float blink = 0.15f + 0.85f * (0.5f + 0.5f * (float) Math.sin(time * rate));

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
