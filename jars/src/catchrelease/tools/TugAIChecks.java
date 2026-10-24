package catchrelease.tools;

import catchrelease.combat.tug.HarpoonAutofireAI;
import catchrelease.combat.tug.HarpoonAutofireAI.Shield;
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

        System.out.println("Tug AI checks: harpoon target scoring and line-of-fire distance passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
