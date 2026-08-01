package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

/**
 * Draws the catch and takes the input for it. The rules live in {@link FishingMinigame}; this is the
 * screen and the mouse.
 * <p>
 * Hold the left button to lift the bar, let go and it falls. Everything is drawn from the panel's own
 * position, so it does not care where the dialog puts it.
 */
public class FishingMinigamePanel implements CustomUIPanelPlugin {

    public interface Listener {
        void onMinigameEnded(boolean caught);
    }

    protected FishingMinigame minigame;
    protected Listener listener;

    protected PositionAPI position;

    /** Rebuilt each frame from the panel's position; also what custom framing should line up against. */
    protected FishingMinigameLayout layout;
    protected boolean reeling = false;
    protected boolean reported = false;

    /** Held after the game ends, so the result is readable before the dialog closes itself. */
    protected float endLingerLeft = FishConstants.MINIGAME_END_LINGER;

    transient protected SpriteAPI fishSprite;
    transient protected boolean fishSpriteChecked = false;

    public FishingMinigamePanel(FishingMinigame minigame, Listener listener) {
        this.minigame = minigame;
        this.listener = listener;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
    }

    @Override
    public void advance(float amount) {
        if (minigame.isRunning()) {
            minigame.advance(amount, reeling);
            return;
        }

        //let the result sit for a moment rather than the dialog vanishing mid-motion
        endLingerLeft -= amount;
        if (endLingerLeft > 0f || reported) return;

        reported = true;
        if (listener != null) listener.onMinigameEnded(minigame.isCaught());
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (event.isLMBDownEvent()) {
                reeling = true;
                event.consume();
            } else if (event.isLMBUpEvent()) {
                reeling = false;
                event.consume();
            }
        }
    }

    @Override
    public void renderBelow(float alphaMult) {
    }

    @Override
    public void render(float alphaMult) {
        if (position == null) return;

        layout = new FishingMinigameLayout(position);

        renderFrame(layout, alphaMult);
        renderTrack(layout, alphaMult);
        renderBar(layout, alphaMult);
        renderFish(layout, alphaMult);
        renderMeter(layout, alphaMult);
    }

    /**
     * Whatever frames the playfield. Empty on purpose - this is the seam for the custom UI: draw
     * against {@link FishingMinigameLayout#frameX} and friends and it will stay lined up with the
     * track wherever the dialog puts the panel.
     * <p>
     * Anything that wants to be a real UI element rather than a drawn one goes in the dialog plugin's
     * addFramingElements() instead, so it can take part in layout and mouse-over.
     */
    protected void renderFrame(FishingMinigameLayout layout, float alphaMult) {
    }

    protected void renderTrack(FishingMinigameLayout layout, float alphaMult) {
        drawQuad(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight,
                Misc.getDarkPlayerColor(), 0.55f * alphaMult);
    }

    /** The window the player flies. Green while it has the fish, dim while it does not. */
    protected void renderBar(FishingMinigameLayout layout, float alphaMult) {
        float height = minigame.getBarHeightFraction() * layout.trackHeight;
        Color color = minigame.isFishInBar() ? Misc.getPositiveHighlightColor() : Misc.getGrayColor();

        drawQuad(layout.trackX, layout.getTrackY(minigame.getBarPosition()), layout.trackWidth, height,
                color, 0.45f * alphaMult);
    }

    protected void renderFish(FishingMinigameLayout layout, float alphaMult) {
        float size = FishConstants.MINIGAME_FISH_ICON_SIZE;
        float centerX = layout.getTrackCenterX();
        float centerY = layout.getTrackY(minigame.getFishPosition());

        SpriteAPI sprite = getFishSprite();

        if (sprite == null) {
            //no icon for this row - a marker is better than nothing to aim at
            drawQuad(centerX - size * 0.25f, centerY - size * 0.25f, size * 0.5f, size * 0.5f,
                    minigame.getFish().rarity.color, alphaMult);
            return;
        }

        sprite.setSize(size, size);
        sprite.setAlphaMult(alphaMult);
        sprite.renderAtCenter(centerX, centerY);
    }

    /** Progress towards landing it, beside the track. */
    protected void renderMeter(FishingMinigameLayout layout, float alphaMult) {
        drawQuad(layout.meterX, layout.meterY, layout.meterWidth, layout.meterHeight,
                Misc.getDarkPlayerColor(), 0.55f * alphaMult);

        Color color = minigame.getProgress() < FishConstants.MINIGAME_METER_DANGER
                ? Misc.getNegativeHighlightColor()
                : Misc.getPositiveHighlightColor();

        drawQuad(layout.meterX, layout.meterY, layout.meterWidth, minigame.getProgress() * layout.meterHeight,
                color, 0.8f * alphaMult);
    }

    /** The fish's own icon from the table, loaded on first use. */
    protected SpriteAPI getFishSprite() {
        if (fishSpriteChecked) return fishSprite;
        fishSpriteChecked = true;

        FishSpec fish = minigame.getFish();
        if (fish.icon == null || fish.icon.isEmpty()) return null;

        try {
            Global.getSettings().loadTexture(fish.icon);
            fishSprite = Global.getSettings().getSprite(fish.icon);
        } catch (IOException e) {
            Global.getLogger(FishingMinigamePanel.class).warn("No icon for fish " + fish.id + ": " + fish.icon);
            fishSprite = null;
        }

        return fishSprite;
    }

    /** Flat rectangle in screen coordinates, with everything it touches pushed and popped. */
    protected static void drawQuad(float x, float y, float width, float height, Color color, float alpha) {
        if (width <= 0f || height <= 0f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    /** Dev control presses arrive here, routed from the buttons the dialog plugin adds. */
    @Override
    public void buttonPressed(Object buttonId) {
        if (!(buttonId instanceof DevControl)) return;

        switch ((DevControl) buttonId) {
            case DIFFICULTY_DOWN:
                minigame.setDifficulty(minigame.getDifficulty() - FishConstants.MINIGAME_DIFFICULTY_STEP);
                break;
            case DIFFICULTY_UP:
                minigame.setDifficulty(minigame.getDifficulty() + FishConstants.MINIGAME_DIFFICULTY_STEP);
                break;
            case SPEED_DOWN:
                minigame.setMotionSpeed(minigame.getMotionSpeed() - FishConstants.MINIGAME_SPEED_STEP);
                break;
            case SPEED_UP:
                minigame.setMotionSpeed(minigame.getMotionSpeed() + FishConstants.MINIGAME_SPEED_STEP);
                break;
            case MOTION:
                minigame.setMotion(getNextMotion(minigame.getMotion()));
                break;
            case RESTART:
                minigame.restart();
                reported = false;
                endLingerLeft = FishConstants.MINIGAME_END_LINGER;
                break;
        }
    }

    protected static FishMotion getNextMotion(FishMotion current) {
        FishMotion[] values = FishMotion.values();

        return values[(current.ordinal() + 1) % values.length];
    }

    public FishingMinigame getMinigame() {
        return minigame;
    }
}
