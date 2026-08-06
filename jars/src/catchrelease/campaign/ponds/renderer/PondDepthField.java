package catchrelease.campaign.ponds.renderer;

import catchrelease.campaign.ponds.constants.PondConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * Motes of light at different depths inside the rupture, drifting past each other to fake depth.
 * Camera-snap to the pond kills ordinary background parallax (the offset is computed from distance
 * to screen center, which is zero when centered), so depth-cueing lives in the particles instead:
 * each has a depth driving size/color/speed (deep = small, dim, blue, slow), and spirals slowly
 * inward and down, accelerating near the middle, respawning at the rim on reaching the drain.
 */
public class PondDepthField {

    protected static class Particle {
        /** 0 at the bottom of the rupture, 1 just under the surface. Everything else follows from it. */
        float depth;

        float angle;
        float radius;
        float size;
        float phase;

        /** Degrees per second around the middle, signed - not every layer turns the same way. */
        float spin;

        /** How far it rises and falls through its own depth, and how quickly. */
        float bob;
        float bobRate;
    }

    protected final Particle[] particles;
    protected transient SpriteAPI sprite;
    protected float time = 0f;

    public PondDepthField() {
        particles = new Particle[PondConstants.DEPTH_PARTICLES];

        for (int i = 0; i < particles.length; i++) {
            particles[i] = spawn(true);
        }
    }

    /**
     * @param anywhere true for the first fill, which scatters particles through the whole well so
     *                 the spiral does not start as one ring marching in; a respawn starts at the
     *                 rim near the surface, where the drain delivered it
     */
    protected Particle spawn(boolean anywhere) {
        Particle p = new Particle();

        p.angle = MathUtils.getRandomNumberInRange(0f, 360f);

        if (anywhere) {
            p.depth = MathUtils.getRandomNumberInRange(0f, 1f);

            //square-rooted, or every particle bunches into the middle where the area is smallest
            p.radius = (float) Math.sqrt(MathUtils.getRandomNumberInRange(0f, 1f));
        } else {
            p.depth = MathUtils.getRandomNumberInRange(0.85f, 1f);
            p.radius = MathUtils.getRandomNumberInRange(0.92f, 1.05f);
        }

        p.size = MathUtils.getRandomNumberInRange(0.6f, 1.4f);
        p.phase = MathUtils.getRandomNumberInRange(0f, 6.283f);

        //mostly one direction; DEPTH_COUNTER_SHARE gives a few counter-spinners for texture
        p.spin = MathUtils.getRandomNumberInRange(PondConstants.DEPTH_SPIN_MIN, PondConstants.DEPTH_SPIN_MAX)
                * (MathUtils.getRandomNumberInRange(0f, 1f) < PondConstants.DEPTH_COUNTER_SHARE ? -1f : 1f);

        p.bob = MathUtils.getRandomNumberInRange(0.05f, PondConstants.DEPTH_BOB);
        p.bobRate = MathUtils.getRandomNumberInRange(0.15f, 0.6f);

        return p;
    }

    /**
     * Shallow particles orbit faster than deep ones at the same radius (shear reads as depth); all
     * particles spiral inward and down, accelerating near center, and respawn at the rim on
     * reaching the drain.
     */
    public void advance(float amount) {
        time += amount;

        for (int i = 0; i < particles.length; i++) {
            Particle p = particles[i];
            float mult = getSpeedMult(p);

            //the vortex: angular speed climbs as the radius falls, the way water actually drains
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

    /** Depth-driven, so the near layer is the fast one. */
    protected float getSpeedMult(Particle p) {
        return PondConstants.DEPTH_SPEED_FLOOR
                + (1f - PondConstants.DEPTH_SPEED_FLOOR) * getDepth(p);
    }

    /** Its depth this instant - its own, plus however far it has bobbed through the water. */
    protected float getDepth(Particle p) {
        float bobbed = p.depth + (float) Math.sin(time * p.bobRate + p.phase) * p.bob;

        return MathUtils.clamp(bobbed, 0f, 1f);
    }

    /**
     * Single glow-sprite pass per particle, additive-blended (unlike the six-pass bloom fish motes
     * use, which would be too costly at this particle count). "Darker with depth" is done via
     * color/alpha ramps since additive blending can't paint darker. The field intentionally
     * overshoots the pond radius - caller must have the pond mask stencilled to cut it at the rim.
     */
    public void render(Vector2f center, float pondRadius, float alphaMult) {
        if (alphaMult <= 0f || pondRadius <= 0f) return;

        if (sprite == null) sprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        sprite.setAdditiveBlend();

        for (Particle p : particles) {
            float depth = getDepth(p);

            //deep particles sit nearer the middle too, so the rupture reads as a narrowing well
            //rather than a cylinder
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

    /**
     * Deep = dark/cold, shallow = bright/warm. Brightness matters more than hue here, since
     * additive blending means "dark" requires low alpha, not just a darker color.
     */
    protected static Color getColor(float depth) {
        Color deep = PondConstants.DEPTH_COLOR_DEEP;
        Color near = PondConstants.DEPTH_COLOR_NEAR;

        return new Color(
                (int) (deep.getRed() + (near.getRed() - deep.getRed()) * depth),
                (int) (deep.getGreen() + (near.getGreen() - deep.getGreen()) * depth),
                (int) (deep.getBlue() + (near.getBlue() - deep.getBlue()) * depth));
    }
}
