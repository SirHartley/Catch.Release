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
 * The shop's shared hand: fonts, quads, and the dressed-box look the minigame established.
 * <p>
 * The outfitter is drawn rather than assembled from stock widgets wherever the state on screen can
 * change under it - a row that reads its level fresh every frame never goes stale, where a label
 * would have to be found and rewritten. The drawing itself borrows the catch panels' language
 * (dark field, bright line, dimmer line outside it) so the shop reads as another panel of the same
 * interface.
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

    /**
     * A ladder's worth of pips, the bought ones lit. What every upgrade screen since the dawn of
     * the genre has taught players to read at a glance, which is the point of using it.
     */
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

    public static boolean contains(float x, float y, float width, float height,
                                   float pointX, float pointY) {

        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }
}
