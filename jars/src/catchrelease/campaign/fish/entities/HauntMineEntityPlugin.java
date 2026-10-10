package catchrelease.campaign.fish.entities;

import catchrelease.campaign.fish.legendary.DawnShieldTransfer;
import catchrelease.campaign.fish.legendary.LegendaryShields;
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

public class HauntMineEntityPlugin extends BaseCustomEntityPlugin {

    public enum Kind {

        BLAST(new Color(255, 90, 60), 10f),
        SHIELD(LegendaryShields.SHIELD_GREEN, 8f),
        IMPLOSION(new Color(255, 210, 90), 6.5f);

        public final Color color;
        public final float blinkRate;

        Kind(Color color, float blinkRate) {
            this.color = color;
            this.blinkRate = blinkRate;
        }
    }

    public static final String MINE_TAG = "catchrelease_haunt_mine";

    public static final float TRIGGER_RANGE = 400f;
    public static final float EFFECT_RANGE = 700f;
    public static final float ARM_SECONDS = 2f;
    public static final float LIFETIME_SECONDS = 10f;
    public static final float GLOW_SIZE = 34f;
    public static final float PULSE_PERIOD = 2.2f;
    public static final float PULSE_SECONDS = 1.1f;

    public static final float BLAST_PUSH_SPEED = 700f;
    public static final float BLAST_RADIUS = 320f;
    public static final float IMPLOSION_RIPPLE_SECONDS = 3f;
    private static final float NOTICE_SECONDS = 2f;

    protected Kind kind = Kind.BLAST;
    protected float time;
    protected float blinkOffset;
    protected boolean triggered;
    protected DawnShieldTransfer transfer;
    protected float noticeLeft;

    protected transient SpriteAPI sprite;

    public static class Params {

        public final Kind kind;

        public Params(Kind kind) {
            this.kind = kind;
        }
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        if (pluginParams instanceof Params params) kind = params.kind;
        blinkOffset = (float) (Math.random() * 10f);
        entity.addTag(MINE_TAG);
    }

    @Override
    public float getRenderRange() {
        return Math.max(TRIGGER_RANGE + 500f, transfer == null ? 0f : transfer.getRenderRange());
    }

    public void detonate() {
        trigger(false);
    }

    protected void trigger(boolean collision) {
        if (triggered || entity.isExpired()) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null
                || player.getContainingLocation() != entity.getContainingLocation()) {
            return;
        }

        triggered = true;
        entity.removeTag(MINE_TAG);
        fire(player, Misc.getDistance(player.getLocation(), entity.getLocation())
                <= EFFECT_RANGE, collision);
    }

    @Override
    public void advance(float amount) {
        if (amount <= 0f || Global.getSector().isPaused() || entity.isExpired()) return;
        time += amount;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != entity.getContainingLocation()) {
            return;
        }

        if (triggered) {
            noticeLeft = Math.max(0f, noticeLeft - amount);
            if (transfer != null && transfer.advance(amount)) {
                LegendaryShields.say(entity, "The False Dawn brightens");
                // Vanilla keeps this notice for one second, then fades it for half a second.
                noticeLeft = NOTICE_SECONDS;
            }
            if ((transfer == null || transfer.isDone()) && noticeLeft <= 0f) entity.setExpired(true);
            return;
        }

        if (time >= LIFETIME_SECONDS) {
            detonate();
            return;
        }
        if (time < ARM_SECONDS) return;
        if (Misc.getDistance(player.getLocation(), entity.getLocation()) > TRIGGER_RANGE) {
            return;
        }

        trigger(true);
    }

    protected void fire(CampaignFleetAPI player, boolean close, boolean collision) {
        switch (kind) {
            case BLAST -> {
                explode(kind.color, BLAST_RADIUS);

                if (close) applyImpulse(player, BLAST_PUSH_SPEED);
            }
            case SHIELD -> {
                explode(kind.color, BLAST_RADIUS * 0.55f);
                if (collision) transfer = new DawnShieldTransfer(entity);
            }
            case IMPLOSION -> {
                if (close) applyImpulse(player, -BLAST_PUSH_SPEED);
                implode();
            }
        }
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
        if (entity.isExpired()) return;
        if (triggered) {
            if (transfer != null) transfer.render(viewport);
            return;
        }
        float alpha = viewport.getAlphaMult() * entity.getSensorFaderBrightness();
        if (alpha <= 0f) return;

        if (sprite == null) {
            sprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
            if (sprite == null) return;
        }

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

        // The pulse shows the trigger radius.
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
