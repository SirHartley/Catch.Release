package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays out over the catch card's specimen box when a fish is landed: a flash, the specimen
 * growing in, confetti, and the catch text at an angle. Centered on {@code layout.getBoxCenter*}
 * each frame, and every part times itself off {@link #elapsed} / {@link FishConstants#CELEBRATION_TIME}
 * so nothing can drift out of sync.
 */
public class CatchCelebration {

    protected static class Confetto {
        float x, y, vx, vy, spin, angle, size;
        Color color;
    }

    protected final FishSpec fish;
    protected final List<Confetto> confetti = new ArrayList<>();

    protected float elapsed = 0f;

    transient protected LazyFont font;
    transient protected LazyFont.DrawableString text;
    transient protected boolean fontChecked = false;

    /** Set on first render, not construction - only then is the settled layout center known. */
    protected boolean confettiSpawned = false;

    public CatchCelebration(FishSpec fish) {
        this.fish = fish;

        playHook(FishConstants.SOUND_CATCH);
    }

    protected Confetto spawn(float x, float y, FishSpec fish) {
        Confetto c = new Confetto();

        float arc = FishConstants.CELEBRATION_CONFETTI_ARC;
        float angle = 90f + MathUtils.getRandomNumberInRange(-arc, arc);
        float spread = FishConstants.CELEBRATION_CONFETTI_SPREAD;

        //speed varies so pieces spread across the burst radius instead of moving as one ring
        float speed = MathUtils.getRandomNumberInRange(
                FishConstants.CELEBRATION_CONFETTI_SPEED * 0.35f, FishConstants.CELEBRATION_CONFETTI_SPEED);

        c.x = x + MathUtils.getRandomNumberInRange(-spread, spread);
        c.y = y + MathUtils.getRandomNumberInRange(-spread * 0.5f, spread * 0.5f);
        c.vx = (float) Math.cos(Math.toRadians(angle)) * speed;
        c.vy = (float) Math.sin(Math.toRadians(angle)) * speed;
        c.spin = MathUtils.getRandomNumberInRange(-360f, 360f);
        c.angle = MathUtils.getRandomNumberInRange(0f, 360f);
        c.size = MathUtils.getRandomNumberInRange(2f, FishConstants.CELEBRATION_CONFETTI_SIZE);
        c.color = pickColor(fish);

        return c;
    }

    /** Most confetti is random hue; a fraction takes the fish's own rarity colour. */
    protected Color pickColor(FishSpec fish) {
        if (fish != null
                && MathUtils.getRandomNumberInRange(0f, 1f) < FishConstants.CELEBRATION_CONFETTI_RARITY_SHARE) {
            return fish.rarity.color;
        }

        return Color.getHSBColor(MathUtils.getRandomNumberInRange(0f, 1f),
                FishConstants.CELEBRATION_CONFETTI_SATURATION,
                FishConstants.CELEBRATION_CONFETTI_BRIGHTNESS);
    }

    public void advance(float amount) {
        elapsed += amount;

        for (Confetto c : confetti) {
            c.vy -= FishConstants.CELEBRATION_CONFETTI_GRAVITY * amount;
            c.x += c.vx * amount;
            c.y += c.vy * amount;
            c.angle += c.spin * amount;
        }
    }

    public boolean isDone() {
        return elapsed >= FishConstants.CELEBRATION_TIME;
    }

    /** 0 at the start, 1 at the end. */
    protected float getProgress() {
        return MathUtils.clamp(elapsed / Math.max(0.01f, FishConstants.CELEBRATION_TIME), 0f, 1f);
    }

    public void render(FishingMinigameLayout layout, SpriteAPI fishSprite, float alphaMult) {
        float progress = getProgress();

        float fade = progress > FishConstants.CELEBRATION_FADE_FROM
                ? 1f - (progress - FishConstants.CELEBRATION_FADE_FROM)
                        / (1f - FishConstants.CELEBRATION_FADE_FROM)
                : 1f;

        float alpha = alphaMult * MathUtils.clamp(fade, 0f, 1f);
        if (alpha <= 0f) return;

        float centerX = layout.getBoxCenterX();
        float centerY = layout.getBoxCenterY();

        if (!confettiSpawned) {
            confettiSpawned = true;
            for (int i = 0; i < FishConstants.CELEBRATION_CONFETTI; i++) {
                confetti.add(spawn(centerX, centerY, fish));
            }
        }

        renderFlash(centerX, centerY, progress, alpha);
        renderBacklight(centerX, centerY, progress, alpha);
        renderFish(fishSprite, centerX, centerY, progress, alpha);
        renderConfetti(alpha);
        renderText(centerX, centerY, progress, alpha);
    }

    /** A disc of light behind everything, thrown out fast and gone before the rest. */
    protected void renderFlash(float centerX, float centerY, float progress, float alpha) {
        float share = MathUtils.clamp(progress / FishConstants.CELEBRATION_FLASH_TIME, 0f, 1f);
        if (share >= 1f) return;

        Disc.draw(centerX, centerY, FishConstants.CELEBRATION_FLASH_SIZE * (0.2f + share * 0.8f),
                getAccentColor(), (1f - share) * FishConstants.CELEBRATION_FLASH_ALPHA * alpha, 0f, true);
    }

    /** Backing disc in the fish's rarity colour, ringed in the player's UI bright/dim border colours. */
    protected void renderBacklight(float centerX, float centerY, float progress, float alpha) {
        float radius = getBacklightRadius(progress);

        Disc.draw(centerX, centerY, radius, getAccentColor(),
                FishConstants.CELEBRATION_BACKLIGHT_ALPHA * alpha,
                FishConstants.CELEBRATION_BACKLIGHT_EDGE_ALPHA * alpha, true);

        Disc.drawOutline(centerX, centerY, radius + FishConstants.CELEBRATION_RING_SPACING,
                Misc.getDarkPlayerColor(), FishConstants.CELEBRATION_RING_OUTER_ALPHA * alpha,
                FishConstants.CELEBRATION_RING_WIDTH);

        Disc.drawOutline(centerX, centerY, radius, Misc.getBrightPlayerColor(),
                FishConstants.CELEBRATION_RING_ALPHA * alpha, FishConstants.CELEBRATION_RING_WIDTH);
    }

    protected float getBacklightRadius(float progress) {
        float pulse = 1f + FishConstants.CELEBRATION_BACKLIGHT_PULSE
                * (float) Math.sin(elapsed * FishConstants.CELEBRATION_BACKLIGHT_PULSE_RATE);

        return FishConstants.CELEBRATION_BACKLIGHT_SIZE * (0.55f + 0.45f * ease(progress)) * pulse;
    }

    /** The specimen sprite, centered over the backlight and growing in. */
    protected void renderFish(SpriteAPI sprite, float centerX, float centerY, float progress, float alpha) {
        if (sprite == null) return;

        float size = FishConstants.CELEBRATION_FISH_SIZE
                * (1f + FishConstants.CELEBRATION_FISH_GROW * ease(progress));

        sprite.setSize(size, size);
        sprite.setAlphaMult(alpha);
        sprite.renderAtCenter(centerX, centerY);
    }

    protected void renderConfetti(float alpha) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (Confetto c : confetti) {
            GL11.glPushMatrix();
            GL11.glTranslatef(c.x, c.y, 0f);
            GL11.glRotatef(c.angle, 0f, 0f, 1f);

            GL11.glColor4f(c.color.getRed() / 255f, c.color.getGreen() / 255f, c.color.getBlue() / 255f, alpha);

            //oblong, not square, so the spin is visible
            float w = c.size, h = c.size * 0.45f;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(-w, -h);
            GL11.glVertex2f(w, -h);
            GL11.glVertex2f(w, h);
            GL11.glVertex2f(-w, h);
            GL11.glEnd();

            GL11.glPopMatrix();
        }

        GL11.glPopAttrib();
    }

    /** Catch text, slanted, popping past full size before settling. */
    protected void renderText(float centerX, float centerY, float progress, float alpha) {
        LazyFont.DrawableString drawable = getText();
        if (drawable == null) return;

        float pop = progress < FishConstants.CELEBRATION_POP_TIME
                ? progress / FishConstants.CELEBRATION_POP_TIME
                : 1f;
        float scale = pop < 1f
                ? 0.4f + 1.0f * pop
                : 1f + FishConstants.CELEBRATION_POP_OVERSHOOT * (float) Math.exp(-(progress - FishConstants.CELEBRATION_POP_TIME) * 8f);

        drawable.setFontSize(FishConstants.CELEBRATION_TEXT_SIZE * scale);
        drawable.setBaseColor(withAlpha(getAccentColor(), alpha));

        float y = centerY + FishConstants.CELEBRATION_TEXT_RISE;
        drawable.drawAtAngle(centerX - drawable.getWidth() * 0.5f, y, FishConstants.CELEBRATION_TEXT_ANGLE);
    }

    /** The rarity colour, shared by text, flash and confetti. */
    protected Color getAccentColor() {
        return fish == null ? Color.WHITE : fish.rarity.color;
    }

    protected static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                (int) MathUtils.clamp(alpha * 255f, 0f, 255f));
    }

    /** Loaded once; a missing font just means no text renders. */
    protected LazyFont.DrawableString getText() {
        if (!fontChecked) {
            fontChecked = true;
            try {
                font = LazyFont.loadFont(FishConstants.CELEBRATION_FONT);
                text = font.createText(FishConstants.CELEBRATION_TEXT, Color.WHITE,
                        FishConstants.CELEBRATION_TEXT_SIZE);
            } catch (Exception e) {
                Global.getLogger(CatchCelebration.class).warn("No font for the catch text", e);
            }
        }

        return text;
    }

    /** Sound hook; a no-op until sound ids are filled in {@link FishConstants}. */
    public static void playHook(String soundId) {
        if (soundId == null || soundId.isEmpty()) return;

        Global.getSoundPlayer().playUISound(soundId, 1f, 1f);
    }

    /** Ease-out curve. */
    protected static float ease(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}
