package catchrelease.campaign.ponds.renderer;

import catchrelease.campaign.ponds.constants.PondConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class PondDepthField {
    protected final Particle[] particles;
    protected transient SpriteAPI sprite;
    protected float time = 0f;

    protected static class Particle {
        float depth;
        float angle;
        float radius;
        float size;
        float phase;
        float spin;
        float bob;
        float bobRate;
    }

    public PondDepthField() {
        particles = new Particle[PondConstants.DEPTH_PARTICLES];

        for (int i = 0; i < particles.length; i++) {
            particles[i] = spawn(true);
        }
    }

    protected Particle spawn(boolean anywhere) {
        Particle p = new Particle();

        p.angle = MathUtils.getRandomNumberInRange(0f, 360f);

        if (anywhere) {
            p.depth = MathUtils.getRandomNumberInRange(0f, 1f);

            p.radius = (float) Math.sqrt(MathUtils.getRandomNumberInRange(0f, 1f));
        } else {
            p.depth = MathUtils.getRandomNumberInRange(0.85f, 1f);
            p.radius = MathUtils.getRandomNumberInRange(0.92f, 1.05f);
        }

        p.size = MathUtils.getRandomNumberInRange(0.6f, 1.4f);
        p.phase = MathUtils.getRandomNumberInRange(0f, 6.283f);

        p.spin = MathUtils.getRandomNumberInRange(PondConstants.DEPTH_SPIN_MIN, PondConstants.DEPTH_SPIN_MAX)
                * (MathUtils.getRandomNumberInRange(0f, 1f) < PondConstants.DEPTH_COUNTER_SHARE ? -1f : 1f);

        p.bob = MathUtils.getRandomNumberInRange(0.05f, PondConstants.DEPTH_BOB);
        p.bobRate = MathUtils.getRandomNumberInRange(0.15f, 0.6f);

        return p;
    }

    public void advance(float amount) {
        time += amount;

        for (int i = 0; i < particles.length; i++) {
            Particle p = particles[i];
            float mult = getSpeedMult(p);

            float vortex = 1f + PondConstants.DEPTH_SWIRL_BOOST
                    * (1f - MathUtils.clamp(p.radius, 0f, 1f));

            p.angle += p.spin * mult * vortex * amount;
            if (p.angle > 360f) p.angle -= 360f;
            if (p.angle < 0f) p.angle += 360f;

            p.radius -= PondConstants.DEPTH_SINK_RADIUS * mult * amount;
            p.depth = MathUtils.clamp(p.depth - PondConstants.DEPTH_SINK_DEPTH * amount, 0f, 1f);

            if (p.radius <= PondConstants.DEPTH_DRAIN || p.depth <= 0.02f) {
                particles[i] = spawn(false);
            }
        }
    }

    protected float getSpeedMult(Particle p) {
        return PondConstants.DEPTH_SPEED_FLOOR
                + (1f - PondConstants.DEPTH_SPEED_FLOOR) * getDepth(p);
    }

    protected float getDepth(Particle p) {
        float bobbed = p.depth + (float) Math.sin(time * p.bobRate + p.phase) * p.bob;

        return MathUtils.clamp(bobbed, 0f, 1f);
    }

    public void render(Vector2f center, float pondRadius, float alphaMult) {
        if (alphaMult <= 0f || pondRadius <= 0f) return;

        if (sprite == null) sprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        sprite.setAdditiveBlend();

        for (Particle p : particles) {
            float depth = getDepth(p);

            // deep particles sit nearer the middle too, so the rupture reads as a narrowing well rather than a cylinder
            float reach = PondConstants.DEPTH_REACH_FLOOR
                    + (1f - PondConstants.DEPTH_REACH_FLOOR) * depth;

            double radians = Math.toRadians(p.angle);
            float distance = p.radius * reach * pondRadius * PondConstants.DEPTH_FILL;

            float x = center.x + (float) Math.cos(radians) * distance;
            float y = center.y + (float) Math.sin(radians) * distance;

            float size = p.size * (PondConstants.DEPTH_SIZE_MIN
                    + (PondConstants.DEPTH_SIZE_MAX - PondConstants.DEPTH_SIZE_MIN) * depth);

            float alpha = alphaMult * (PondConstants.DEPTH_ALPHA_MIN
                    + (PondConstants.DEPTH_ALPHA_MAX - PondConstants.DEPTH_ALPHA_MIN) * depth);

            if (alpha <= 0f) continue;

            sprite.setColor(getColor(depth));
            sprite.setSize(size, size);
            sprite.setAlphaMult(alpha);
            sprite.renderAtCenter(x, y);
        }
    }

    protected static Color getColor(float depth) {
        Color deep = PondConstants.DEPTH_COLOR_DEEP;
        Color near = PondConstants.DEPTH_COLOR_NEAR;

        return new Color(
                (int) (deep.getRed() + (near.getRed() - deep.getRed()) * depth),
                (int) (deep.getGreen() + (near.getGreen() - deep.getGreen()) * depth),
                (int) (deep.getBlue() + (near.getBlue() - deep.getBlue()) * depth));
    }
}
