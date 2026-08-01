package catchrelease.abilities.rod.constants;

import java.awt.Color;

public class RodConstants {

    /** Drone hull and trail colour. */
    public static final Color DRONE_COLOR = Color.CYAN;

    //drones
    /** Radius of the ring the drones fly once they arrive. The reticule is sized to match. */
    public static final float DRONE_ORBIT_RADIUS = 250f;

    /** World units per second on the way out and the way home. */
    public static final float DRONE_SPEED = 300f;

    /** Degrees per second around the ring. */
    public static final float DRONE_ORBIT_SPEED = 60f;

    /** How close counts as arrived, in world units. */
    public static final float DRONE_ARRIVAL_DISTANCE = 20f;

    public static final float DRONE_SPRITE_SIZE = 24f;
    public static final float DRONE_TRAIL_SIZE = 12f;

    /** Used when the upgrade stat is missing entirely, so a fresh save still fishes. */
    public static final int DRONE_COUNT_FALLBACK = 1;

    /** How often the swarm looks for something to catch, in seconds. */
    public static final float DRONE_SEARCH_INTERVAL = 0.25f;

}
