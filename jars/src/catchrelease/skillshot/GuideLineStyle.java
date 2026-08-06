package catchrelease.skillshot;

/**
 * How reticule guide lines are drawn. Set per reticule with
 * {@link catchrelease.skillshot.render.BaseReticuleRenderer#withLineStyle(GuideLineStyle)}, or via
 * {@link SkillshotSettings#GUIDE_LINE_STYLE} for the default. Dash/dot lengths are in
 * {@link SkillshotSettings}, in screen pixels so lines look the same at any map zoom.
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
