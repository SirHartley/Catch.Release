package catchrelease.abilities.rod.constants;

import java.awt.Color;

public class RodConstants {

    public static final Color DRONE_COLOR = Color.ORANGE;
    public static final String SOUND_DRONE_LAUNCH = "catchrelease_drone_launch";
    public static final String SOUND_TARGET_LOCK = "catchrelease_rod_target_lock";
    public static final String SOUND_MOTE_CAUGHT = "catchrelease_rod_catch";
    public static final String SOUND_POND_OPEN = "catchrelease_pond_open_boom";

    public static float DRONE_LAUNCH_OFFSET = 0.5f;
    public static final float DRONE_ORBIT_RADIUS = 60f;
    public static final float DRONE_ROAM_RADIUS = 140f;

    public static final float RING_RADIUS_FALLBACK = 150f;
    public static final float CHASE_MARGIN_FALLBACK = 40f;

    public static final float DRONE_SPEED = 300f;
    public static final float DRONE_STEER_RESPONSE = 0.45f;
    public static final float DRONE_SLOWING_DISTANCE = 120f;
    public static final float DRONE_BRAKE_MARGIN = 0.5f;
    public static final float DRONE_NOISE_STRENGTH = 40f;
    public static final float DRONE_NOISE_FREQUENCY = 0.9f;
    public static final float DRONE_CATCH_DISTANCE = 15f;
    public static final float DRONE_ORBIT_SPEED = 30f;
    public static final float DRONE_JOIN_DISTANCE = 15f;
    public static final float DRONE_JOIN_LEAD_ANGLE = 55f;
    public static final float DRONE_JOIN_ALIGNMENT = 60f;
    public static final float DRONE_FACING_RESPONSE = 0.4f;
    public static final float DRONE_TRIM_RATE = 25f;
    public static final float DRONE_SETTLE_RESPONSE = 1.2f;
    public static final float DRONE_RETURN_ACCELERATION = 0.2f;
    public static final float DRONE_RETURN_MAX_MULT = 3.5f;

    public static final int RING_SEGMENTS = 72;
    public static final float RING_WIDTH = 1.5f;
    public static final int RING_DASH_COUNT = 24;
    public static final float RING_DASH_DUTY = 0.5f;
    public static final float RING_ALPHA_IDLE = 0.02f;
    public static final float RING_ALPHA_ACTIVE = 0.1f;
    public static final float RING_FADE_TIME = 0.5f;
    public static final float RING_PULSE_SPEED = 4f;

    public static final float DRONE_ARRIVAL_DISTANCE = 10f;
    public static final float DRONE_SPRITE_SIZE = 8f;
    public static final float DRONE_TRAIL_SIZE = 5f;
    public static final int DRONE_COUNT_FALLBACK = 1;
    public static final float DRONE_SEARCH_INTERVAL = 0.25f;
}
