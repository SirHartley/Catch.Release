package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * What happens when the fish is landed: a flash behind it, the specimen rising out of the track,
 * confetti, and the word for it at an angle.
 * <p>
 * Driven by one clock. Everything reads its own timing off that rather than keeping its own, so the
 * whole thing can be retimed from {@link FishConstants#CELEBRATION_TIME} without any part of it
 * drifting out of step with the rest.
 * <p>
 * Sound is left as hooks rather than guessed at - see {@link #playHook(String)}.
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

    public CatchCelebration(FishSpec fish, float centerX, float centerY) {
        this.fish = fish;

        for (int i = 0; i < FishConstants.CELEBRATION_CONFETTI; i++) {
            confetti.add(spawn(centerX, centerY, fish));
        }

        playHook(FishConstants.SOUND_CATCH);
    }

    /** Thrown up and out, with gravity taking it back down - so the burst has a shape to it. */
    protected Confetto spawn(float x, float y, FishSpec fish) {
        Confetto c = new Confetto();

        float angle = MathUtils.getRandomNumberInRange(60f, 120f);
        float speed = MathUtils.getRandomNumberInRange(
                FishConstants.CELEBRATION_CONFETTI_SPEED * 0.4f, FishConstants.CELEBRATION_CONFETTI_SPEED);

        c.x = x + MathUtils.getRandomNumberInRange(-10f, 10f);
        c.y = y;
        c.vx = (float) Math.cos(Math.toRadians(angle)) * speed;
        c.vy = (float) Math.sin(Math.toRadians(angle)) * speed;
        c.spin = MathUtils.getRandomNumberInRange(-360f, 360f);
        c.angle = MathUtils.getRandomNumberInRange(0f, 360f);
        c.size = MathUtils.getRandomNumberInRange(2f, FishConstants.CELEBRATION_CONFETTI_SIZE);
        c.color = fish == null ? Color.WHITE : fish.rarity.color;

        return c;
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

    /** 0 at the start, 1 at the end. Everything below is written against this. */
    protected float getProgress() {
        return MathUtils.clamp(elapsed / Math.max(0.01f, FishConstants.CELEBRATION_TIME), 0f, 1f);
    }

    public void render(FishingMinigameLayout layout, SpriteAPI fishSprite, float alphaMult) {
        float progress = getProgress();

        //everything fades together over the last of it, so nothing is left hanging
        float fade = progress > FishConstants.CELEBRATION_FADE_FROM
                ? 1f - (progress - FishConstants.CELEBRATION_FADE_FROM)
                        / (1f - FishConstants.CELEBRATION_FADE_FROM)
                : 1f;

        float alpha = alphaMult * MathUtils.clamp(fade, 0f, 1f);
        if (alpha <= 0f) return;

        float centerX = layout.getTrackCenterX();
        float centerY = layout.trackY + layout.trackHeight * 0.5f;

        renderFlash(centerX, centerY, progress, alpha);
        renderFish(fishSprite, centerX, centerY, progress, alpha);
        renderConfetti(alpha);
        renderText(centerX, centerY, progress, alpha);
    }

    /** A disc of light behind everything, thrown out fast and gone before the rest of it. */
    protected void renderFlash(float centerX, float centerY, float progress, float alpha) {
        float share = MathUtils.clamp(progress / FishConstants.CELEBRATION_FLASH_TIME, 0f, 1f);
        if (share >= 1f) return;

        float radius = FishConstants.CELEBRATION_FLASH_SIZE * (0.2f + share * 0.8f);
        Color color = getAccentColor();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        //a fan rather than a sprite, so it needs no art and takes the rarity colour as it is
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                (1f - share) * FishConstants.CELEBRATION_FLASH_ALPHA * alpha);
        GL11.glVertex2f(centerX, centerY);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 0f);
        for (int i = 0; i <= 24; i++) {
            double a = Math.toRadians(i * 360.0 / 24.0);
            GL11.glVertex2f(centerX + (float) Math.cos(a) * radius, centerY + (float) Math.sin(a) * radius);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    /** The specimen itself, lifted clear of the track and growing as it comes. */
    protected void renderFish(SpriteAPI sprite, float centerX, float centerY, float progress, float alpha) {
        if (sprite == null) return;

        float rise = FishConstants.CELEBRATION_FISH_RISE * ease(progress);
        float size = FishConstants.MINIGAME_FISH_ICON_SIZE
                * (1f + FishConstants.CELEBRATION_FISH_GROW * ease(progress));

        sprite.setSize(size, size);
        sprite.setAlphaMult(alpha);
        sprite.renderAtCenter(centerX, centerY + rise);
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

            //oblong rather than square, which is what makes the spin read
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

    /** The word for it, slanted, popping past its size before settling back to it. */
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

    /** The rarity colour, which is what ties the text, the flash and the confetti together. */
    protected Color getAccentColor() {
        return fish == null ? Color.WHITE : fish.rarity.color;
    }

    protected static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                (int) MathUtils.clamp(alpha * 255f, 0f, 255f));
    }

    /** Loaded once and kept; a missing font costs the text and nothing else. */
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

    /**
     * Where the sound goes when there is one. The ids are blank in {@link FishConstants} and nothing
     * plays until they are filled in, so this is a hook rather than a placeholder that rattles.
     */
    public static void playHook(String soundId) {
        if (soundId == null || soundId.isEmpty()) return;

        Global.getSoundPlayer().playUISound(soundId, 1f, 1f);
    }

    /** Slow at the end rather than linear, so the rise arrives rather than stopping. */
    protected static float ease(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}
