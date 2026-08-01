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
