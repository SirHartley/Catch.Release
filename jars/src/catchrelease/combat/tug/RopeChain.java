package catchrelease.combat.tug;

import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

// An attached harpoon rope as a chain of points, so a slack rope moves as a loose line does in space: nothing pulls
// it down, it keeps its momentum, and when its ends close in the spare length folds and loops. Position-based: each
// step moves the points by their velocity and pins the ends, then pulls neighbours back to their spacing, keeps each
// joint from folding sharper than MAX_BEND, and keeps every point within its share of the rope from both ends. That
// last rule (long-range attachment) straightens a taut rope at once, however many links it has. No engine calls, so
// tools/TetherRopeChecks can run it headless.
public class RopeChain {

    public static final float SPACING = 15f;
    public static final int MIN_LINKS = 12;
    public static final int MAX_LINKS = 64;
    public static final int ITERATIONS = 16;
    public static final float MAX_STEP = 1f / 60f;
    public static final int MAX_STEPS = 8;
    // Loss per second, standing in for the rope's own friction; space adds none.
    public static final float DAMPING = 1.2f;
    // The sharpest turn one joint takes, in degrees, which sets the tightest loop.
    public static final float MAX_BEND = 12f;
    // A rope resists stretching fully but gives under pushing: a link shorter than its spacing is only eased back,
    // so a pushed rope bends where it is pushed instead of acting as a rod.
    public static final float PUSH_STIFFNESS = 0.3f;
    // A straight rope pushed end to end stays straight in a perfect simulation. Each point of a slack rope gets a
    // random nudge of up to this speed every step, as a real rope has kinks, so it buckles.
    public static final float KINK_SPEED = 1f;
    public static final int SMOOTHING = 3;

    private final int links;
    private final Vector2f[] points;
    private final Vector2f[] previous;
    private final Vector2f[] velocities;
    private final Vector2f[] path;
    private final Vector2f startFrom = new Vector2f();
    private final Vector2f endFrom = new Vector2f();
    private final Random random = new Random();
    private final float minSkip;

    // Takes over from the drawn curve at the bite: the shape is resampled along its length, and each point moves as
    // the ends do, shared by its place along the rope.
    public RopeChain(Vector2f[] shape, float length, Vector2f startVelocity, Vector2f endVelocity) {
        links = Math.max(MIN_LINKS, Math.min(MAX_LINKS, Math.round(length / SPACING)));
        points = new Vector2f[links + 1];
        previous = new Vector2f[links + 1];
        velocities = new Vector2f[links + 1];
        path = new Vector2f[links * SMOOTHING + 1];
        for (int i = 0; i <= links; i++) {
            float share = i / (float) links;
            points[i] = sample(shape, share);
            previous[i] = new Vector2f(points[i]);
            velocities[i] = new Vector2f(startVelocity.x + (endVelocity.x - startVelocity.x) * share,
                    startVelocity.y + (endVelocity.y - startVelocity.y) * share);
        }
        for (int i = 0; i < path.length; i++) path[i] = new Vector2f();
        minSkip = 2f * (float) Math.cos(Math.toRadians(MAX_BEND * 0.5f));
    }

    private static Vector2f sample(Vector2f[] shape, float share) {
        float total = 0f;
        for (int i = 1; i < shape.length; i++) total += distance(shape[i - 1], shape[i]);
        float wanted = total * share;
        for (int i = 1; i < shape.length; i++) {
            float piece = distance(shape[i - 1], shape[i]);
            if (wanted <= piece || i == shape.length - 1) {
                float t = piece <= 0f ? 0f : Math.min(1f, wanted / piece);
                return new Vector2f(shape[i - 1].x + (shape[i].x - shape[i - 1].x) * t,
                        shape[i - 1].y + (shape[i].y - shape[i - 1].y) * t);
            }
            wanted -= piece;
        }
        return new Vector2f(shape[0]);
    }

