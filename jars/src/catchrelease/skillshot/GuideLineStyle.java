package catchrelease.skillshot;

/**
 * How the reticule guide lines are drawn. Set one per reticule with
 * {@link catchrelease.skillshot.render.BaseReticuleRenderer#withLineStyle(GuideLineStyle)}, or change
 * {@link SkillshotSettings#GUIDE_LINE_STYLE} to move the default for every reticule that does not
 * ask for a specific one.
 * <p>
 * The dash and dot lengths live in {@link SkillshotSettings} so they can be retuned without touching
 * this enum. They are in screen pixels, so a dashed line looks the same however far the campaign map
 * is zoomed out.
 */
public enum GuideLineStyle {

    /** One unbroken line. */
    SOLID,

    /** Long strokes with gaps between them - reads as a path. */
    DASHED,

    /** Short ticks with wide gaps - quieter, good for lines that are only a hint. */
    DOTTED;

    /** Length of one drawn stroke in screen pixels; 0 for {@link #SOLID}. */
    public float getSegmentPx() {
        switch (this) {
            case DASHED:
                return SkillshotSettings.GUIDE_LINE_DASH_PX;
            case DOTTED:
                return SkillshotSettings.GUIDE_LINE_DOT_PX;
            default:
                return 0f;
        }
    }

    /** Length of the gap after each stroke, in screen pixels; 0 for {@link #SOLID}. */
    public float getGapPx() {
        switch (this) {
            case DASHED:
                return SkillshotSettings.GUIDE_LINE_DASH_GAP_PX;
            case DOTTED:
                return SkillshotSettings.GUIDE_LINE_DOT_GAP_PX;
            default:
                return 0f;
        }
    }
}
