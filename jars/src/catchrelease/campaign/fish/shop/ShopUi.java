package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.rendering.helper.RoundedBorder;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * Shared rendering helpers for the shop UI: fonts, quads, and the dressed-box look established by
 * the minigame panels. The outfitter draws its own state each frame, rather than using stock
 * widgets, so it never goes stale.
 */
public class ShopUi {

    public static final String FONT_TITLE = FishConstants.MINIGAME_RESULT_TITLE_FONT;
    public static final String FONT_BODY = FishConstants.MINIGAME_RESULT_FONT;
    public static final String FONT_SMALL = "graphics/fonts/orbitron12condensed.fnt";

    protected static LazyFont title;
    protected static LazyFont body;
    protected static LazyFont small;
    protected static boolean fontsChecked = false;

    /** Loaded once and kept. A missing font costs the text and nothing else. */
    protected static void loadFonts() {
        if (fontsChecked) return;
        fontsChecked = true;

        try {
            title = LazyFont.loadFont(FONT_TITLE);
            body = LazyFont.loadFont(FONT_BODY);
            small = LazyFont.loadFont(FONT_SMALL);
        } catch (Exception e) {
            Global.getLogger(ShopUi.class).warn("No fonts for the outfitter", e);
        }
    }

    public static LazyFont getTitleFont() {
        loadFonts();
        return title;
    }

    public static LazyFont getBodyFont() {
        loadFonts();
        return body;
    }

    public static LazyFont getSmallFont() {
        loadFonts();
        return small;
    }

    /**
     * Text at the one size a bitmap font is sharp at: its own. Anything else is the texture
     * scaled, which is the blur.
     */
    public static LazyFont.DrawableString createText(LazyFont font, String text) {
        return font.createText(text, java.awt.Color.WHITE, font.getBaseHeight());
    }

    public static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                (int) MathUtils.clamp(alpha * 255f, 0f, 255f));
    }

    public static void drawQuad(float x, float y, float width, float height, Color color, float alpha) {
        if (alpha <= 0f || width <= 0f || height <= 0f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                alpha * (color.getAlpha() / 255f));

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    /**
     * The sidebar's dressing, the mod's one panel face: transparent black under a one-pixel
     * player-colour border at half strength, corners square.
     */
    public static void drawPanel(float x, float y, float width, float height,
                                 float bgAlpha, float alphaMult) {
        drawQuad(x, y, width, height, Color.BLACK, bgAlpha * alphaMult);

        Color border = Misc.getBasePlayerColor();
        drawQuad(x, y, width, 1f, border, 0.55f * alphaMult);
        drawQuad(x, y + height - 1f, width, 1f, border, 0.55f * alphaMult);
        drawQuad(x, y, 1f, height, border, 0.55f * alphaMult);
        drawQuad(x + width - 1f, y, 1f, height, border, 0.55f * alphaMult);
    }

    /** The bright outline just off a box and the dimmer one outside it, as the catch's panels have. */
    public static void dress(float x, float y, float width, float height, float alphaMult) {
        float inset = FishConstants.MINIGAME_BORDER_INSET;
        float spacing = FishConstants.MINIGAME_BORDER_SPACING;

        outline(x, y, width, height, inset + spacing, Misc.getDarkPlayerColor(),
                FishConstants.MINIGAME_BORDER_OUTER_ALPHA * alphaMult);

        outline(x, y, width, height, inset, Misc.getBrightPlayerColor(),
                FishConstants.MINIGAME_BORDER_ALPHA * alphaMult);
    }

    protected static void outline(float x, float y, float width, float height, float offset,
                                  Color color, float alpha) {

        RoundedBorder.draw(x - offset, y - offset, width + offset * 2f, height + offset * 2f,
                FishConstants.MINIGAME_BORDER_RADIUS + offset, color, alpha,
                FishConstants.MINIGAME_BORDER_WIDTH);
    }

    /** Draws an upgrade pip row, lit pips up to {@code level}. */
    public static void drawPips(float x, float y, float size, float gap,
                                int level, int max, Color lit, float alpha) {

        for (int i = 0; i < max; i++) {
            float px = x + i * (size + gap);

            if (i < level) {
                drawQuad(px, y, size, size, lit, alpha);
            } else {
                drawQuad(px, y, size, size, Misc.getDarkPlayerColor(), 0.35f * alpha);
                RoundedBorder.draw(px, y, size, size, 1f, Misc.getDarkPlayerColor(), 0.8f * alpha, 1f);
            }
        }
    }

    public static float getPipRowWidth(int max, float size, float gap) {
        return max <= 0 ? 0f : max * size + (max - 1) * gap;
    }

    /**
     * Clips drawing to a rectangle in UI coordinates, for rows living inside a scroller - the
     * scroller clips its stock children, but knows nothing about what a plugin paints.
     */
    public static void startClip(float x, float y, float width, float height) {
        float scale = Display.getPixelScaleFactor();

        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale), (int) (y * scale),
                (int) (width * scale), (int) (height * scale));
    }

    public static void endClip() {
        GL11.glPopAttrib();
    }

    /**
     * The dressed box a hover card is drawn in, and nothing else - the text is the caller's, since
     * only the caller knows when it changed and a {@code DrawableString} is a display list rather
     * than something worth rebuilding sixty times a second.
     */
    public static void drawCard(float x, float y, float width, float height, float alphaMult) {
        drawQuad(x, y, width, height, java.awt.Color.BLACK, 0.9f * alphaMult);
        drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.12f * alphaMult);

        dress(x, y, width, height, alphaMult);
    }

    /**
     * Keeps a card on screen. Placed up and to the right of what it is about, and folded back over
     * the other side rather than off the edge - a card the player cannot read is worse than none.
     *
     * @return {x, y} for the bottom-left corner
     */
    public static float[] placeCard(float atX, float atY, float width, float height, float gap) {
        float screenWidth = Global.getSettings().getScreenWidth();
        float screenHeight = Global.getSettings().getScreenHeight();

        float x = atX + gap;
        if (x + width > screenWidth - gap) x = atX - gap - width;
        if (x < gap) x = gap;

        float y = atY + gap;
        if (y + height > screenHeight - gap) y = screenHeight - gap - height;
        if (y < gap) y = gap;

        return new float[]{x, y};
    }

    public static boolean contains(float x, float y, float width, float height,
                                   float pointX, float pointY) {

        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }
}
