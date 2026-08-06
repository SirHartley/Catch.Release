package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.treasure.MinigameTreasure;
import catchrelease.campaign.fish.treasure.TreasureAward;
import catchrelease.campaign.fish.treasure.TreasureRoller;
import catchrelease.rendering.helper.Disc;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.RoundedBorder;
import catchrelease.rendering.plugins.WarpGrid;
import catchrelease.rendering.plugins.WarpedRectRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the catch and takes the input for it. Rules live in {@link FishingMinigame}. Hold the left
 * button to lift the bar, let go and it falls.
 */
public class FishingMinigamePanel implements CustomUIPanelPlugin {

    public interface Listener {
        void onMinigameEnded(boolean caught);
    }

    protected FishingMinigame minigame;
    protected Listener listener;

    /** Rolled before the catch begins; shown only on a win. */
    protected FishCatch specimen;

    /** For the log, read once on a win. */
    protected SectorEntityToken where;
    protected FishLogEntry.Method method;

    protected PositionAPI position;

    /** Rebuilt each frame from the panel's position. */
    protected FishingMinigameLayout layout;
    protected boolean reeling = false;
    protected boolean reported = false;

    /** Runs once the fish is landed; holds the dialog open while it does. */
    transient protected CatchCelebration celebration;

    transient protected CatchResultPanel result;

    /** Null when nothing else came up. */
    transient protected LootResultPanel lootResult;

    /** Everything held onto during the catch, resolved once at the end. */
    protected final List<TreasureAward> lootAwards = new ArrayList<>();
    protected boolean treasureResolved = false;

    /** Held after a fish is lost so the result stays readable before the dialog closes itself. */
    protected float endLingerLeft = FishConstants.MINIGAME_END_LINGER;

    /** Drives the icon's twitch; visual only, never affects where the bar has to be. */
    protected float jitterTime = 0f;

    transient protected SpriteAPI fishSprite;
    transient protected boolean fishSpriteChecked = false;

    /** Track backing and its warp; built on first use. */
    transient protected SpriteAPI backgroundSprite;
    transient protected boolean backgroundChecked = false;
    transient protected WarpGrid warp;

    public FishingMinigamePanel(FishingMinigame minigame, FishCatch specimen, SectorEntityToken where,
                               FishLogEntry.Method method, Listener listener) {
        this.minigame = minigame;
        this.specimen = specimen;
        this.where = where;
        this.method = method;
        this.listener = listener;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
    }

    @Override
    public void advance(float amount) {
        jitterTime += amount;

        getWarp().advance(amount);

        if (minigame.isRunning()) {
            minigame.advance(amount, reeling);
            return;
        }

        if (minigame.isCaught()) {
            //only on a win - losing the fish also loses whatever was taken
            resolveTreasure();

            advanceCaught(amount);
            return;
        }

        //closes itself once the linger has elapsed
        endLingerLeft -= amount;
        if (endLingerLeft > 0f) return;

        end(false);
    }

    /** Puts up the readout and waits; does not close the dialog, the player does that. */
    protected void advanceCaught(float amount) {
        if (result == null) {
            result = new CatchResultPanel(specimen, where, method);

            if (!lootAwards.isEmpty()) lootResult = new LootResultPanel(lootAwards);

            //celebration reads its centre off the layout at render time, after the readout has settled
            if (CrabWares.CELEBRATION.isOn()) {
                celebration = new CatchCelebration(minigame.getFish());
            }
        }

        result.advance(amount);

        //loot tally starts only after the fish tally finishes, so they don't sound-overlap
        if (lootResult != null) {
            //the rain runs from the moment the card is up; only the list waits its turn
            lootResult.advanceBackdrop(amount);

            if (result.isComplete()) lootResult.advance(amount);
        }

        if (celebration != null) celebration.advance(amount);
    }

    /** Adds taken treasure to the hold, once, only on a landed fish. */
    protected void resolveTreasure() {
        if (treasureResolved) return;
        treasureResolved = true;

        for (MinigameTreasure treasure : minigame.getTakenTreasures()) {
            lootAwards.add(TreasureRoller.award(treasure.rarity, minigame.getTackle().shipTackle));
        }
    }

    /** Reports the outcome once. */
    protected void end(boolean caught) {
        if (reported) return;
        reported = true;

        if (listener != null) listener.onMinigameEnded(caught);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (result != null) {
                processResultInput(event);
                continue;
            }

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

    /**
     * Once the readout is up, Escape accepts the catch and closes (rather than giving up, as it did
     * mid-catch). Any other input skips straight to the full readout, or closes it if already full.
     */
    protected void processResultInput(InputEventAPI event) {
        if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
            event.consume();
            end(true);
            return;
        }

        if (!event.isKeyDownEvent() && !event.isLMBDownEvent() && !event.isRMBDownEvent()) return;

        event.consume();

        if (isReadoutComplete()) {
            end(true);
        } else {
            result.revealAll();
            if (lootResult != null) lootResult.revealAll();
        }
    }

