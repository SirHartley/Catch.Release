package catchrelease.tools;

import catchrelease.combat.tug.RopeCurve;
import org.lwjgl.util.vector.Vector2f;

public final class TetherRopeChecks {

    private TetherRopeChecks() {
    }

    public static void main(String[] args) {
        require(RopeCurve.getSag(500f, 500f) == 0f && RopeCurve.getSag(520f, 500f) == 0f,
                "A taut rope sagged");
        float previous = 0f;
        for (float chord = 490f; chord >= 0f; chord -= 10f) {
            float sag = RopeCurve.getSag(chord, 500f);
            require(sag >= previous, "Sag shrank as the rope slackened at chord " + chord);
            require(sag <= RopeCurve.MAX_SAG * 500f, "Sag exceeded its cap");
            previous = sag;
        }

        Vector2f a = new Vector2f(0f, 0f);
        Vector2f b = new Vector2f(400f, 0f);
        RopeCurve taut = new RopeCurve();
        settle(taut, a, b, 400f);
        Vector2f[] path = taut.getPath(a, b, 400f, true);
        require(path.length == RopeCurve.SEGMENTS + 1, "Path lost segments");
        require(near(path[0], a) && near(path[RopeCurve.SEGMENTS], b), "Path does not end at the rope's ends");
        require(Math.abs(path[RopeCurve.SEGMENTS / 2].y) < 0.5f, "Settled taut rope is not straight");

        RopeCurve slack = new RopeCurve();
        settle(slack, a, b, 500f);
        float belly = slack.getPath(a, b, 500f, false)[RopeCurve.SEGMENTS / 2].y;
        float expected = RopeCurve.getSag(400f, 500f);
        // The wobble rides on top of the belly, at most WAVE_AMPLITUDE of the chord at the middle.
        require(Math.abs(Math.abs(belly) - expected) <= 400f * RopeCurve.WAVE_AMPLITUDE + 1f,
                "Slack rope's middle is " + belly + " off the chord, expected about " + expected);
        int side = Integer.signum(Math.round(belly));
        Vector2f crossed = new Vector2f(395f, 0f);
        for (int frame = 0; frame < 6; frame++) slack.advance(a, crossed, 500f, 1f / 60f);
        float after = slack.getPath(a, crossed, 500f, false)[RopeCurve.SEGMENTS / 2].y;
        require(Integer.signum(Math.round(after)) == side, "Slack rope flipped sides while staying slack");

        System.out.println("Tether rope checks: sag, ends, settling, belly height and side hold passed");
    }

    private static void settle(RopeCurve curve, Vector2f a, Vector2f b, float length) {
        for (int frame = 0; frame < 600; frame++) curve.advance(a, b, length, 1f / 60f);
    }

    private static boolean near(Vector2f p, Vector2f q) {
        return Math.abs(p.x - q.x) < 1e-3f && Math.abs(p.y - q.y) < 1e-3f;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
