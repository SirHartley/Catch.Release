package catchrelease.tools;

import catchrelease.combat.lights.BreachLightEffect;
import catchrelease.combat.lights.BreachMoteAI;
import org.lwjgl.util.vector.Vector2f;

public final class BreachLightChecks {

    private BreachLightChecks() {
    }

    public static void main(String[] args) {
        Vector2f origin = new Vector2f(100f, -50f);
        float direction = 135f;
        float[] samples = {0f, 0.25f, 0.5f, 0.75f, 1f};
        for (float u : samples) {
            for (float v : samples) {
                Vector2f at = BreachLightEffect.pointInCone(origin, direction, u, v);
                float dx = at.x - origin.x;
                float dy = at.y - origin.y;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float bearing = (float) Math.toDegrees(Math.atan2(dy, dx));
                float off = Math.abs(((bearing - direction) % 360f + 540f) % 360f - 180f);
                require(distance <= BreachLightEffect.REACH + 0.01f, "mote spawned past the cone's reach");
                require(distance >= BreachLightEffect.SPAWN_NEAR * BreachLightEffect.REACH - 0.01f,
                        "mote spawned on the lamp");
                require(off <= BreachLightEffect.HALF_ANGLE + 0.01f, "mote spawned outside the cone");
            }
        }

        require(BreachMoteAI.alphaAt(0f) == 0f, "mote visible on spawn");
        require(BreachMoteAI.alphaAt(BreachMoteAI.LIFETIME) == 0f, "mote visible at the end of its life");
        require(BreachMoteAI.alphaAt(BreachMoteAI.LIFETIME / 2f) == 1f, "mote not fully lit mid-life");
        require(BreachMoteAI.alphaAt(BreachMoteAI.LIFETIME - BreachMoteAI.FADE_OUT / 2f) < 1f,
                "mote did not fade before expiry");

        System.out.println("BreachLightChecks passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
