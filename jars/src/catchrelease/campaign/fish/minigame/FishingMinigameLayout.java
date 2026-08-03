package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.ui.PositionAPI;

/**
 * Where the parts of the catch sit on screen, worked out once per frame from the panel's own
 * position.
 * <p>
 * Split out so custom framing art has something to line up against: anything added later can ask for
 * the track or the meter rather than recomputing the same offsets and drifting out of step with them.
 * All values are screen coordinates, with y going up.
 */
public class FishingMinigameLayout {

    public float trackX;
    public float trackY;
    public float trackWidth;
    public float trackHeight;

    public float meterX;
    public float meterY;
    public float meterWidth;
    public float meterHeight;

    /** The whole playfield - track and meter together - for a frame to be drawn around. */
    public float frameX;
    public float frameY;
    public float frameWidth;
    public float frameHeight;

    /**
     * The readout's own panel, off the right edge of the catch's. Everything here is in the same
     * screen coordinates as the rest, so it lines up with the track whatever the dialog does with
     * the panel it was given.
     */
    public float panelX;
    public float panelY;
    public float panelWidth;
    public float panelHeight;

    /** Inside that panel: the content column, and the cargo-square at the top of it. */
    public float resultX;
    public float resultWidth;
    public float boxX;
    public float boxY;
    public float boxSize;

    public FishingMinigameLayout(PositionAPI position) {
        trackWidth = FishConstants.MINIGAME_TRACK_WIDTH;
        trackHeight = FishConstants.MINIGAME_TRACK_HEIGHT;
        meterWidth = FishConstants.MINIGAME_METER_WIDTH;
        meterHeight = trackHeight;

        float totalWidth = trackWidth + FishConstants.MINIGAME_METER_GAP + meterWidth;

        trackX = position.getCenterX() - totalWidth * 0.5f;
        trackY = position.getCenterY() - trackHeight * 0.5f;

        meterX = trackX + trackWidth + FishConstants.MINIGAME_METER_GAP;
        meterY = trackY;

        //a panel of its own, off the right edge of this one - not a column inside it
        panelX = position.getX() + position.getWidth() + FishConstants.MINIGAME_RESULT_GAP;
        panelY = trackY - FishConstants.MINIGAME_FRAME_PAD;
        panelWidth = FishConstants.MINIGAME_RESULT_WIDTH;
        panelHeight = trackHeight + FishConstants.MINIGAME_FRAME_PAD * 2f;

        resultX = panelX + FishConstants.MINIGAME_RESULT_PAD;
        resultWidth = panelWidth - FishConstants.MINIGAME_RESULT_PAD * 2f;

        boxSize = FishConstants.MINIGAME_RESULT_BOX;
        boxX = resultX + (resultWidth - boxSize) * 0.5f;
        boxY = panelY + panelHeight - FishConstants.MINIGAME_RESULT_PAD - boxSize;

        frameX = trackX - FishConstants.MINIGAME_FRAME_PAD;
        frameY = trackY - FishConstants.MINIGAME_FRAME_PAD;
        frameWidth = totalWidth + FishConstants.MINIGAME_FRAME_PAD * 2f;
        frameHeight = trackHeight + FishConstants.MINIGAME_FRAME_PAD * 2f;
    }

    /** Screen y of a point in the track, given as a 0..1 position from the bottom. */
    public float getTrackY(float fraction) {
        return trackY + fraction * trackHeight;
    }

    public float getTrackCenterX() {
        return trackX + trackWidth * 0.5f;
    }
}
