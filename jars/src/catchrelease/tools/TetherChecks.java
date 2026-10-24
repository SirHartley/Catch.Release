package catchrelease.tools;

import catchrelease.combat.tug.Tether;
import org.lwjgl.util.vector.Vector2f;

public final class TetherChecks {

    private TetherChecks() {
    }

    public static void main(String[] args) {
        Vector2f a = new Vector2f(0f, 0f);
        Vector2f b = new Vector2f(500f, 0f);
        Vector2f still = new Vector2f();
        Vector2f normal = new Vector2f();

        require(Tether.solve(a, b, new Vector2f(-100f, 0f), still, 1f / 600f, 1f / 200f, 520f, normal) == 0f,
                "Slack rope pulled");
        require(Tether.solve(a, b, new Vector2f(250f, 0f), still, 1f / 600f, 1f / 200f, 400f, normal) == 0f,
                "Ends closing fast enough still pulled");

        Vector2f tug = new Vector2f(-100f, 0f);
        Vector2f target = new Vector2f();
        float impulse = Tether.solve(a, b, tug, target, 1f / 600f, 1f / 200f, 499f, normal);
        require(impulse > 0f && normal.x == 1f && normal.y == 0f, "Taut separating rope did not pull toward B");
        float tugChange = impulse / 600f;
        float targetChange = impulse / 200f;
        require(Math.abs(targetChange - 3f * tugChange) < 1e-3f, "Frigate did not move three times the Stigmata");
        require(Math.abs(tugChange + targetChange - (100f + Tether.CORRECTION_RATE)) < 1e-3f,
                "Separation was not removed exactly");

        impulse = Tether.solve(a, b, tug, target, 1f / 600f, 1f / 1800f, 499f, normal);
        require(Math.abs(impulse / 1800f - impulse / 600f / 3f) < 1e-3f, "Cruiser did not move a third as much");

        impulse = Tether.solve(a, b, still, still, 1f / 600f, 1f / 600f, 490f, normal);
        float closing = 2f * impulse / 600f;
        require(Math.abs(closing - 10f * Tether.CORRECTION_RATE) < 1e-3f, "Stretch correction rate changed");
        impulse = Tether.solve(a, b, still, still, 1f / 600f, 1f / 600f, 100f, normal);
        require(Math.abs(2f * impulse / 600f - Tether.MAX_CORRECTION_SPEED) < 1e-3f, "Stretch correction not capped");

        float allowance = 0f;
        for (int frame = 0; frame < 300; frame++) {
            allowance = Tether.nextAllowance(allowance, 140f, 100f, true, 1f / 60f);
        }
        require(allowance == 40f + Tether.SPEED_MARGIN, "Steady drag allowance crept to " + allowance);
        allowance = Tether.nextAllowance(allowance, 140f, 100f, false, 0.1f);
        require(Math.abs(allowance - (40f + Tether.SPEED_MARGIN - 0.1f * Tether.SPEED_ALLOWANCE_DECAY)) < 1e-3f,
                "Allowance did not decay once slack: " + allowance);
        require(Tether.nextAllowance(0f, 80f, 100f, true, 1f / 60f) == 0f, "Allowance given below top speed");

        System.out.println("Tether checks: slack, closing, mass split, stretch correction and speed allowance passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
