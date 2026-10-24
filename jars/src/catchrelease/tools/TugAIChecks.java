package catchrelease.tools;

import catchrelease.combat.tug.HarpoonAutofireAI;
import catchrelease.combat.tug.HarpoonAutofireAI.Shield;
import catchrelease.combat.tug.TugDriveAI;
import catchrelease.combat.tug.TugPilot;
import org.lwjgl.util.vector.Vector2f;

public final class TugAIChecks {

    private TugAIChecks() {
    }

    public static void main(String[] args) {
        float exposed = HarpoonAutofireAI.score(Shield.DOWN, false, false, 1f, 200f, 0, false, 0.5f);
        float flanked = HarpoonAutofireAI.score(Shield.FLANKED, false, false, 1f, 200f, 0, false, 0.5f);
        float facing = HarpoonAutofireAI.score(Shield.FACING, false, false, 1f, 200f, 0, false, 0.5f);
        require(exposed > flanked && flanked > facing, "Shield state does not order the targets");
        require(facing < HarpoonAutofireAI.MIN_SCORE, "Would fire into a frigate's facing shield");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, false, 1f, 200f, 2, false, 0.5f) < exposed / 2f,
                "Ships already under friendly attack are not passed over");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, true, 1f, 200f, 0, false, 0.5f) > exposed,
                "A fleeing ship is not preferred");
        require(HarpoonAutofireAI.score(Shield.DOWN, true, false, 1f, 200f, 0, false, 0.5f) > exposed,
                "An overloaded ship is not preferred");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, false, 1f, 3500f, 0, false, 0.5f) < exposed,
                "A capital scores as well as a frigate");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, false, 0.3f, 200f, 0, false, 0.5f) > exposed,
                "A damaged ship is not preferred");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, false, 1f, 200f, 0, false, 0f) > exposed,
                "A nearer ship is not preferred");

        Vector2f a = new Vector2f(0f, 0f);
        Vector2f b = new Vector2f(100f, 0f);
        require(HarpoonAutofireAI.distanceToSegment(new Vector2f(50f, 30f), a, b) == 30f, "Segment middle");
        require(HarpoonAutofireAI.distanceToSegment(new Vector2f(-40f, 30f), a, b) == 50f, "Segment start");
        require(HarpoonAutofireAI.distanceToSegment(new Vector2f(130f, 40f), a, b) == 50f, "Segment end");

        Vector2f tug = new Vector2f(0f, 0f);
        Vector2f catchPoint = new Vector2f(-500f, 0f);
        Vector2f straight = TugPilot.dragDirection(tug, catchPoint, new Vector2f());
        require(near(straight, 1f, 0f), "No bias did not pull straight away");
        Vector2f towardFriends = TugPilot.dragDirection(tug, catchPoint, new Vector2f(0f, 3f));
        require(towardFriends.y > 0.3f && towardFriends.x > 0f, "Did not bend toward friendly ships");
        Vector2f awayFromEnemies = TugPilot.dragDirection(tug, catchPoint, new Vector2f(0f, -5f));
        require(awayFromEnemies.y < -0.3f, "Did not bend away from enemies");
        for (int angle = 0; angle < 360; angle += 15) {
            Vector2f bias = new Vector2f((float) Math.cos(Math.toRadians(angle)) * 9f,
                    (float) Math.sin(Math.toRadians(angle)) * 9f);
            Vector2f heading = TugPilot.dragDirection(tug, catchPoint, bias);
            require(heading.x > 0.4f, "Heading closes on the catch for bias at " + angle + " degrees");
            require(Math.abs(heading.length() - 1f) < 1e-4f, "Heading is not a unit vector");
        }

        require(!TugDriveAI.shouldHaul(false, 5f, 200f), "Hauled on a slack line");
        require(TugDriveAI.shouldHaul(true, 2f, 0f), "Did not haul a heavy catch");
        require(TugDriveAI.shouldHaul(true, 0.3f, 50f), "Did not haul a light catch pulling away");
        require(!TugDriveAI.shouldHaul(true, 0.3f, 0f), "Hauled a light catch that follows");
        require(TugDriveAI.shouldEscape(0.2f, true) && !TugDriveAI.shouldEscape(0.2f, false)
                && !TugDriveAI.shouldEscape(0.9f, true), "Escape rule changed");

        System.out.println("Tug AI checks: harpoon target scoring, line-of-fire distance, drag heading and drive"
                + " use passed");
    }

    private static boolean near(Vector2f v, float x, float y) {
        return Math.abs(v.x - x) < 1e-4f && Math.abs(v.y - y) < 1e-4f;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
