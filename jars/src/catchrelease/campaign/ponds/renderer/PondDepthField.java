package catchrelease.campaign.ponds.renderer;

import catchrelease.campaign.ponds.constants.PondConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.MathUtils;
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
    protected transient SpriteAPI sprite;
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
     * The same soft glow sprite the fish motes use, but a single pass per particle - the motes stack
     * six shrinking passes into a bloom, and ninety of those would be both a draw-call pile and a
     * wall of light.
     * <p>
     * Additive, so they read as light in a medium rather than as objects floating on top of it.
     * Additive can only ever add, though, which is why "darker with depth" lives entirely in the
     * colour and alpha ramps - a deep particle contributes almost nothing rather than being painted
     * dark. The caller is expected to have the pond's mask stencilled: the field deliberately
     * overshoots the rim and relies on being cut, so a particle half-swallowed by the edge reads as
     * one that continues underneath it.
     */
    public void render(Vector2f center, float pondRadius, float alphaMult) {
        if (alphaMult <= 0f || pondRadius <= 0f) return;

        if (sprite == null) sprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        sprite.setAdditiveBlend();

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
     * Deep is dark and cold, shallow is bright and warm. The brightness half matters more than the
     * hue half: over a lit background an additive particle is only as dark as how little it adds,
     * so the deep end of the ramp has to be genuinely dim rather than merely blue.
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
