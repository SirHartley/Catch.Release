package catchrelease.skillshot;

import java.awt.*;

/**
 * Every id, size and colour the framework uses, in one place.
 * <p>
 * All fields are non-final on purpose - a mod can rewrite them once during startup (before any
 * ability is used) to point at its own sprites and sounds, or to rename the tag if "skillshot"
 * collides with something else.
 */
public class SkillshotSettings {

    /**
     * Abilities carrying this tag in abilities.csv are picked up by the framework's input listener.
     * The ability plugin must also implement {@link catchrelease.skillshot.ability.SkillshotAbility}.
     */
    public static String TAG_SKILLSHOT = "skillshot";

    /** Sprite category used for all lookups - the key of the map in settings.json "graphics". */
    public static String SPRITE_CATEGORY = "fx";

    /** Ring drawn around the player fleet, rotated to face the cursor. */
    public static String SPRITE_FLEET_RETICULE = "skillshot_direction_reticule";

    /** Arrow drawn at the cursor by {@link catchrelease.skillshot.render.DirectionReticuleRenderer}. */
    public static String SPRITE_DIRECTION_ARROW = "skillshot_arrow";

    /** Circle drawn at the cursor by {@link catchrelease.skillshot.render.AreaReticuleRenderer}. */
    public static String SPRITE_AREA_TARGET = "skillshot_target";

    /** Played when the player tries to fire at a position the reticule rejects. */
    public static String SOUND_INVALID_TARGET = "skillshot_denied";

    /** Alpha of the fleet reticule. */
    public static float RETICULE_ALPHA = 0.9f;

    /** Alpha of the cursor-bound sprite, as a multiplier on {@link #RETICULE_ALPHA}. */
    public static float CURSOR_SPRITE_ALPHA_MULT = 0.6f;

    /** Added to the fleet radius to get the fleet reticule size. */
    public static float FLEET_RETICULE_PADDING = 250f;

    /** Edge length of the direction arrow, in world units. */
    public static float DIRECTION_ARROW_SIZE = 170f;

    /** Default diameter of the area reticule, in world units. */
    public static float DEFAULT_AREA_SIZE = 400f;

    /**
     * Width in screen pixels of the optional trajectory / bounds lines drawn by
     * {@link catchrelease.skillshot.render.DirectionReticuleRenderer}.
     */
    public static float GUIDE_LINE_WIDTH = 2f;

    /** Alpha of those lines, as a multiplier on {@link #RETICULE_ALPHA}. */
    public static float GUIDE_LINE_ALPHA_MULT = 0.45f;

    /**
     * Style used by reticules that do not ask for a specific one with
     * {@link catchrelease.skillshot.render.BaseReticuleRenderer#withLineStyle(GuideLineStyle)}.
     */
    public static GuideLineStyle GUIDE_LINE_STYLE = GuideLineStyle.SOLID;

    /**
     * {@link GuideLineStyle#DASHED}: length of one dash, and of the gap after it, in screen pixels.
     * <p>
     * Both get snapped to the nearest pattern GL can stipple - the dash keeps its share of the
     * dash-plus-gap period, and the period rounds to a multiple of 16 pixels - so treat them as the
     * look you are after rather than an exact measurement.
     */
    public static float GUIDE_LINE_DASH_PX = 24f;
    public static float GUIDE_LINE_DASH_GAP_PX = 24f;

    /** {@link GuideLineStyle#DOTTED}: same two lengths, snapped the same way. */
    public static float GUIDE_LINE_DOT_PX = 4f;
    public static float GUIDE_LINE_DOT_GAP_PX = 28f;

    /** Colour of the reticule while the aim point is rejected. */
    public static Color INVALID_COLOR = Color.RED.darker();

    /** Set true to get the framework's state transitions in starsector.log. */
    public static boolean LOG_DEBUG = true;
}
