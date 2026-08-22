package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishermanBycatch;
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
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


public class FishingMinigamePanel implements CustomUIPanelPlugin {

    public interface Listener {
        void onMinigameEnded(boolean caught);
    }

    protected FishingMinigame minigame;
    protected Listener listener;


    protected FishCatch specimen;


    protected SectorEntityToken where;
    protected FishLogEntry.Method method;

    protected PositionAPI position;


    protected FishingMinigameLayout layout;
    protected boolean reeling = false;
    protected boolean reported = false;
    protected boolean failedSoundPlayed = false;


    protected float lineLoopHeldLevel = 0f;


    protected boolean fishCoveredLastFrame;


    transient protected CatchCelebration celebration;

    transient protected CatchResultPanel result;


    transient protected LootResultPanel lootResult;


    protected final List<TreasureAward> lootAwards = new ArrayList<>();
    protected boolean treasureResolved = false;


    protected MinigameTreasure soundTreasure;
    protected boolean treasureGotSoundPlayed = false;


    protected float endLingerLeft = FishConstants.MINIGAME_END_LINGER;


    protected float jitterTime = 0f;


    transient protected SpriteAPI backgroundSprite;
    transient protected boolean backgroundChecked = false;
    transient protected WarpGrid warp;

    transient protected SpriteAPI moteSprite;

    public FishingMinigamePanel(FishingMinigame minigame, FishCatch specimen, SectorEntityToken where,
                               FishLogEntry.Method method, Listener listener) {
        this.minigame = minigame;
        this.specimen = specimen;
        this.where = where;
        this.method = method;
        this.listener = listener;
        this.fishCoveredLastFrame = minigame.isFishInBar();
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
            advanceIndicatorSoundHook();
            advanceTreasureSoundHooks();

            if (minigame.isRunning()) advanceLineSoundLoop(amount);
            return;
        }

        if (minigame.isCaught()) {
            // only on a win - losing the fish also loses whatever was taken
            resolveTreasure();

            advanceCaught(amount);
            return;
        }

        if (!failedSoundPlayed) {
            failedSoundPlayed = true;
            CatchCelebration.playHook(FishConstants.SOUND_FAILED);
        }

        endLingerLeft -= amount;
        if (endLingerLeft > 0f) return;

