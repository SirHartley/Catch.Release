package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.RoundedBorder;
import catchrelease.rendering.plugins.WarpGrid;
import catchrelease.rendering.plugins.WarpedRectRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
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

    /** Drives the icon's twitch. Visual only - it never moves what the bar has to be over. */
    protected float jitterTime = 0f;

    transient protected SpriteAPI fishSprite;
    transient protected boolean fishSpriteChecked = false;

    /** The track's backing, and the warp that keeps it swimming. Built on first use. */
    transient protected SpriteAPI backgroundSprite;
    transient protected boolean backgroundChecked = false;
    transient protected WarpGrid warp;

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
        jitterTime += amount;

        //ahead of anything that returns early: the backing swims whether or not the catch is still on
        getWarp().advance(amount);

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
            } else if (event.getEventValue() == Keyboard.KEY_ESCAPE){
                event.consume();
                minigame.setEscaped();
                endLingerLeft = 0f;
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
        drawDressing(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight, alphaMult);
        drawDressing(layout.meterX, layout.meterY, layout.meterWidth, layout.meterHeight, alphaMult);
    }

    /**
     * The border dressing around one bar: a bright rounded outline just off it, and a dimmer one
     * outside that. Drawn in the player's own UI colour, so it matches the rest of the interface
     * rather than being a colour of its own.
     */
    protected void drawDressing(float x, float y, float width, float height, float alphaMult) {
        float inset = FishConstants.MINIGAME_BORDER_INSET;
        float spacing = FishConstants.MINIGAME_BORDER_SPACING;

        //outer first, so the bright line lands on top of it where they meet at the corners
        drawBorder(x, y, width, height, inset + spacing,
                Misc.getDarkPlayerColor(), FishConstants.MINIGAME_BORDER_OUTER_ALPHA * alphaMult);

        drawBorder(x, y, width, height, inset,
                Misc.getBrightPlayerColor(), FishConstants.MINIGAME_BORDER_ALPHA * alphaMult);
    }

    /** One outline, grown out from the bar by {@code offset} on every side. */
    protected void drawBorder(float x, float y, float width, float height, float offset,
                              Color color, float alpha) {
        RoundedBorder.draw(
                x - offset,
                y - offset,
                width + offset * 2f,
                height + offset * 2f,
                FishConstants.MINIGAME_BORDER_RADIUS + offset,
                color,
                alpha,
                FishConstants.MINIGAME_BORDER_WIDTH);
    }

    /**
     * The track the fish is played in: hyperspace behind it, swimming, fading into the dark towards
     * the bottom.
     */
    protected void renderTrack(FishingMinigameLayout layout, float alphaMult) {
        //black underneath, so the backing reads against the dialog rather than through it
        drawQuad(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight,
                Color.BLACK, 0.9f * alphaMult);

        SpriteAPI background = getBackgroundSprite();

        if (background != null) {
            WarpedRectRenderer.render(background, getWarp(),
                    layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight,
                    Color.WHITE, FishConstants.MINIGAME_TRACK_BG_ALPHA * alphaMult,
                    FishConstants.MINIGAME_TRACK_BG_ZOOM);
        }

        //deepening dark down the bar. Over the backing, under everything in play
        drawVerticalGradient(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight,
                Color.BLACK,
                FishConstants.MINIGAME_TRACK_FADE_BOTTOM * alphaMult,
                FishConstants.MINIGAME_TRACK_FADE_TOP * alphaMult);

        //the tint the track always had, holding the two bars together as one piece of UI
        drawQuad(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight,
                Misc.getDarkPlayerColor(), 0.05f * alphaMult);
    }

    /** The track's backing, loaded on first use. Null if the sprite is missing; the bar just goes dark. */
    protected SpriteAPI getBackgroundSprite() {
        if (backgroundChecked) return backgroundSprite;
        backgroundChecked = true;

        backgroundSprite = SpriteLoader.getSprite("hs_bg");

        return backgroundSprite;
    }

    protected WarpGrid getWarp() {
        if (warp == null) {
            warp = new WarpGrid(
                    FishConstants.MINIGAME_TRACK_BG_WARP_CELLS,
                    FishConstants.MINIGAME_TRACK_BG_WARP_CELLS,
                    FishConstants.MINIGAME_TRACK_BG_WARP_MIN,
                    FishConstants.MINIGAME_TRACK_BG_WARP_MAX,
                    FishConstants.MINIGAME_TRACK_BG_WARP_RATE);
        }

        return warp;
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

        //the twitch goes on the drawing only. The fish the bar has to cover is exactly where the
        //rules say it is, so nothing here can make a catch feel stolen
        float centerX = layout.getTrackCenterX() + getJitter(0f);
        float centerY = layout.getTrackY(minigame.getFishPosition()) + getJitter(1.7f);

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

    /**
     * A few pixels of twitch, from three sines that do not divide into each other - so it never
     * repeats on a beat the eye can catch, and never jumps the way frame-by-frame randomness would.
     * <p>
     * A fish thrashing about is livelier than one gliding, so the amount rises with how fast it is
     * actually moving.
     *
     * @param offset phase shift, so the two axes are not the same wobble twice
     */
    protected float getJitter(float offset) {
        float time = (jitterTime + offset) * FishConstants.MINIGAME_FISH_JITTER_SPEED;

        float wobble = (float) (Math.sin(time) * 0.5f
                + Math.sin(time * 1.73f) * 0.3f
                + Math.sin(time * 2.61f) * 0.2f);

        float effort = 1f + Math.abs(minigame.getFishVelocity()) * FishConstants.MINIGAME_FISH_JITTER_EFFORT;

        //the constant is the baseline; the fish's own row says how much of it this one gets
        return wobble * FishConstants.MINIGAME_FISH_JITTER * minigame.getFish().jitter * effort;
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
    /**
     * A quad whose alpha runs from one value at the bottom to another at the top - the colour is the
     * same throughout, only how much of it there is changes.
     */
    protected static void drawVerticalGradient(float x, float y, float width, float height,
                                               Color color, float bottomAlpha, float topAlpha) {
        if (width <= 0f || height <= 0f) return;

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(r, g, b, bottomAlpha);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glColor4f(r, g, b, topAlpha);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

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
