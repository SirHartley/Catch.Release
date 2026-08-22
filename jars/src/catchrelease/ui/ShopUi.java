package catchrelease.ui;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.rendering.helper.RoundedBorder;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class ShopUi {

    public static final String FONT_TITLE = FishConstants.MINIGAME_RESULT_TITLE_FONT;
    public static final String FONT_BODY = FishConstants.MINIGAME_RESULT_FONT;
    public static final String FONT_SMALL = "graphics/fonts/orbitron12condensed.fnt";

    protected static LazyFont title;
    protected static LazyFont body;
    protected static LazyFont small;
    protected static boolean fontsChecked = false;

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

    public static void drawVerticalGradient(float x, float y, float width, float height,
                                            Color color, float bottomAlpha, float topAlpha) {
        drawVerticalGradient(x, y, width, height, color, color, bottomAlpha, topAlpha);
    }

    public static void drawVerticalGradient(float x, float y, float width, float height,
                                            Color bottom, Color top,
                                            float bottomAlpha, float topAlpha) {
        if (width <= 0f || height <= 0f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(bottom.getRed() / 255f, bottom.getGreen() / 255f, bottom.getBlue() / 255f,
                bottomAlpha * (bottom.getAlpha() / 255f));
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glColor4f(top.getRed() / 255f, top.getGreen() / 255f, top.getBlue() / 255f,
                topAlpha * (top.getAlpha() / 255f));
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    public static void drawPanel(float x, float y, float width, float height,
                                 float bgAlpha, float alphaMult) {
        drawQuad(x, y, width, height, Color.BLACK, bgAlpha * alphaMult);

        Color border = Misc.getBasePlayerColor();
        drawQuad(x, y, width, 1f, border, 0.55f * alphaMult);
        drawQuad(x, y + height - 1f, width, 1f, border, 0.55f * alphaMult);
        drawQuad(x, y, 1f, height, border, 0.55f * alphaMult);
        drawQuad(x + width - 1f, y, 1f, height, border, 0.55f * alphaMult);
    }

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

    public static void drawPips(float x, float y, float size, float gap,
                                int level, int max, Color lit, float alpha) {
        drawPips(x, y, size, gap, level, max, lit, alpha, null);
    }

    public static void drawPips(float x, float y, float size, float gap,
                                int level, int max, Color lit, float alpha,
                                java.util.function.IntPredicate missingSchematic) {
        for (int i = 0; i < max; i++) {
            float px = x + i * (size + gap);

            if (i < level) {
                drawQuad(px, y, size, size, lit, alpha);
                continue;
            }

            Color box = missingSchematic != null && missingSchematic.test(i + 1)
                    ? Misc.getGrayColor() : Misc.getDarkPlayerColor();

            drawQuad(px, y, size, size, box, 0.35f * alpha);
            RoundedBorder.draw(px, y, size, size, 1f, box, 0.8f * alpha, 1f);
        }
    }

    public static float getPipRowWidth(int max, float size, float gap) {
        return max <= 0 ? 0f : max * size + (max - 1) * gap;
    }

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

    public static void drawCard(float x, float y, float width, float height, float alphaMult) {
        drawQuad(x, y, width, height, java.awt.Color.BLACK, 0.9f * alphaMult);
        drawQuad(x, y, width, height, Misc.getDarkPlayerColor(), 0.12f * alphaMult);

        dress(x, y, width, height, alphaMult);
    }

    public static boolean contains(float x, float y, float width, float height,
                                   float pointX, float pointY) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }
}
