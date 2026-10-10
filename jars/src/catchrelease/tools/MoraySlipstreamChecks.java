package catchrelease.tools;

import catchrelease.campaign.fish.legendary.SlipDashModule;
import catchrelease.campaign.fish.legendary.LegendaryStarAvoidance;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.awt.geom.Line2D;
import java.util.HashMap;
import java.util.Map;

public final class MoraySlipstreamChecks {

    private static int checks;
    private static final float STREAM_ENVELOPE = SlipDashModule.STREAM_WIDTH * 1.1f;

    private static class Slip extends SlipDashModule {

        Slip(LegendaryEscapeChecks.Environment env) {
            super(env.system, env.real("slipstream_moray").spec);
            catchrelease.campaign.fish.legendary.LegendaryChases.getState(spec.id).roaming = true;
        }

        void sample(float x, float y) { recordTrail(new Vector2f(x, y)); }
        void finish() { endDash(null, getGrowingTrail()); }
        boolean clear(float x1, float y1, float x2, float y2) {
            return clearOfTrails(new Vector2f(x1, y1), new Vector2f(x2, y2), null);
        }
        void age(float seconds) { advanceTrails(seconds); }
        void previous(float direction) { lastTravelBearing = direction; }
        float startDirection() { return dashStartBearing; }
        float previousDirection() { return previousExitBearing; }
        float direction() { return bearing; }
        float remaining() { return dashLeft; }
        void step(LegendaryEscapeChecks.Fish fish, float seconds) { steer(fish, seconds); }
        void normal(LegendaryEscapeChecks.Fish fish) { begin(fish, Global.getSector().getPlayerFleet()); }
    }

    public static void main(String[] args) {
        crossings();
        fading();
        foldedTrail();
        noReturn();
        starDetour();
        cooldown();
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
            if (env.terrain.get(a).expired || env.terrain.get(b).expired) continue;
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

    private static void noReturn() {
        for (int fps : new int[]{30, 60, 144}) for (float previous : new float[]{0, 90, 180, 270, 350}) {
            try (var env = new LegendaryEscapeChecks.Environment()) {
                Slip slip = new Slip(env);
                var fish = env.fish.get(0);
                fish.setSwimTarget(new Vector2f(50000f, 50000f));
                fish.at.set((float) Math.cos(Math.toRadians(previous + 180)) * 1000f,
                        (float) Math.sin(Math.toRadians(previous + 180)) * 1000f);
                slip.previous(previous);
                for (int dash = 0; dash < 3; dash++) {
                    Map<LegendaryEscapeChecks.Terrain, Integer> before = new HashMap<>();
                    for (var terrain : env.terrain) before.put(terrain, terrain.stream.getSegments().size());
                    if (dash == 0) slip.normal(fish);
                    else slip.onFailedCatch(fish);
                    check(forward(slip.direction(), slip.previousDirection()),
                            "escape does not begin toward the previous approach, including angle wrap");
                    slip.step(fish, 0f);
                    while (slip.remaining() > 0f) {
                        fish.advance(1f / fps);
                        slip.step(fish, 1f / fps);
                        check(forward(slip.direction(), slip.startDirection())
                                        && forward(slip.direction(), slip.previousDirection()),
                                "curve stays forward for the whole dash at " + fps + " Hz");
                    }
                    checkNewDirections(env, slip, before);
                    if (dash == 1) {
                        slip.age(1000);
                        for (var terrain : env.terrain) for (var segment : terrain.stream.getSegments()) {
                            segment.fader.advance(1000);
                        }
                        slip.age(1);
                        check(env.terrain.stream().allMatch(t -> t.expired),
                                "direction constraint also outlives fully removed trails");
                    }
                }
                assertSeparate(env);
                check(env.terrain.stream().anyMatch(t -> t.stream.getSegments().size() > 3),
                        "forward constraint still produces usable streams");
                slip.cleanup();
            }
        }
    }

    private static void starDetour() {
        for (float dt : new float[]{1f / 30f, 1f / 144f, 0.7f}) {
            try (var env = new LegendaryEscapeChecks.Environment()) {
                Slip slip = new Slip(env);
                var fish = env.fish.get(0);
                fish.setSwimTarget(new Vector2f(50000, 0));
                fish.at.set(1500, 0);
                slip.previous(0);
                slip.onFailedCatch(fish);
                double direction = Math.toRadians(slip.direction());
                Vector2f center = new Vector2f(fish.at.x + (float) Math.cos(direction) * 1500,
                        fish.at.y + (float) Math.sin(direction) * 1500);
                env.star(center.x, center.y, 300, 600);
                while (slip.remaining() > 0) {
                    fish.advance(dt);
                    double clearance = Math.hypot(fish.at.x - center.x, fish.at.y - center.y);
                    check(clearance >= 600 + LegendaryStarAvoidance.CLEARANCE - 0.1f,
                            "stream constraints preserve corona-safe positions: " + clearance + " at dt=" + dt);
                    slip.step(fish, dt);
                }
                checkNewDirections(env, slip, Map.of());
                assertSeparate(env);
                slip.cleanup();
            }
        }
    }

    private static void cooldown() {
        for (int fps : new int[]{30, 60, 144}) {
            try (var env = new LegendaryEscapeChecks.Environment()) {
                Slip slip = new Slip(env);
                var fish = env.fish.get(0);
                slip.advance(1f / fps);
                slip.step(fish, 100);
                for (int n = 0; n < 10 * fps; n++) slip.advance(1f / fps);
                check(slip.remaining() == 0, "repeat dash waits beyond the old nine-second minimum");
                for (int n = 0; n < 8 * fps + 2 && slip.remaining() <= 0; n++) slip.advance(1f / fps);
                check(slip.remaining() > 0, "eligible repeat dash starts within the new upper bound");
                slip.cleanup();
            }
        }
    }

    private static void checkNewDirections(LegendaryEscapeChecks.Environment env, Slip slip,
                                           Map<LegendaryEscapeChecks.Terrain, Integer> before) {
        for (var terrain : env.terrain) {
            var segments = terrain.stream.getSegments();
            for (int i = Math.max(1, before.getOrDefault(terrain, 0)); i < segments.size(); i++) {
                Vector2f from = segments.get(i - 1).loc, to = segments.get(i).loc;
                float bearing = (float) Math.toDegrees(Math.atan2(to.y - from.y, to.x - from.x));
                check(forward(bearing, slip.startDirection()) && forward(bearing, slip.previousDirection()),
                        "actual emitted stream never heads back, even after a star detour");
            }
        }
    }

    private static boolean forward(float direction, float axis) {
        return Math.cos(Math.toRadians(direction - axis)) > 0;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