        end(false);
    }


    protected void advanceLineSoundLoop(float amount) {
        float fadeTime = reeling
                ? FishConstants.LINE_LOOP_HELD_FADE_IN
                : FishConstants.LINE_LOOP_HELD_FADE_OUT;
        float step = fadeTime <= 0f ? 1f : amount / fadeTime;

        if (reeling) {
            lineLoopHeldLevel = Math.min(1f, lineLoopHeldLevel + step);
        } else {
            lineLoopHeldLevel = Math.max(0f, lineLoopHeldLevel - step);
        }

        float volumeRange = FishConstants.LINE_LOOP_HELD_VOLUME - FishConstants.LINE_LOOP_BASE_VOLUME;
        float volume = FishConstants.LINE_LOOP_BASE_VOLUME + volumeRange * lineLoopHeldLevel;
        if (FishConstants.SOUND_LINE_LOOP.isEmpty() || volume <= 0f) return;

        Global.getSoundPlayer().playUILoop(FishConstants.SOUND_LINE_LOOP, 1f, volume);
    }


    protected void advanceIndicatorSoundHook() {
        boolean covered = minigame.isFishInBar();

        if (fishCoveredLastFrame && !covered) {
            float pitch = MathUtils.getRandomNumberInRange(
                    FishConstants.SOUND_INDICATOR_PITCH_MIN,
                    FishConstants.SOUND_INDICATOR_PITCH_MAX);
            CatchCelebration.playHook(FishConstants.SOUND_INDICATOR_CROSS, pitch);
        }

        fishCoveredLastFrame = covered;
    }


    protected void advanceTreasureSoundHooks() {
        MinigameTreasure treasure = minigame.getTreasure();

        if (treasure != soundTreasure) {
            soundTreasure = treasure;
            treasureGotSoundPlayed = false;

            if (treasure != null && treasure.isActive()) {
                CatchCelebration.playHook(FishConstants.SOUND_TREASURE_SPAWN);
            }
        }

        if (treasure == null) return;

        if (treasure.isTaken() && !treasureGotSoundPlayed) {
            treasureGotSoundPlayed = true;
            CatchCelebration.playHook(FishConstants.SOUND_TREASURE_GOT);
        }
    }


    protected void advanceCaught(float amount) {
        if (result == null) {
            CatchCelebration.playHook(FishConstants.SOUND_CAUGHT);
            result = new CatchResultPanel(specimen, where, method);

            // filed beside the species log, after resolveTreasure, so the entry knows its bycatch
            catchrelease.campaign.fish.intel.CatchLogIntel.record(specimen, where, lootAwards);

            if (!lootAwards.isEmpty()) lootResult = new LootResultPanel(lootAwards);

            // celebration reads its centre off the layout at render time, after the readout has settled
            if (CrabWares.CELEBRATION.isOn()) {
                celebration = new CatchCelebration(minigame.getFish());
            }
        }

        result.advance(amount);

        // loot tally starts only after the fish tally finishes, so they don't sound-overlap
        if (lootResult != null) {
            // the rain runs from the moment the card is up; only the list waits its turn
            lootResult.advanceBackdrop(amount);

            if (result.isComplete()) lootResult.advance(amount);
        }

        if (celebration != null) celebration.advance(amount);
    }


    protected void resolveTreasure() {
        if (treasureResolved) return;
        treasureResolved = true;

        if (!minigame.getTakenTreasures().isEmpty()) FishermanBycatch.recordFound();

        for (MinigameTreasure treasure : minigame.getTakenTreasures()) {
            lootAwards.add(TreasureRoller.award(treasure.rarity, minigame.getTackle().shipTackle));
        }
    }


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
                if (!FishConstants.PLAY_LINE_CLICK_ON_RELEASE && minigame.isRunning())
                    CatchCelebration.playHook(FishConstants.SOUND_LINE_CLICK);
                reeling = true;
                event.consume();
            } else if (event.isLMBUpEvent()) {
                if (FishConstants.PLAY_LINE_CLICK_ON_RELEASE && minigame.isRunning())
                    CatchCelebration.playHook(FishConstants.SOUND_LINE_CLICK);
                reeling = false;
                event.consume();
            } else if (event.getEventValue() == Keyboard.KEY_ESCAPE){
                event.consume();
                minigame.setEscaped();
                endLingerLeft = 0f;
            }
        }
    }


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
        renderTreasure(layout, alphaMult);
        // The catch is the thing the player must never lose: it wins every overlap on the track.
        renderFish(layout, alphaMult);
        renderMeter(layout, alphaMult);

        // result rendered first so its geometry is settled before the celebration centres on it
        if (result != null) result.render(layout, getFishSprite(), alphaMult);
        if (lootResult != null) lootResult.render(layout, alphaMult);
        if (celebration != null) celebration.render(layout, getFishSprite(), alphaMult);
    }


    protected void renderFrame(FishingMinigameLayout layout, float alphaMult) {
        drawDressing(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight, alphaMult);
        drawDressing(layout.meterX, layout.meterY, layout.meterWidth, layout.meterHeight, alphaMult);
    }


    protected void drawDressing(float x, float y, float width, float height, float alphaMult) {
        float inset = FishConstants.MINIGAME_BORDER_INSET;
        float spacing = FishConstants.MINIGAME_BORDER_SPACING;

        // outer drawn first so the bright line lands on top at the corners
        drawBorder(x, y, width, height, inset + spacing,
                Misc.getDarkPlayerColor(), FishConstants.MINIGAME_BORDER_OUTER_ALPHA * alphaMult);

        drawBorder(x, y, width, height, inset,
                Misc.getBrightPlayerColor(), FishConstants.MINIGAME_BORDER_ALPHA * alphaMult);
    }


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


    protected void renderTrack(FishingMinigameLayout layout, float alphaMult) {
        // solid black first so the backing reads against the dialog rather than through it
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

        drawQuad(layout.trackX, layout.trackY, layout.trackWidth, layout.trackHeight,
                Misc.getDarkPlayerColor(), 0.05f * alphaMult);
    }


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


    protected void renderBar(FishingMinigameLayout layout, float alphaMult) {
        float height = minigame.getBarHeightFraction() * layout.trackHeight;
        float y = layout.getTrackY(minigame.getBarPosition());
        float x = layout.trackX;
        float w = layout.trackWidth;

        boolean holding = minigame.isFishInBar();
        Color color = holding ? Misc.getPositiveHighlightColor() : Misc.getGrayColor();
        float alpha = (holding ? FishConstants.BAR_ALPHA_HOLDING : FishConstants.BAR_ALPHA_EMPTY) * alphaMult;

        float mid = height * 0.5f;
        drawVerticalGradient(x, y, w, mid, color, alpha, alpha * FishConstants.BAR_CENTER_MULT);
        drawVerticalGradient(x, y + mid, w, height - mid, color, alpha * FishConstants.BAR_CENTER_MULT, alpha);

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

        // jitter is visual only; the hit position the rules use is unaffected
        float centerX = layout.getTrackCenterX() + getJitter(0f);
        float centerY = layout.getTrackY(minigame.getFishPosition()) + getJitter(1.7f);

        // Sonar still earns the exact species reveal. The bought profile replaces only the unidentified mote, never the information a fitted head is meant to provide.
        if (!minigame.getTackle().sonar) {
            if (CrabWares.CHICKEN_PROFILE.isOn()) {
                renderChicken(centerX, centerY, size, alphaMult);
            } else {
                renderCatchMote(centerX, centerY, alphaMult);
            }
            return;
        }

        SpriteAPI sprite = getFishSprite();
        if (sprite == null) {
            renderCatchMote(centerX, centerY, alphaMult);
            return;
        }

        sprite.setSize(size, size);
        sprite.setColor(Color.WHITE);
        sprite.setNormalBlend();
        sprite.setAlphaMult(alphaMult);
        sprite.renderAtCenter(centerX, centerY);
    }


    protected void renderChicken(float centerX, float centerY, float size, float alphaMult) {
        SpriteAPI sprite = SpriteLoader.loadSprite(FishConstants.MINIGAME_CHICKEN_ICON);
        if (sprite == null) {
            renderCatchMote(centerX, centerY, alphaMult);
            return;
        }

        float scale = Math.min(size / sprite.getWidth(), size / sprite.getHeight());

        sprite.setSize(sprite.getWidth() * scale, sprite.getHeight() * scale);
        sprite.setColor(Color.WHITE);
        sprite.setNormalBlend();
        sprite.setAlphaMult(alphaMult);
        sprite.renderAtCenter(centerX, centerY);
    }


    protected void renderCatchMote(float centerX, float centerY, float alphaMult) {
        SpriteAPI sprite = getMoteSprite();
        Color color = minigame.getFish().rarity.color;

        if (sprite == null) {
            Disc.draw(centerX, centerY, FishConstants.MINIGAME_MOTE_GLOW_SIZE * 0.5f,
                    color, alphaMult, 0f, true);
            Disc.draw(centerX, centerY, FishConstants.MINIGAME_MOTE_CORE_SIZE * 0.5f,
                    Color.WHITE, alphaMult, alphaMult, false);
            return;
        }

        sprite.setAdditiveBlend();
        sprite.setColor(Color.WHITE);
        sprite.setSize(FishConstants.MINIGAME_MOTE_HALO_SIZE,
                FishConstants.MINIGAME_MOTE_HALO_SIZE);
        sprite.setAlphaMult(FishConstants.MINIGAME_MOTE_HALO_ALPHA * alphaMult);
        sprite.renderAtCenter(centerX, centerY);

        sprite.setColor(color);
        float glowSize = FishConstants.MINIGAME_MOTE_GLOW_SIZE;

        for (int i = 0; i < FishConstants.MINIGAME_MOTE_GLOW_PASSES; i++) {
            sprite.setSize(glowSize, glowSize);
            sprite.setAlphaMult(alphaMult * (i == 0 ? 1f : FishConstants.MINIGAME_MOTE_INNER_ALPHA));
            sprite.renderAtCenter(centerX, centerY);
            glowSize *= FishConstants.MINIGAME_MOTE_GLOW_STEP;
        }

        sprite.setColor(Color.WHITE);
        sprite.setSize(FishConstants.MINIGAME_MOTE_CORE_SIZE,
                FishConstants.MINIGAME_MOTE_CORE_SIZE);
        sprite.setAlphaMult(FishConstants.MINIGAME_MOTE_CORE_ALPHA * alphaMult);
        sprite.renderAtCenter(centerX, centerY);
    }


    protected void renderTreasure(FishingMinigameLayout layout, float alphaMult) {
        MinigameTreasure treasure = minigame.getTreasure();
        if (treasure == null || !treasure.isActive()) return;

        float centerX = layout.getTrackCenterX();
        float centerY = layout.getTrackY(treasure.position);

        // rarity-colour wash behind the icon so tier reads before the icon does
        Disc.draw(centerX, centerY, FishConstants.TREASURE_ICON_SIZE * 0.9f, treasure.rarity.color,
                0.5f * alphaMult, 0f, true);

        SpriteAPI sprite = SpriteLoader.loadSprite(FishConstants.TREASURE_MINIGAME_ICON);
        if (sprite != null) {
            sprite.setSize(FishConstants.TREASURE_ICON_SIZE, FishConstants.TREASURE_ICON_SIZE);
            sprite.setNormalBlend();
            sprite.setAlphaMult(alphaMult);
            sprite.renderAtCenter(centerX, centerY);
        }

        float held = treasure.getHeldFraction();
        if (held > 0f) {
            float from = FishConstants.TREASURE_ICON_SIZE;
            float to = FishConstants.TREASURE_ICON_SIZE * 0.5f * FishConstants.TREASURE_RING_END;

            Disc.drawOutline(centerX, centerY, from + (to - from) * held,
                    treasure.rarity.color, alphaMult, 2f);
        }

        renderTreasureClock(treasure, centerX, centerY, alphaMult);
    }


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


    protected void renderMeter(FishingMinigameLayout layout, float alphaMult) {
        drawQuad(layout.meterX, layout.meterY, layout.meterWidth, layout.meterHeight,
                Misc.getDarkPlayerColor(), 0.55f * alphaMult);

        Color color = minigame.getProgress() < FishConstants.MINIGAME_METER_DANGER
                ? Misc.getNegativeHighlightColor()
                : Misc.getPositiveHighlightColor();

        drawQuad(layout.meterX, layout.meterY, layout.meterWidth, minigame.getProgress() * layout.meterHeight,
                color, 0.8f * alphaMult);
    }


    protected float getJitter(float offset) {
        float time = (jitterTime + offset) * FishConstants.MINIGAME_FISH_JITTER_SPEED;

        float wobble = (float) (Math.sin(time) * 0.5f
                + Math.sin(time * 1.73f) * 0.3f
                + Math.sin(time * 2.61f) * 0.2f);

        float effort = 1f + Math.abs(minigame.getFishVelocity()) * FishConstants.MINIGAME_FISH_JITTER_EFFORT;

        return wobble * FishConstants.MINIGAME_FISH_JITTER * minigame.getFish().jitter * effort;
    }


    protected SpriteAPI getMoteSprite() {
        if (moteSprite == null) {
            moteSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        }

        return moteSprite;
    }


    protected SpriteAPI getFishSprite() {
        FishSpec fish = minigame.getFish();
        if (fish.icon == null || fish.icon.isEmpty()) return null;

        return SpriteLoader.loadSprite(fish.icon);
    }


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
