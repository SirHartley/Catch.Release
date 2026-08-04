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
 * What happens when the fish is landed: a flash behind it, the specimen growing over the catch
 * card's cargo-square, confetti, and the word for it at an angle.
 * <p>
 * Centred on the card's specimen rather than the track - the card is where the eye goes the moment
 * the fish is landed, and the flourish should land where the eye is. The centre is read off the
 * layout every frame, after the card has settled it, so there is exactly one source of it.
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

    /** Set once the confetti has been thrown - which is on first render, not construction, because
     * only a layout the card has settled knows where the burst should come from. */
    protected boolean confettiSpawned = false;

    public CatchCelebration(FishSpec fish) {
        this.fish = fish;

        playHook(FishConstants.SOUND_CATCH);
    }

    /** Thrown up and out, with gravity taking it back down - so the burst has a shape to it. */
    protected Confetto spawn(float x, float y, FishSpec fish) {
        Confetto c = new Confetto();

        float arc = FishConstants.CELEBRATION_CONFETTI_ARC;
        float angle = 90f + MathUtils.getRandomNumberInRange(-arc, arc);
        float spread = FishConstants.CELEBRATION_CONFETTI_SPREAD;

        //the slow ones stay near the middle and the fast ones reach the edge of the panel, which is
        //what makes the burst read as a burst rather than as a ring leaving
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

    /**
     * Most of it is drawn from across the wheel, and a share of it takes the fish's own colour.
     * <p>
     * All of one colour said what was caught and nothing about the catching; all of every colour
     * would say the opposite. The share is what keeps both.
     */
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

        //the card's specimen, not the track - and read here, each frame, so the burst follows
        //wherever the card's own layout put the box this frame
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

    /** A disc of light behind everything, thrown out fast and gone before the rest of it. */
    protected void renderFlash(float centerX, float centerY, float progress, float alpha) {
        float share = MathUtils.clamp(progress / FishConstants.CELEBRATION_FLASH_TIME, 0f, 1f);
        if (share >= 1f) return;

        //no art and no shader: a fan takes the rarity colour as it is
        Disc.draw(centerX, centerY, FishConstants.CELEBRATION_FLASH_SIZE * (0.2f + share * 0.8f),
                getAccentColor(), (1f - share) * FishConstants.CELEBRATION_FLASH_ALPHA * alpha, 0f, true);
    }

    /**
     * What the specimen is read against. The disc is lit in the fish's own colour; the two rings are
     * in the player's UI colours, the same bright-line-and-dimmer-line the panel is framed with.
     */
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

    /** Opens out with the same ease the fish grows on, then breathes rather than sitting still. */
    protected float getBacklightRadius(float progress) {
        float pulse = 1f + FishConstants.CELEBRATION_BACKLIGHT_PULSE
                * (float) Math.sin(elapsed * FishConstants.CELEBRATION_BACKLIGHT_PULSE_RATE);

        return FishConstants.CELEBRATION_BACKLIGHT_SIZE * (0.55f + 0.45f * ease(progress)) * pulse;
    }

    /**
     * The specimen itself, over the card's cargo-square and growing as it comes.
     * <p>
     * On the centre rather than lifted off it: the fish is the thing being looked at, and it was
     * being drawn away from the light that was meant to be behind it.
     */
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