    /** True only once both cards are up, so closing mid-tally never skips the unread half. */
    protected boolean isReadoutComplete() {
        return result.isComplete() && (lootResult == null || lootResult.isComplete());
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
        renderTreasure(layout, alphaMult);
        renderMeter(layout, alphaMult);

        //result rendered first so its geometry is settled before the celebration centres on it
        if (result != null) result.render(layout, getFishSprite(), alphaMult);
        if (lootResult != null) lootResult.render(layout, alphaMult);
        if (celebration != null) celebration.render(layout, getFishSprite(), alphaMult);
    }

    /**
     * Seam for custom framing: draw against {@link FishingMinigameLayout#frameX} and friends to stay
     * lined up with the track. Real UI elements (needing layout/mouse-over) belong in the dialog
     * plugin's addFramingElements() instead.
     */
    protected void renderFrame(FishingMinigameLayout layout, float alphaMult) {
        drawDressing(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight, alphaMult);
        drawDressing(layout.meterX, layout.meterY, layout.meterWidth, layout.meterHeight, alphaMult);
    }

    /** Bright rounded outline just off the bar, plus a dimmer one outside it, in the UI's player colour. */
    protected void drawDressing(float x, float y, float width, float height, float alphaMult) {
        float inset = FishConstants.MINIGAME_BORDER_INSET;
        float spacing = FishConstants.MINIGAME_BORDER_SPACING;

        //outer drawn first so the bright line lands on top at the corners
        drawBorder(x, y, width, height, inset + spacing,
                Misc.getDarkPlayerColor(), FishConstants.MINIGAME_BORDER_OUTER_ALPHA * alphaMult);

        drawBorder(x, y, width, height, inset,
                Misc.getBrightPlayerColor(), FishConstants.MINIGAME_BORDER_ALPHA * alphaMult);
    }

    /** One outline, grown out by {@code offset} on every side. */
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

    /** Hyperspace backing behind the fish, warped, fading dark towards the bottom. */
    protected void renderTrack(FishingMinigameLayout layout, float alphaMult) {
        //solid black first so the backing reads against the dialog rather than through it
        drawQuad(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight,
                Color.BLACK, 0.9f * alphaMult);

        SpriteAPI background = getBackgroundSprite();

        if (background != null) {
            WarpedRectRenderer.render(background, getWarp(),
                    layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight,
                    Color.WHITE, FishConstants.MINIGAME_TRACK_BG_ALPHA * alphaMult,
                    FishConstants.MINIGAME_TRACK_BG_ZOOM);
        }

        drawVerticalGradient(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight,
                Color.BLACK,
                FishConstants.MINIGAME_TRACK_FADE_BOTTOM * alphaMult,
                FishConstants.MINIGAME_TRACK_FADE_TOP * alphaMult);

        //player-colour tint tying the track and meter bars together as one UI piece
        drawQuad(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight,
                Misc.getDarkPlayerColor(), 0.05f * alphaMult);
    }

    /** Null if the sprite is missing; the track just goes dark. */
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
        float y = layout.getTrackY(minigame.getBarPosition());
        float x = layout.trackX;
        float w = layout.trackWidth;

        boolean holding = minigame.isFishInBar();
        Color color = holding ? Misc.getPositiveHighlightColor() : Misc.getGrayColor();
        float alpha = (holding ? FishConstants.BAR_ALPHA_HOLDING : FishConstants.BAR_ALPHA_EMPTY) * alphaMult;

        //full alpha at top/bottom, thinning through the middle, so the edges read and the middle is see-through
        float mid = height * 0.5f;
        drawVerticalGradient(x, y, w, mid, color, alpha, alpha * FishConstants.BAR_CENTER_MULT);
        drawVerticalGradient(x, y + mid, w, height - mid, color, alpha * FishConstants.BAR_CENTER_MULT, alpha);

        //bright edge + inset dark line reads as a lip, lifting the bar above the track
        float inset = FishConstants.BAR_BORDER_INNER_INSET;

        RoundedBorder.draw(x + inset, y + inset, w - inset * 2f, height - inset * 2f,
                FishConstants.BAR_BORDER_RADIUS, Color.BLACK,
                Math.min(1f, alpha * FishConstants.BAR_BORDER_INNER_ALPHA),
                FishConstants.BAR_BORDER_WIDTH);

