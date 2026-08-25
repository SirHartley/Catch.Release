package catchrelease.campaign.fish.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * A rock that is not there: drifts, turns, shimmers, and holds no collision, no salvage
 * and no interaction. Placeholder art - the translucent blue sprites under fx/ - until
 * real ghost-asteroid frames land.
 */
public class GhostAsteroidEntityPlugin
        extends com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin {

    public static final String SPRITE_PATH = "graphics/catchrelease/fx/ghost_asteroid_";
    public static final float FADE_SECONDS = 2.5f;

    public static class Params {

        public final int variant;
        public final float size;
        public final float spinDegPerSecond;
        public final Vector2f drift;

        public Params(int variant, float size, float spinDegPerSecond, Vector2f drift) {
            this.variant = variant;
            this.size = size;
            this.spinDegPerSecond = spinDegPerSecond;
            this.drift = drift;
        }
    }

    protected Params params;
    protected transient SpriteAPI sprite;

    protected float angle;
    protected float time;
    protected float fadeIn = 0f;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        // the base keeps the entity reference getRenderRange() reads; shadowing it
        // with a local field leaves the base's copy null and crashes the first render
        super.init(entity, pluginParams);

        this.params = (Params) pluginParams;
        this.angle = (float) (Math.random() * 360f);
        this.time = (float) (Math.random() * 100f);
    }

    @Override
    public void advance(float amount) {
        if (params == null) return;

        time += amount;
        fadeIn = Math.min(1f, fadeIn + amount / FADE_SECONDS);
        angle += params.spinDegPerSecond * amount;

        Vector2f at = entity.getLocation();
        entity.setLocation(at.x + params.drift.x * amount, at.y + params.drift.y * amount);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (params == null) return;

        if (sprite == null) {
            sprite = Global.getSettings().getSprite(
                    SPRITE_PATH + Math.max(1, Math.min(3, params.variant)) + ".png");
            if (sprite == null) return;
        }

        // slow shimmer on top of the sprite's own translucency: never fully solid
        float shimmer = 0.65f + 0.35f * (float) Math.sin(time * 0.9f);
        float alpha = fadeIn * (0.45f + 0.35f * shimmer);

        sprite.setSize(params.size, params.size);
        sprite.setAngle(angle);
        sprite.setColor(Color.WHITE);
        sprite.setAlphaMult(alpha);
        sprite.setAdditiveBlend();
        sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
    }
}
