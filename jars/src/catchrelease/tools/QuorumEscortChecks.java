package catchrelease.tools;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.tools.LegendaryEscapeChecks.Environment;
import catchrelease.tools.LegendaryEscapeChecks.Fish;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class QuorumEscortChecks {

    private static int checks;

    public static void main(String[] args) throws Exception {
        spawnAndReplace();
        movement();
        wraparound();
        check(!Modifier.isTransient(FishEntityPlugin.class.getDeclaredField("orbitAngle").getModifiers()),
                "orbit angles must survive normal save/load");
        System.out.println("Quorum escort checks passed: " + checks);
    }

    private static void spawnAndReplace() {
        int uneven = 0;
        for (int seed = 0; seed < 500; seed++) try (Environment env = new Environment()) {
            MathUtils.getRandom().setSeed(seed);
            Fish real = env.real("quorum");
            real.at.set(seed * 13f, -seed * 19f);
            LegendaryShields.maintainSatellites(real);
            List<Fish> escorts = escorts(env, real);
            check(escorts.size() == 3, "spawn all three escorts");
            separated(real, escorts);
            List<Float> angles = escorts.stream().map(Fish::getOrbitAngle).sorted().toList();
            if (Math.abs(angles.get(1) - angles.get(0) - 120f) > 10f) uneven++;
            for (int replaced = 0; replaced < 3; replaced++) {
                escorts.get(replaced).expired = true;
                List<Fish> survivors = escorts(env, real);
                List<Float> before = survivors.stream().map(Fish::getOrbitAngle).toList();
                LegendaryShields.maintainSatellites(real);
                escorts = escorts(env, real);
                check(escorts.size() == 3, "expired escort replaced once");
                separated(real, escorts);
                for (int i = 0; i < survivors.size(); i++) {
                    check(before.get(i) == survivors.get(i).getOrbitAngle(), "replacement keeps survivor angles");
                }
            }
        }
        check(uneven > 400, "random gaps are not an evenly spaced formation");
    }

    private static void movement() {
        for (int fps : new int[]{30, 60, 144}) for (boolean anchorFirst : new boolean[]{true, false}) {
            try (Environment env = new Environment()) {
                MathUtils.getRandom().setSeed(307);
                Fish real = env.real("quorum");
                real.keepSurfaced(120f);
                real.setSwimTarget(new Vector2f(3500f, 2000f));
                LegendaryShields.maintainSatellites(real);
                List<Fish> escorts = escorts(env, real);
                List<Float> start = escorts.stream().map(Fish::getOrbitAngle).toList();
                for (int frame = 0; frame < fps * 10; frame++) {
                    if (anchorFirst) real.advance(1f / fps);
                    for (Fish escort : escorts) escort.advance(1f / fps);
                    if (!anchorFirst) real.advance(1f / fps);
                    separated(real, escorts);
                    for (int i = 1; i < escorts.size(); i++) {
                        float before = (start.get(i) - start.get(0) + 360f) % 360f;
                        float now = (escorts.get(i).getOrbitAngle() - escorts.get(0).getOrbitAngle() + 360f) % 360f;
                        check(Math.abs(MathUtils.getShortestRotation(before, now)) < 0.02f,
                                "callback order preserves relative angles");
                    }
                }
                Fish held = escorts.get(0);
                held.setHeld(true);
                held.at.set(-900f, 1100f);
                for (int i = 0; i < fps; i++) real.advance(1f / fps);
                check(held.at.equals(new Vector2f(-900f, 1100f)), "anchor cannot move a held escort");
                held.setHeld(false);
                held.advance(1f / fps);
                separated(real, escorts);
                List<Vector2f> before = escorts.stream().map(f -> new Vector2f(f.at)).toList();
                real.advance(0f);
                for (Fish escort : escorts) escort.advance(0f);
                for (int i = 0; i < escorts.size(); i++) {
                    check(before.get(i).equals(escorts.get(i).at), "pause leaves the orbit still");
                }
            }
        }
    }

    private static void wraparound() {
        try (Environment env = new Environment()) {
            Fish real = env.real("quorum");
            LegendaryShields.maintainSatellites(real);
            List<Fish> escorts = escorts(env, real);
            escorts.get(0).setOrbitAngle(359.9f);
            escorts.get(1).setOrbitAngle(90f);
            escorts.get(2).expired = true;
            for (int seed = 0; seed < 200; seed++) {
                MathUtils.getRandom().setSeed(seed);
                LegendaryShields.maintainSatellites(real);
                List<Fish> current = escorts(env, real);
                separated(real, current);
                current.get(2).expired = true;
            }
        }
    }

    private static List<Fish> escorts(Environment env, Fish real) {
        List<Fish> result = new ArrayList<>();
        for (Fish fish : env.fish) {
            if (!fish.expired && fish.getOrbitAnchor() == real.getMote()) result.add(fish);
        }
        return result;
    }

    private static void separated(Fish real, List<Fish> escorts) {
        for (int i = 0; i < escorts.size(); i++) {
            check(Math.abs(Vector2f.sub(escorts.get(i).at, real.at, null).length() - 70f) < 0.01f,
                    "escort is immediately on the orbit, not at its center");
            for (int j = 0; j < i; j++) {
                check(Vector2f.sub(escorts.get(i).at, escorts.get(j).at, null).length() >= 59.99f,
                        "escort centers retain the minimum separation");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