        RoundedBorder.draw(x, y, w, height, FishConstants.BAR_BORDER_RADIUS, color,
                Math.min(1f, alpha * FishConstants.BAR_BORDER_MULT), FishConstants.BAR_BORDER_WIDTH);
    }

    protected void renderFish(FishingMinigameLayout layout, float alphaMult) {
        float size = FishConstants.MINIGAME_FISH_ICON_SIZE;

        //jitter is visual only; the hit position the rules use is unaffected
        float centerX = layout.getTrackCenterX() + getJitter(0f);
        float centerY = layout.getTrackY(minigame.getFishPosition()) + getJitter(1.7f);

        SpriteAPI sprite = getTrackSprite();

        if (sprite == null) {
            //fallback marker; not the rarity colour, which would give away what's on the line
            drawQuad(centerX - size * 0.25f, centerY - size * 0.25f, size * 0.5f, size * 0.5f,
                    Misc.getBrightPlayerColor(), alphaMult);
            return;
        }

        sprite.setSize(size, size);
        sprite.setAlphaMult(alphaMult);
        sprite.renderAtCenter(centerX, centerY);
    }

    /** The treasure, if any: a stand-in icon with its time-left bar underneath. */
    protected void renderTreasure(FishingMinigameLayout layout, float alphaMult) {
        MinigameTreasure treasure = minigame.getTreasure();
        if (treasure == null || !treasure.isActive()) return;

        float centerX = layout.getTrackCenterX();
        float centerY = layout.getTrackY(treasure.position);

        //rarity-colour wash behind the icon so tier reads before the icon does
        Disc.draw(centerX, centerY, FishConstants.TREASURE_ICON_SIZE * 0.9f, treasure.rarity.color,
                0.5f * alphaMult, 0f, true);

        SpriteAPI sprite = SpriteLoader.loadSprite(FishConstants.TREASURE_ICON);
        if (sprite != null) {
            sprite.setSize(FishConstants.TREASURE_ICON_SIZE, FishConstants.TREASURE_ICON_SIZE);
            sprite.setNormalBlend();
            sprite.setAlphaMult(alphaMult);
            sprite.renderAtCenter(centerX, centerY);
        }

        //ring shrinks from full icon size down to TREASURE_RING_END as it's held
        float held = treasure.getHeldFraction();
        if (held > 0f) {
            float from = FishConstants.TREASURE_ICON_SIZE;
            float to = FishConstants.TREASURE_ICON_SIZE * 0.5f * FishConstants.TREASURE_RING_END;

            Disc.drawOutline(centerX, centerY, from + (to - from) * held,
                    treasure.rarity.color, alphaMult, 2f);
        }

        renderTreasureClock(treasure, centerX, centerY, alphaMult);
    }

    /** Time left; drains rather than fills. */
    protected void renderTreasureClock(MinigameTreasure treasure, float centerX, float centerY,
                                       float alphaMult) {

        float width = FishConstants.TREASURE_BAR_WIDTH;
        float height = FishConstants.TREASURE_BAR_HEIGHT;

        float x = centerX - width * 0.5f;
        float y = centerY - FishConstants.TREASURE_ICON_SIZE * 0.5f
                - FishConstants.TREASURE_BAR_GAP - height;

        drawQuad(x, y, width, height, Color.BLACK, 0.6f * alphaMult);
        drawQuad(x, y, width * treasure.getTimeLeft(), height, treasure.rarity.color, 0.9f * alphaMult);
    }

    /** Landing progress, beside the track. */
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
     * Icon twitch from three non-aligned sine waves (avoids a repeating beat or per-frame jumps).
     * Scales up with the fish's current velocity.
     *
     * @param offset phase shift so both axes don't wobble identically
     */
    protected float getJitter(float offset) {
        float time = (jitterTime + offset) * FishConstants.MINIGAME_FISH_JITTER_SPEED;

        float wobble = (float) (Math.sin(time) * 0.5f
                + Math.sin(time * 1.73f) * 0.3f
                + Math.sin(time * 2.61f) * 0.2f);

        float effort = 1f + Math.abs(minigame.getFishVelocity()) * FishConstants.MINIGAME_FISH_JITTER_EFFORT;

        return wobble * FishConstants.MINIGAME_FISH_JITTER * minigame.getFish().jitter * effort;
    }

    /** Generic stand-in icon; the real species is withheld until the readout unless sonar is active. */
    protected SpriteAPI getTrackSprite() {
        if (minigame.getTackle().sonar) return getFishSprite();

        return SpriteLoader.loadSprite(FishConstants.MINIGAME_TRACK_ICON);
    }

    /** Loaded on first use, for the celebration and the readout. */
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

    /** Quad with alpha interpolated bottom-to-top; colour stays constant. */
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


    @Override
    public void buttonPressed(Object buttonId) {
    }

    public FishingMinigame getMinigame() {
        return minigame;
    }
}
