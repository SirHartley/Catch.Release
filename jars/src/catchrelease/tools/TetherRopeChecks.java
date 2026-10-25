package catchrelease.tools;

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
        Vector2f[] path = taut.getPath(a, b, 400f, true);
        require(path.length == RopeCurve.SEGMENTS + 1, "Path lost segments");
        require(near(path[0], a) && near(path[RopeCurve.SEGMENTS], b), "Path does not end at the rope's ends");
        require(Math.abs(path[RopeCurve.SEGMENTS / 2].y) < 0.5f, "Settled taut rope is not straight");

        // Slack shows as wobble about the chord, never as a belly to one side.
        RopeCurve slack = new RopeCurve();
        settle(slack, a, b, 600);
        for (int frame = 0; frame < 120; frame++) {
            slack.advance(a, b, FRAME);
            requireWithinWobble(slack.getPath(a, b, 500f, false), 400f, "Slack rope bellied out");
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
            requireWithinWobble(flight.getPath(a, shot, paidOut, false), shot.x, "Rope drifted sideways in flight");
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
        Vector2f middle = swung.getPath(a, end, 400f, true)[RopeCurve.SEGMENTS / 2];
        require(Math.abs(middle.x) < 1f && Math.abs(middle.y - 200f) < 1f,
                "Rope still bent 1.5 s after the ends stopped: middle at " + middle);

        System.out.println("Tether rope checks: ends, settling, no belly when slack, no sideways drift in flight"
                + " and straightening after a swing passed");
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
