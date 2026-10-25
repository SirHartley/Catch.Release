package catchrelease.combat.tug;

import org.lwjgl.util.vector.Vector2f;

// The harpoon rope's shape, the campaign harpoon's spring-driven quadratic curve and wobble (HarpoonEntityPlugin
// advanceSlack, getRestPoint and getLinePath): the curve's middle springs toward the chord's midpoint, so it only
// bends while the ends move, and slack shows as wobble, never as a belly to one side. No engine calls, so
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
    // The campaign's HAUL_SHIVER: with a ship on the end the rope is a cable, so it mostly follows the swing term.
    public static final float HELD_SHIVER = 0.3f;

    private final Vector2f[] points = new Vector2f[SEGMENTS + 1];
    private Vector2f slack;
    private final Vector2f slackVelocity = new Vector2f();
    private float age;

    public RopeCurve() {
        for (int i = 0; i <= SEGMENTS; i++) points[i] = new Vector2f();
    }

    public void advance(Vector2f a, Vector2f b, float amount) {
        age += amount;
        float restX = (a.x + b.x) * 0.5f;
        float restY = (a.y + b.y) * 0.5f;
        if (slack == null) {
            slack = new Vector2f(restX, restY);
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(amount / MAX_STEP));
        float step = amount / steps;
        for (int i = 0; i < steps; i++) {
            slackVelocity.x += ((restX - slack.x) * SPRING - slackVelocity.x * DRAG) * step;
            slackVelocity.y += ((restY - slack.y) * SPRING - slackVelocity.y * DRAG) * step;
            slack.x += slackVelocity.x * step;
            slack.y += slackVelocity.y * step;
        }
    }

    // Quadratic Bézier through the slack point at its middle, with a wobble across the chord that is zero at both
    // ends. The returned points are reused by the next call.
    public Vector2f[] getPath(Vector2f a, Vector2f b, float ropeLength, boolean held) {
        float chord = Vector2f.sub(b, a, null).length();
        Vector2f across = getAcross(a, b, chord);
        float middleX = slack == null ? (a.x + b.x) * 0.5f : slack.x;
        float middleY = slack == null ? (a.y + b.y) * 0.5f : slack.y;
        float controlX = middleX * 2f - (a.x + b.x) * 0.5f;
        float controlY = middleY * 2f - (a.y + b.y) * 0.5f;
        float shiver = chord * WAVE_AMPLITUDE * getShiver(chord, ropeLength, held);
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

    private float getShiver(float chord, float ropeLength, boolean held) {
        float excess = chord <= 0f ? 0f : Math.max(0f, ropeLength - chord) / chord / WAVE_EXCESS_FULL;
        float thrown = (float) Math.exp(-age / WAVE_DAMPING);
        float swung = slackVelocity.length() / WAVE_REFERENCE_SPEED;
        float shiver = Math.min(1f, Math.max(excess, Math.max(thrown, swung)));
        return held ? shiver * HELD_SHIVER : shiver;
    }

    private static Vector2f getAcross(Vector2f a, Vector2f b, float chord) {
        if (chord <= 0f) return new Vector2f(0f, 1f);
        return new Vector2f(-(b.y - a.y) / chord, (b.x - a.x) / chord);
    }
}
