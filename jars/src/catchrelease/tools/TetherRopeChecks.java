package catchrelease.tools;

import catchrelease.combat.tug.RopeChain;
import catchrelease.combat.tug.RopeCurve;
import org.lwjgl.util.vector.Vector2f;

public final class TetherRopeChecks {

    private static final float FRAME = 1f / 60f;

    private TetherRopeChecks() {
    }

    public static void main(String[] args) {
        Vector2f a = new Vector2f(0f, 0f);
        Vector2f b = new Vector2f(400f, 0f);
        RopeCurve taut = new RopeCurve();
        settle(taut, a, b, 600);
        Vector2f[] path = taut.getPath(a, b, 400f);
        require(path.length == RopeCurve.SEGMENTS + 1, "Path lost segments");
        require(near(path[0], a) && near(path[RopeCurve.SEGMENTS], b), "Path does not end at the rope's ends");
        require(Math.abs(path[RopeCurve.SEGMENTS / 2].y) < 0.5f, "Settled taut rope is not straight");

        // Slack shows as wobble about the chord, never as a belly to one side.
        RopeCurve slack = new RopeCurve();
        settle(slack, a, b, 600);
        for (int frame = 0; frame < 120; frame++) {
            slack.advance(a, b, FRAME);
            requireWithinWobble(slack.getPath(a, b, 500f), 400f, "Slack rope bellied out");
        }

        // A shot paying the rope out along its chord never bows it sideways.
        RopeCurve flight = new RopeCurve();
        Vector2f shot = new Vector2f(30f, 0f);
        float paidOut = shot.x;
        flight.advance(a, shot, FRAME);
        while (shot.x < 900f) {
            shot.x += 800f * FRAME;
            paidOut = Math.max(paidOut, shot.x * 1.06f);
            flight.advance(a, shot, FRAME);
            requireWithinWobble(flight.getPath(a, shot, paidOut), shot.x, "Rope drifted sideways in flight");
        }

        // After the tug swings a quarter turn about the catch, the rope straightens again on the chord.
        RopeCurve swung = new RopeCurve();
        settle(swung, a, b, 600);
        Vector2f end = new Vector2f(b);
        for (int frame = 1; frame <= 30; frame++) {
            double angle = Math.PI / 2 * frame / 30;
            end.set((float) (400 * Math.cos(angle)), (float) (400 * Math.sin(angle)));
            swung.advance(a, end, FRAME);
        }
        settle(swung, a, end, 90);
        Vector2f middle = swung.getPath(a, end, 400f)[RopeCurve.SEGMENTS / 2];
        require(Math.abs(middle.x) < 1f && Math.abs(middle.y - 200f) < 1f,
                "Rope still bent 1.5 s after the ends stopped: middle at " + middle);

        chain();

        System.out.println("Tether rope checks: ends, settling, no belly when slack, no sideways drift in flight,"
                + " straightening after a swing, and an attached rope that is straight when taut, keeps its length"
                + " and bends out when slack, stays within reach of its ends and snaps straight when pulled passed");
    }

    // After the bite: a chain as long as the tether plus its slack.
    private static void chain() {
        Vector2f a = new Vector2f(0f, 0f);
        Vector2f b = new Vector2f(400f, 0f);
        Vector2f[] shape = {new Vector2f(a), new Vector2f(200f, 10f), new Vector2f(b)};
        RopeChain rope = new RopeChain(shape, 400f, new Vector2f(), new Vector2f());
        for (int frame = 0; frame < 3; frame++) rope.advance(a, b, 400f, FRAME);
        requireStraight(rope, "Taut attached rope is not straight");

        // The catch closes from 400 to 150 in half a second, then both hold.
        for (int frame = 1; frame <= 150; frame++) {
            b.x = Math.max(150f, 400f - 500f * frame * FRAME);
            rope.advance(a, b, 400f, FRAME);
            requireWithinReach(rope, a, b, 400f);
        }
        Vector2f[] points = rope.getPoints();
        require(near(points[0], a) && near(points[points.length - 1], b), "Chain left its ends");
        float length = 0f;
        float bent = 0f;
        for (int i = 1; i < points.length; i++) {
            length += Vector2f.sub(points[i], points[i - 1], null).length();
            bent = Math.max(bent, Math.abs(points[i].y));
        }
        require(length >= 0.85f * 400f && length <= 1.02f * 400f, "Slack rope's length is " + length + " of 400");
        require(bent > 20f, "Slack rope lay on its chord instead of bending out: " + bent);

        // Pulled taut again in one frame.
        b.x = 400f;
        rope.advance(a, b, 400f, FRAME);
        requireStraight(rope, "Rope pulled taut did not straighten");
    }

    private static void requireStraight(RopeChain rope, String message) {
        for (Vector2f point : rope.getPoints()) require(Math.abs(point.y) < 0.5f, message + ": " + point.y);
    }

    // No point is further from either end than its share of the rope, nor further from the middle than half the
    // rope and half the chord.
    private static void requireWithinReach(RopeChain rope, Vector2f a, Vector2f b, float length) {
        Vector2f[] points = rope.getPoints();
        int links = points.length - 1;
        float spacing = Math.max(length, Vector2f.sub(b, a, null).length()) / links;
        for (int i = 0; i <= links; i++) {
            float fromA = Vector2f.sub(points[i], a, null).length();
            float fromB = Vector2f.sub(points[i], b, null).length();
            require(fromA <= i * spacing + 0.5f && fromB <= (links - i) * spacing + 0.5f,
                    "Point " + i + " is out of its reach: " + fromA + " and " + fromB);
        }
    }

    private static void settle(RopeCurve curve, Vector2f a, Vector2f b, int frames) {
        for (int frame = 0; frame < frames; frame++) curve.advance(a, b, FRAME);
    }

    // Along the x axis the rope's only sideways offset is its wobble, at most WAVE_AMPLITUDE of the chord.
    private static void requireWithinWobble(Vector2f[] path, float chord, String message) {
        float limit = chord * RopeCurve.WAVE_AMPLITUDE + 1e-3f;
        for (Vector2f point : path) require(Math.abs(point.y) <= limit, message + ": " + point.y + " off the chord");
    }

    private static boolean near(Vector2f p, Vector2f q) {
        return Math.abs(p.x - q.x) < 1e-3f && Math.abs(p.y - q.y) < 1e-3f;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
