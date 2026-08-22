package catchrelease.skillshot;

import java.awt.*;

public class SkillshotSettings {

    public static String TAG_SKILLSHOT = "skillshot";
    public static String SPRITE_CATEGORY = "fx";
    public static String SPRITE_FLEET_RETICULE = "skillshot_direction_reticule";
    public static String SPRITE_DIRECTION_ARROW = "skillshot_arrow";
    public static String SPRITE_AREA_TARGET = "skillshot_target";
    public static String SOUND_INVALID_TARGET = "skillshot_denied";

    public static float RETICULE_ALPHA = 0.9f;
    public static float CURSOR_SPRITE_ALPHA_MULT = 0.6f;
    public static float FLEET_RETICULE_PADDING = 250f;
    public static float DIRECTION_ARROW_SIZE = 20f;
    public static float DEFAULT_AREA_SIZE = 400f;

    public static float GUIDE_LINE_WIDTH = 2f;
    public static float GUIDE_LINE_ALPHA_MULT = 0.45f;
    public static GuideLineStyle GUIDE_LINE_STYLE = GuideLineStyle.SOLID;
    public static float GUIDE_LINE_DASH_PX = 24f;
    public static float GUIDE_LINE_DASH_GAP_PX = 24f;
    public static float GUIDE_LINE_DOT_PX = 4f;
    public static float GUIDE_LINE_DOT_GAP_PX = 28f;

    public static Color INVALID_COLOR = Color.RED.darker();
    public static boolean LOG_DEBUG = true;
}
