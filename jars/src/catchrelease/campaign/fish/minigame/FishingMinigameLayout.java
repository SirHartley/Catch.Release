package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.ui.PositionAPI;

/**
 * Where the parts of the catch minigame sit on screen, computed once per frame from the panel's
 * position. Split out so other code can query the track/meter position rather than recomputing it.
 * Screen coordinates, y going up.
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

    /** The readout's panel, off the right edge of the catch's playfield. */
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

    /** The loot card's panel, off the left edge - mirrors the readout in width and box size, not height. */
    public float lootPanelX;
    public float lootPanelY;
    public float lootPanelWidth;
    public float lootPanelHeight;
    public float lootX;
    public float lootWidth;
    public float lootBoxX;
    public float lootBoxY;

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

        //takes the dialog panel's full vertical extent, not the playfield frame's
        panelX = position.getX() + position.getWidth() + FishConstants.MINIGAME_RESULT_GAP;
        panelY = position.getY();
        panelWidth = FishConstants.MINIGAME_RESULT_WIDTH;
        panelHeight = position.getHeight();

        resultX = panelX + FishConstants.MINIGAME_RESULT_PAD;
        resultWidth = panelWidth - FishConstants.MINIGAME_RESULT_PAD * 2f;

        boxSize = FishConstants.MINIGAME_RESULT_BOX;
        boxX = resultX + (resultWidth - boxSize) * 0.5f;
        boxY = panelY + panelHeight - FishConstants.MINIGAME_RESULT_PAD - boxSize;

        //right edge is fixed - growing wider grows leftward, away from the catch
        lootPanelWidth = FishConstants.MINIGAME_RESULT_WIDTH;
        lootPanelX = position.getX() - FishConstants.MINIGAME_RESULT_GAP - lootPanelWidth;
        lootPanelY = position.getY();
        lootPanelHeight = position.getHeight();

        lootX = lootPanelX + FishConstants.MINIGAME_RESULT_PAD;
        lootWidth = lootPanelWidth - FishConstants.MINIGAME_RESULT_PAD * 2f;

        lootBoxX = lootX + (lootWidth - boxSize) * 0.5f;
        lootBoxY = lootPanelY + lootPanelHeight - FishConstants.MINIGAME_RESULT_PAD - boxSize;

        //extra reach is on the frame only - track and meter positions are unaffected
        frameX = trackX - FishConstants.MINIGAME_FRAME_PAD - FishConstants.MINIGAME_FRAME_EXTRA_LEFT;
        frameY = trackY - FishConstants.MINIGAME_FRAME_PAD;
        frameWidth = totalWidth + FishConstants.MINIGAME_FRAME_PAD * 2f
                + FishConstants.MINIGAME_FRAME_EXTRA_LEFT;
        frameHeight = trackHeight + FishConstants.MINIGAME_FRAME_PAD * 2f;
    }

    /** Widens the readout panel to fit content, clamped between the default and max width. */
    public void fitResultContent(float contentWidth) {
        float wanted = contentWidth + FishConstants.MINIGAME_RESULT_PAD * 2f;

        panelWidth = Math.max(FishConstants.MINIGAME_RESULT_WIDTH,
                Math.min(wanted, FishConstants.MINIGAME_RESULT_MAX_WIDTH));

        resultX = panelX + FishConstants.MINIGAME_RESULT_PAD;
        resultWidth = panelWidth - FishConstants.MINIGAME_RESULT_PAD * 2f;

        boxX = resultX + (resultWidth - boxSize) * 0.5f;
    }

    /**
     * Vertically centres the readout's content column in its panel. Caller must know its own
     * rendered height (fonts/line count), so {@code boxY} stays top-anchored until this is called.
     *
     * @param headroom space kept clear above the box for a floating overlay (e.g. record banner)
     */
    public void centerResultContent(float contentHeight, float headroom) {
        float total = contentHeight + headroom;
        float centeredTop = panelY + (panelHeight + total) * 0.5f - headroom;
        float highestTop = panelY + panelHeight - FishConstants.MINIGAME_RESULT_PAD - headroom;

        boxY = Math.min(centeredTop, highestTop) - boxSize;
    }

    /** Screen y of a point in the track, given as a 0..1 position from the bottom. */
    public float getTrackY(float fraction) {
        return trackY + fraction * trackHeight;
    }

    public float getTrackCenterX() {
        return trackX + trackWidth * 0.5f;
    }

    /** Centre of the readout's specimen box. Top-anchored default until {@code fitResultContent}/{@code centerResultContent} run. */
    public float getBoxCenterX() {
        return boxX + boxSize * 0.5f;
    }

    public float getBoxCenterY() {
        return boxY + boxSize * 0.5f;
    }

    /** {@link #fitResultContent} for the loot card: same clamping, but grows leftward (right edge fixed). */
    public void fitLootContent(float contentWidth) {
        float wanted = contentWidth + FishConstants.MINIGAME_RESULT_PAD * 2f;
        float right = lootPanelX + lootPanelWidth;

        lootPanelWidth = Math.max(FishConstants.MINIGAME_RESULT_WIDTH,
                Math.min(wanted, FishConstants.MINIGAME_RESULT_MAX_WIDTH));

        lootPanelX = right - lootPanelWidth;
        lootX = lootPanelX + FishConstants.MINIGAME_RESULT_PAD;
        lootWidth = lootPanelWidth - FishConstants.MINIGAME_RESULT_PAD * 2f;

        lootBoxX = lootX + (lootWidth - boxSize) * 0.5f;
    }

    /**
     * Levels the loot card's box with the readout's box; frame height/width match the readout's
     * unconditionally (mismatched card heights read as broken, not content-fitted).
     * Requires the readout to have rendered first, so {@code boxY} is already settled.
     *
     * @param contentHeight unused - kept so callers still measure before drawing, avoiding resize mid-layout
     */
    public void alignLootContent(float contentHeight) {
        lootBoxY = boxY;
    }

}
