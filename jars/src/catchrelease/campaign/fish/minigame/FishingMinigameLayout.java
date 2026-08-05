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

    /**
     * The loot card's panel, off the LEFT edge - the readout's mirror in width and box size, but
     * not in height: it hugs its content, and its square is pinned level with the readout's, so
     * the two flank the catch as a pair of exhibits rather than a pair of pillars.
     */
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

        //a panel of its own, off the right edge of this one - not a column inside it. It takes the
        //dialog panel's full vertical extent rather than the playfield frame's, so the two stand as
        //a pair instead of the readout hanging short beside the catch
        panelX = position.getX() + position.getWidth() + FishConstants.MINIGAME_RESULT_GAP;
        panelY = position.getY();
        panelWidth = FishConstants.MINIGAME_RESULT_WIDTH;
        panelHeight = position.getHeight();

        resultX = panelX + FishConstants.MINIGAME_RESULT_PAD;
        resultWidth = panelWidth - FishConstants.MINIGAME_RESULT_PAD * 2f;

        boxSize = FishConstants.MINIGAME_RESULT_BOX;
        boxX = resultX + (resultWidth - boxSize) * 0.5f;
        boxY = panelY + panelHeight - FishConstants.MINIGAME_RESULT_PAD - boxSize;

        //the readout's mirror, off the other edge and at the readout's own measurements, so a
        //catch with both up reads as one interface with a pane on each side. Its right edge is
        //the fixed one: growing wider means growing leftward, away from the catch
        lootPanelWidth = FishConstants.MINIGAME_RESULT_WIDTH;
        lootPanelX = position.getX() - FishConstants.MINIGAME_RESULT_GAP - lootPanelWidth;
        lootPanelY = position.getY();
        lootPanelHeight = position.getHeight();

        lootX = lootPanelX + FishConstants.MINIGAME_RESULT_PAD;
        lootWidth = lootPanelWidth - FishConstants.MINIGAME_RESULT_PAD * 2f;

        lootBoxX = lootX + (lootWidth - boxSize) * 0.5f;
        lootBoxY = lootPanelY + lootPanelHeight - FishConstants.MINIGAME_RESULT_PAD - boxSize;

        //the extra is on the frame alone - the track and meter hold their ground, the frame
        //reaches further left around them
        frameX = trackX - FishConstants.MINIGAME_FRAME_PAD - FishConstants.MINIGAME_FRAME_EXTRA_LEFT;
        frameY = trackY - FishConstants.MINIGAME_FRAME_PAD;
        frameWidth = totalWidth + FishConstants.MINIGAME_FRAME_PAD * 2f
                + FishConstants.MINIGAME_FRAME_EXTRA_LEFT;
        frameHeight = trackHeight + FishConstants.MINIGAME_FRAME_PAD * 2f;
    }

    /**
     * Widens the readout's panel to hold something too wide for it, and puts the column back in the
     * middle of whatever width that leaves.
     * <p>
     * A floor rather than a size: the panel is a fixed width for the fish that fit, and only the
     * ones that do not get to argue with it. Capped, because the name is read off a table and a
     * long enough one would otherwise walk the panel off the side of the screen.
     */
    public void fitResultContent(float contentWidth) {
        float wanted = contentWidth + FishConstants.MINIGAME_RESULT_PAD * 2f;

        panelWidth = Math.max(FishConstants.MINIGAME_RESULT_WIDTH,
                Math.min(wanted, FishConstants.MINIGAME_RESULT_MAX_WIDTH));

        resultX = panelX + FishConstants.MINIGAME_RESULT_PAD;
        resultWidth = panelWidth - FishConstants.MINIGAME_RESULT_PAD * 2f;

        boxX = resultX + (resultWidth - boxSize) * 0.5f;
    }

    /**
     * Drops the readout's content column to the middle of its panel. The panel is taller than the
     * column ever is, and a column pinned to the top left the difference as a void under the last
     * line; centred, the spare height splits evenly, the way the playfield sits in its own panel.
     * <p>
     * The height has to come from the readout, since only it knows its fonts and how many lines it
     * ended up with. {@code boxY} stays at its top-anchored default until this is called.
     *
     * @param headroom space to keep clear above the box, for anything that floats over it - the
     *                 record banner. Centred as part of the column and held inside the pad, so the
     *                 floater neither unbalances the card nor gets pushed off the top of it.
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

    /**
     * The middle of the readout's cargo-square - where the specimen is shown, and so where anything
     * about the specimen should centre itself. Only settled once the readout has laid itself out;
     * before {@code fitResultContent} and {@code centerResultContent} run this is the top-anchored
     * default.
     */
    public float getBoxCenterX() {
        return boxX + boxSize * 0.5f;
    }

    public float getBoxCenterY() {
        return boxY + boxSize * 0.5f;
    }

    /**
     * The loot card's version of {@link #fitResultContent}: same floor, same cap, but the growth
     * goes leftward - the card's right edge holds its distance from the catch.
     */
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
     * Puts the loot card's square level with the readout's, and leaves its frame alone.
     * <p>
     * The frame is the readout's frame: the same width, the same full height, standing on the same
     * line. Shrinking it to hug its rows was tried, on the reasoning that a card holding three
     * things should not be as tall as one holding a whole tally - and what it actually produced was
     * two cards of different heights either side of the track, which reads as one of them having
     * gone wrong rather than as either of them fitting its contents.
     * <p>
     * The square is pinned rather than centred, which is the one thing worth keeping from that: the
     * two icons sit on the same line, so the eye crosses from one card to the other without
     * travelling. {@code boxY} is already settled by the time this runs, because the readout
     * renders first.
     *
     * @param contentHeight what the card holds, unused now the frame no longer bends to it - kept
     *                      so the caller still measures before it draws, which is what stops the
     *                      column resizing while its rows are still arriving
     */
    public void alignLootContent(float contentHeight) {
        lootBoxY = boxY;
    }

}
