package catchrelease.tools;

import catchrelease.campaign.fish.legendary.SlipDashModule;
import org.lwjgl.util.vector.Vector2f;

import java.awt.geom.Line2D;

public final class MoraySlipstreamChecks {

    private static int checks;

    private static class Slip extends SlipDashModule {

        Slip(LegendaryEscapeChecks.Environment env) {
            super(env.system, env.real("slipstream_moray").spec);
        }

        void sample(float x, float y) { recordTrail(new Vector2f(x, y)); }
        void finish() { endDash(null, getGrowingTrail()); }
        boolean clear(float x1, float y1, float x2, float y2) {
            return clearOfTrails(new Vector2f(x1, y1), new Vector2f(x2, y2), null);
        }
        void age(float seconds) { advanceTrails(seconds); }
    }

    public static void main(String[] args) {
        crossings();
        fading();
        foldedTrail();
        System.out.println("Moray slipstream checks passed: " + checks);
    }

    private static void crossings() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            Slip slip = new Slip(env);
            for (int x = -2000; x <= 2000; x += 200) slip.sample(x, 0);
            slip.finish();
            check(!slip.clear(0, -1500, 0, 1500), "crossing is blocked even with both ends outside");
            check(!slip.clear(-1500, 620, 1500, 620), "parallel ribbons reserve edge wobble too");
            check(slip.clear(-1500, 700, 1500, 700), "separated parallel ribbons remain available");
            check(!slip.clear(2001, 0, 2200, 0), "trail end reserves space");
            check(!slip.clear(0, 0, 0, 0), "a new trail cannot start inside an invisible pending trail");

            slip.sample(0, -1500);
            slip.sample(0, 1500);
            check(env.terrain.get(1).stream.getSegments().size() == 1,
                    "a long movement frame does not bridge an old stream");
            slip.sample(0, 1800);
            check(env.terrain.size() == 3 && env.terrain.get(2).stream.getSegments().size() == 2,
                    "recording resumes on the other side without connecting the gap");
            assertSeparate(env);
            slip.cleanup();
        }
    }

    private static void fading() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            Slip slip = new Slip(env);
            slip.sample(0, 0);
            slip.sample(200, 0);
            slip.sample(400, 0);
            slip.finish();
            var segments = env.terrain.get(0).stream.getSegments();
            for (var s : segments) s.fader.forceIn();
            slip.age(100);
            for (var s : segments) s.fader.advance(1);
            check(!slip.clear(100, -1000, 100, 1000), "fading ribbons still block crossings");
            for (var s : segments) s.fader.advance(100);
            check(slip.clear(100, -1000, 100, 1000), "fully faded rolled sections release their space");
            slip.sample(100, -1000);
            slip.sample(100, 1000);
            check(env.terrain.get(1).stream.getSegments().size() == 2,
                    "a later stream may use the freed space");
            slip.cleanup();
        }
    }

    private static void foldedTrail() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            Slip slip = new Slip(env);
            for (int x = 0; x <= 2000; x += 200) slip.sample(x, 0);
            slip.sample(1500, 0);
            check(env.terrain.size() == 1 && env.terrain.get(0).stream.getSegments().size() == 11,
                    "a reversed movement cannot fold the ribbon over itself");
            slip.sample(-1000, 0);
            slip.sample(-1200, 0);
            assertSeparate(env);
            slip.cleanup();
        }
    }

    private static void assertSeparate(LegendaryEscapeChecks.Environment env) {
        for (int a = 0; a < env.terrain.size(); a++) for (int b = a + 1; b < env.terrain.size(); b++) {
            var first = env.terrain.get(a).stream.getSegments();
            var second = env.terrain.get(b).stream.getSegments();
            for (int i = 1; i < first.size(); i++) for (int j = 1; j < second.size(); j++) {
                Vector2f p = first.get(i - 1).loc, q = first.get(i).loc;
                Vector2f r = second.get(j - 1).loc, s = second.get(j).loc;
                check(!Line2D.linesIntersect(p.x, p.y, q.x, q.y, r.x, r.y, s.x, s.y),
                        "stream centerlines never cross");
                for (int n = 0; n <= 20; n++) {
                    float t = n / 20f;
                    check(Line2D.ptSegDist(r.x, r.y, s.x, s.y,
                                    p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t) >= STREAM_ENVELOPE,
                            "whole ribbons remain separate, including vertex wobble");
                }
            }
        }
    }

    private static final float STREAM_ENVELOPE = SlipDashModule.STREAM_WIDTH * 1.1f;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
