package catchrelease.combat.tug;

import org.lwjgl.util.vector.Vector2f;

// The harpoon rope's shape: the campaign harpoon's spring-driven quadratic curve and wobble
// (HarpoonEntityPlugin.advanceSlack and getLinePath), plus a sag so a slack rope bellies out. No engine calls, so
// tools/TetherRopeChecks can run it headless.
public class RopeCurve {

    public static final int SEGMENTS = 32;
    public static final float SPRING = 150f;
    public static final float DRAG = 6f;
    public static final float MAX_STEP = 1f / 60f;
    public static final float WAVE_COUNT = 2.5f;
    public static final float WAVE_SPEED = 9f;
    public static final float WAVE_AMPLITUDE = 0.06f;
    public static final float WAVE_DAMPING = 0.5f;
    public static final float WAVE_REFERENCE_SPEED = 500f;
    public static final float WAVE_EXCESS_FULL = 0.12f;
    public static final float TAUT_SHIVER = 0.3f;
    public static final float MAX_SAG = 0.45f;
    // The belly keeps its side until the rope has been taut this long, so it does not flip as the ends cross.
    public static final float SIDE_HOLD = 0.25f;

    private final Vector2f[] points = new Vector2f[SEGMENTS + 1];
    private Vector2f slack;
    private final Vector2f slackVelocity = new Vector2f();
    private float age;
    private int side = 1;
    private float tautTime = SIDE_HOLD;

    public RopeCurve() {
        for (int i = 0; i <= SEGMENTS; i++) points[i] = new Vector2f();
    }

    public void advance(Vector2f a, Vector2f b, float ropeLength, float amount) {
        age += amount;
        Vector2f rest = getRestPoint(a, b, ropeLength, amount);
        if (slack == null) {
            slack = rest;
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(amount / MAX_STEP));
        float step = amount / steps;
        for (int i = 0; i < steps; i++) {
            slackVelocity.x += ((rest.x - slack.x) * SPRING - slackVelocity.x * DRAG) * step;
            slackVelocity.y += ((rest.y - slack.y) * SPRING - slackVelocity.y * DRAG) * step;
            slack.x += slackVelocity.x * step;
            slack.y += slackVelocity.y * step;
        }
    }

    // A whip along the rope when the harpoon bites.
    public void kick(Vector2f velocity, float scale) {
        slackVelocity.translate(velocity.x * scale, velocity.y * scale);
    }

    Vector2f getRestPoint(Vector2f a, Vector2f b, float ropeLength, float amount) {
        Vector2f middle = new Vector2f((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f);
        float chord = Vector2f.sub(b, a, null).length();
        float sag = getSag(chord, ropeLength);
        if (sag <= 0f) {
            tautTime += amount;
            return middle;
        }
        Vector2f across = getAcross(a, b, chord);
        if (tautTime >= SIDE_HOLD && slack != null) {
            float offset = (slack.x - middle.x) * across.x + (slack.y - middle.y) * across.y;
            side = offset < 0f ? -1 : 1;
        }
        tautTime = 0f;
        middle.translate(across.x * sag * side, across.y * sag * side);
        return middle;
    }

    // A parabola over chord d with sag s is about d + 8s²/3d long. Using the rope's length for d keeps the sag
    // growing as the ends close, where the estimate itself would shrink it again below half the length.
    public static float getSag(float chord, float ropeLength) {
        float excess = ropeLength - chord;
        if (excess <= 0f) return 0f;
        return Math.min(MAX_SAG * ropeLength, (float) Math.sqrt(3f * ropeLength * excess / 8f));
    }

    // Quadratic Bézier through the slack point at its middle, with a wobble across the chord that is zero at both
    // ends. The returned points are reused by the next call.
    public Vector2f[] getPath(Vector2f a, Vector2f b, float ropeLength, boolean taut) {
        float chord = Vector2f.sub(b, a, null).length();
        Vector2f across = getAcross(a, b, chord);
        float middleX = slack == null ? (a.x + b.x) * 0.5f : slack.x;
        float middleY = slack == null ? (a.y + b.y) * 0.5f : slack.y;
        float controlX = middleX * 2f - (a.x + b.x) * 0.5f;
        float controlY = middleY * 2f - (a.y + b.y) * 0.5f;
        float shiver = chord * WAVE_AMPLITUDE * getShiver(chord, ropeLength, taut);
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = i / (float) SEGMENTS;
            float inverse = 1f - t;
            float x = inverse * inverse * a.x + 2f * inverse * t * controlX + t * t * b.x;
            float y = inverse * inverse * a.y + 2f * inverse * t * controlY + t * t * b.y;
            float offset = (float) (Math.sin(t * Math.PI) * shiver
                    * Math.sin(t * Math.PI * WAVE_COUNT - age * WAVE_SPEED));
            points[i].set(x + across.x * offset, y + across.y * offset);
        }
        return points;
    }

    private float getShiver(float chord, float ropeLength, boolean taut) {
        float excess = chord <= 0f ? 0f : Math.max(0f, ropeLength - chord) / chord / WAVE_EXCESS_FULL;
        float thrown = (float) Math.exp(-age / WAVE_DAMPING);
        float swung = slackVelocity.length() / WAVE_REFERENCE_SPEED;
        float shiver = Math.min(1f, Math.max(excess, Math.max(thrown, swung)));
        return taut ? shiver * TAUT_SHIVER : shiver;
    }

    private static Vector2f getAcross(Vector2f a, Vector2f b, float chord) {
        if (chord <= 0f) return new Vector2f(0f, 1f);
        return new Vector2f(-(b.y - a.y) / chord, (b.x - a.x) / chord);
    }
}