    // a and b are the ends this frame; length is the rope's length, which a taut rope's chord may exceed.
    public void advance(Vector2f a, Vector2f b, float length, float amount) {
        if (amount <= 0f) return;
        int steps = Math.min(MAX_STEPS, Math.max(1, (int) Math.ceil(amount / MAX_STEP)));
        float step = amount / steps;
        float keep = (float) Math.exp(-DAMPING * step);
        startFrom.set(points[0]);
        endFrom.set(points[links]);
        for (int s = 1; s <= steps; s++) {
            float t = s / (float) steps;
            float ax = startFrom.x + (a.x - startFrom.x) * t;
            float ay = startFrom.y + (a.y - startFrom.y) * t;
            float bx = endFrom.x + (b.x - endFrom.x) * t;
            float by = endFrom.y + (b.y - endFrom.y) * t;
            float chord = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
            float spacing = Math.max(length, chord) / links;
            boolean slack = length > chord + spacing * 0.01f;
            for (int i = 1; i < links; i++) {
                Vector2f velocity = velocities[i];
                velocity.scale(keep);
                if (slack) {
                    velocity.translate((random.nextFloat() * 2f - 1f) * KINK_SPEED,
                            (random.nextFloat() * 2f - 1f) * KINK_SPEED);
                }
                previous[i].set(points[i]);
                points[i].translate(velocity.x * step, velocity.y * step);
            }
            points[0].set(ax, ay);
            points[links].set(bx, by);
            if (slack) {
                for (int k = 0; k < ITERATIONS; k++) solve(spacing, (k & 1) == 0);
            } else {
                // A taut rope's only shape is the straight line, which projecting onto touching reach circles
                // approaches too slowly; its points are put there directly.
                for (int i = 1; i < links; i++) {
                    float share = i / (float) links;
                    points[i].set(ax + (bx - ax) * share, ay + (by - ay) * share);
                }
            }
            for (int i = 1; i < links; i++) {
                velocities[i].set((points[i].x - previous[i].x) / step, (points[i].y - previous[i].y) / step);
            }
        }
    }

    // Sweeps alternate direction, which spreads a correction along the chain twice as fast.
    private void solve(float spacing, boolean forward) {
        for (int n = 0; n < links; n++) {
            int i = forward ? n : links - 1 - n;
            keepApart(i, i + 1, spacing, spacing, PUSH_STIFFNESS);
        }
        float skip = spacing * minSkip;
        for (int i = 0; i + 2 <= links; i++) keepApart(i, i + 2, skip, Float.MAX_VALUE, 1f);
        Vector2f start = points[0];
        Vector2f end = points[links];
        for (int i = 1; i < links; i++) {
            keepWithin(points[i], start, i * spacing);
            keepWithin(points[i], end, (links - i) * spacing);
        }
    }

    // Moves points i and j, ends excepted, until their distance lies within [min, max]; a distance below min is only
    // corrected by pushStiffness of the way.
    private void keepApart(int i, int j, float min, float max, float pushStiffness) {
        Vector2f p = points[i];
        Vector2f q = points[j];
        float dx = q.x - p.x;
        float dy = q.y - p.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float target = Math.max(min, Math.min(max, distance));
        if (distance <= 1e-4f || target == distance) return;
        float wi = i == 0 || i == links ? 0f : 1f;
        float wj = j == 0 || j == links ? 0f : 1f;
        if (wi + wj <= 0f) return;
        float correction = (distance - target) / distance / (wi + wj) * (distance < target ? pushStiffness : 1f);
        p.translate(dx * correction * wi, dy * correction * wi);
        q.translate(-dx * correction * wj, -dy * correction * wj);
    }

    private static void keepWithin(Vector2f point, Vector2f anchor, float reach) {
        float dx = point.x - anchor.x;
        float dy = point.y - anchor.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= reach || distance <= 0f) return;
        float scale = reach / distance;
        point.set(anchor.x + dx * scale, anchor.y + dy * scale);
    }

    // A Catmull-Rom curve through the points, SMOOTHING pieces per link. The returned points are reused.
    public Vector2f[] getPath() {
        for (int i = 0; i < links; i++) {
            Vector2f p0 = points[Math.max(0, i - 1)];
            Vector2f p1 = points[i];
            Vector2f p2 = points[i + 1];
            Vector2f p3 = points[Math.min(links, i + 2)];
            for (int s = 0; s < SMOOTHING; s++) {
                float t = s / (float) SMOOTHING;
                float t2 = t * t;
                float t3 = t2 * t;
                path[i * SMOOTHING + s].set(
                        0.5f * (2f * p1.x + (p2.x - p0.x) * t + (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2
                                + (3f * p1.x - p0.x - 3f * p2.x + p3.x) * t3),
                        0.5f * (2f * p1.y + (p2.y - p0.y) * t + (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2
                                + (3f * p1.y - p0.y - 3f * p2.y + p3.y) * t3));
            }
        }
        path[path.length - 1].set(points[links]);
        return path;
    }

    public Vector2f[] getPoints() {
        return points;
    }

    private static float distance(Vector2f p, Vector2f q) {
        float dx = q.x - p.x;
        float dy = q.y - p.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
