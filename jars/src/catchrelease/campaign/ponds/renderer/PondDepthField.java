package catchrelease.campaign.ponds.renderer;

import catchrelease.campaign.ponds.constants.PondConstants;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * Motes of light hanging at different depths inside the rupture, drifting past each other.
 * <p>
 * The pond used to read as deep because the starfield behind it slid against the mask as the camera
 * moved. Snapping the camera to the pond took that away - a parallax offset computed from the
 * distance to the middle of the screen is zero when the thing is in the middle of the screen - and a
 * hole with a still background in it reads as a hole cut in paper.
 * <p>
 * So the depth is put back into the contents rather than into the camera. Everything here turns on
 * one number per particle: how deep it is. A deep one is small, dim, blue and slow; a shallow one is
 * larger, brighter, warmer and quicker. Because they all circle the same middle at speeds that
 * differ by depth, they slide across each other continuously - which is motion parallax, and it does
 * not care whether the camera is moving.
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
    protected float time = 0f;

    public PondDepthField() {
        particles = new Particle[PondConstants.DEPTH_PARTICLES];

        for (int i = 0; i < particles.length; i++) {
            particles[i] = spawn();
        }
    }

    protected Particle spawn() {
        Particle p = new Particle();

        p.depth = MathUtils.getRandomNumberInRange(0f, 1f);
        p.angle = MathUtils.getRandomNumberInRange(0f, 360f);

        //square-rooted, or every particle bunches into the middle where the area is smallest
        p.radius = (float) Math.sqrt(MathUtils.getRandomNumberInRange(0f, 1f));

        p.size = MathUtils.getRandomNumberInRange(0.6f, 1.4f);
        p.phase = MathUtils.getRandomNumberInRange(0f, 6.283f);

        p.spin = MathUtils.getRandomNumberInRange(PondConstants.DEPTH_SPIN_MIN, PondConstants.DEPTH_SPIN_MAX)
                * (MathUtils.getRandomNumberInRange(0f, 1f) < PondConstants.DEPTH_COUNTER_SHARE ? -1f : 1f);

        p.bob = MathUtils.getRandomNumberInRange(0.05f, PondConstants.DEPTH_BOB);
        p.bobRate = MathUtils.getRandomNumberInRange(0.15f, 0.6f);

        return p;
    }

    /**
     * A shallow particle moves faster than a deep one at the same distance out, which is the whole
     * trick: the layers shear against each other and the eye reads the difference as distance.
     */
    public void advance(float amount) {
        time += amount;

        for (Particle p : particles) {
            p.angle += p.spin * getSpeedMult(p) * amount;
            if (p.angle > 360f) p.angle -= 360f;
            if (p.angle < 0f) p.angle += 360f;
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
     * Drawn as one batch of quads rather than as discs, because at this size a four-sided dot and a
     * thirty-two-sided one are the same handful of pixels and there are a lot of them.
     * <p>
     * Additive, so they read as light in a medium rather than as objects floating on top of it. The
     * caller is expected to have the pond's mask stencilled, or they will spill out of the rupture.
     */
    public void render(Vector2f center, float pondRadius, float alphaMult) {
        if (alphaMult <= 0f || pondRadius <= 0f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        GL11.glBegin(GL11.GL_QUADS);

        for (Particle p : particles) {
            float depth = getDepth(p);

            //a deep particle sits nearer the middle as well as being smaller, so the rupture reads as
            //a well narrowing away from you rather than as a cylinder
            float reach = PondConstants.DEPTH_REACH_FLOOR
                    + (1f - PondConstants.DEPTH_REACH_FLOOR) * depth;

            double radians = Math.toRadians(p.angle);
            float distance = p.radius * reach * pondRadius * PondConstants.DEPTH_FILL;

            float x = center.x + (float) Math.cos(radians) * distance;
            float y = center.y + (float) Math.sin(radians) * distance;

            float size = p.size * (PondConstants.DEPTH_SIZE_MIN
                    + (PondConstants.DEPTH_SIZE_MAX - PondConstants.DEPTH_SIZE_MIN) * depth);

            //fades out towards the rim as well as with depth, so nothing sits hard against the mask
            float edge = 1f - MathUtils.clamp((p.radius * reach - PondConstants.DEPTH_EDGE_FROM)
                    / Math.max(0.01f, 1f - PondConstants.DEPTH_EDGE_FROM), 0f, 1f);

            float alpha = alphaMult * edge * (PondConstants.DEPTH_ALPHA_MIN
                    + (PondConstants.DEPTH_ALPHA_MAX - PondConstants.DEPTH_ALPHA_MIN) * depth);

            if (alpha <= 0f) continue;

            Color color = getColor(depth);
            GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha);

            GL11.glVertex2f(x - size, y - size);
            GL11.glVertex2f(x + size, y - size);
            GL11.glVertex2f(x + size, y + size);
            GL11.glVertex2f(x - size, y + size);
        }

        GL11.glEnd();

        GL11.glPopAttrib();
    }

    /** Deep is cold and shallow is warm, which is the other half of what says how far away it is. */
    protected static Color getColor(float depth) {
        Color deep = PondConstants.DEPTH_COLOR_DEEP;
        Color near = PondConstants.DEPTH_COLOR_NEAR;

        return new Color(
                (int) (deep.getRed() + (near.getRed() - deep.getRed()) * depth),
                (int) (deep.getGreen() + (near.getGreen() - deep.getGreen()) * depth),
                (int) (deep.getBlue() + (near.getBlue() - deep.getBlue()) * depth));
    }
}
