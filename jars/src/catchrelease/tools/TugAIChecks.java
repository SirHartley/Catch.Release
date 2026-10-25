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
        float exposed = HarpoonAutofireAI.score(Shield.DOWN, false, false, 1f, 200f, 4f, 0.5f);
        float flanked = HarpoonAutofireAI.score(Shield.FLANKED, false, false, 1f, 200f, 4f, 0.5f);
        float facing = HarpoonAutofireAI.score(Shield.FACING, true, true, 0.2f, 200f, 8f, 0f);
        require(exposed > flanked && flanked > facing, "Shield state does not order the targets");
        require(facing < HarpoonAutofireAI.MIN_SCORE, "Would fire into a facing shield, even of a crippled frigate");
        require(HarpoonAutofireAI.score(Shield.DOWN, true, true, 0.2f, 200f, 0f, 0f) < HarpoonAutofireAI.MIN_SCORE,
                "Would harpoon a ship with no friendly guns near the tug");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, false, 1f, 200f, 1f, 0.5f) < exposed / 2f,
                "A catch with little friendly firepower near the tug is not passed over");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, false, 1f, 200f, 8f, 0.5f) == exposed,
                "Support past FULL_SUPPORT still counts");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, false, 1f, 600f, 4f, 0.5f)
                        >= HarpoonAutofireAI.MIN_SCORE * 2f,
                "A destroyer with its shield down near friends is not an easy pick");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, true, 1f, 200f, 4f, 0.5f) > exposed,
                "A fleeing ship is not preferred");
        require(HarpoonAutofireAI.score(Shield.DOWN, true, false, 1f, 200f, 4f, 0.5f) > exposed,
                "An overloaded ship is not preferred");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, false, 1f, 3500f, 4f, 0.5f) < exposed,
                "A capital scores as well as a frigate");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, false, 0.3f, 200f, 4f, 0.5f) > exposed,
                "A damaged ship is not preferred");
        require(HarpoonAutofireAI.score(Shield.DOWN, false, false, 1f, 200f, 4f, 0f) > exposed,
                "A nearer ship is not preferred");

        Vector2f a = new Vector2f(0f, 0f);
        Vector2f b = new Vector2f(100f, 0f);
        require(HarpoonAutofireAI.distanceToSegment(new Vector2f(50f, 30f), a, b) == 30f, "Segment middle");
        require(HarpoonAutofireAI.distanceToSegment(new Vector2f(-40f, 30f), a, b) == 50f, "Segment start");
        require(HarpoonAutofireAI.distanceToSegment(new Vector2f(130f, 40f), a, b) == 50f, "Segment end");
        float contact = HarpoonAutofireAI.shieldContact(new Vector2f(60f, 0f), 20f, a, b);
        require(Math.abs(contact - 0.4f) < 1e-4f, "Shield met at " + contact + " of the line, expected 0.4");
        require(HarpoonAutofireAI.shieldContact(new Vector2f(50f, 30f), 20f, a, b) < 0f,
                "A shield beside the line met it");
        require(HarpoonAutofireAI.shieldContact(new Vector2f(200f, 0f), 20f, a, b) < 0f,
                "A shield past the target met it");
        contact = HarpoonAutofireAI.shieldContact(new Vector2f(0f, 0f), 30f, a, b);
        require(Math.abs(contact - 0.3f) < 1e-4f, "A line from inside a shield should meet it where it leaves");

        // The rope runs from the catch to the tug along 0 degrees.
        float swing = TugPilot.MAX_SWING;
        require(TugPilot.dragHeading(0f, 0f, 1f) == 0f, "Did not head straight on with the goal past the tug");
        require(TugPilot.dragHeading(0f, 40f, -1f) == 40f, "Did not head for a goal within MAX_SWING of the rope");
        require(TugPilot.dragHeading(0f, 120f, -1f) == swing && TugPilot.dragHeading(0f, -120f, 1f) == -swing,
                "Did not swing round the catch toward a goal off to one side");
        require(TugPilot.dragHeading(0f, 180f, 1f) == swing && TugPilot.dragHeading(0f, 180f, -1f) == -swing,
                "Did not keep its side with the catch between the tug and its goal");
        for (int angle = 0; angle < 360; angle += 15) {
            float heading = TugPilot.dragHeading(0f, angle, 1f);
            require(Math.cos(Math.toRadians(heading)) >= Math.cos(Math.toRadians(swing)) - 1e-4,
                    "Heading slackens the rope for a goal at " + angle + " degrees");
        }

        require(!TugDriveAI.shouldHaul(false, 5f, 200f), "Hauled on a slack line");
        require(TugDriveAI.shouldHaul(true, 2f, 0f), "Did not haul a heavy catch");
        require(TugDriveAI.shouldHaul(true, 0.3f, 50f), "Did not haul a light catch pulling away");
        require(!TugDriveAI.shouldHaul(true, 0.3f, 0f), "Hauled a light catch that follows");
        require(TugDriveAI.shouldEscape(0.2f, true) && !TugDriveAI.shouldEscape(0.2f, false)
                && !TugDriveAI.shouldEscape(0.9f, true), "Escape rule changed");

        System.out.println("Tug AI checks: harpoon target scoring, line-of-fire distance, shield contact, drag heading"
                + " and drive use passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
