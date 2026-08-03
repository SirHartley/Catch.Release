package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.awt.Color;

/**
 * The species' icon, and the two marks a catch carries over it: rarity as a bar across the top left,
 * and the specimen's grade as pips after it.
 * <p>
 * Drawn rather than authored as sprites because both vary per specimen, and a hundred and eighty
 * icon variants is not a thing anyone wants to keep in step with the table. Deliberately quiet: the
 * icon has to stay readable as an icon, so the marks are a thin row in one corner rather than a wash
 * over the art, and the pips only fill as far as the grade goes.
 */
public class FishItemRenderer {

    /**
     * The species' own icon, fitted to the cargo cell.
     * <p>
     * Fitted rather than stretched: the art is square today and the cell is square today, and neither
     * is ours, so the smaller of the two ratios is taken and a tall fish stays a tall fish.
     * <p>
     * The second pass is what the cargo view does for every other icon - the same sprite again,
     * additive, at the mouse-over brightness - so a catch does not sit dead under the cursor while
     * the rest of the hold lights up.
     */
    public static void renderIcon(float x, float y, float w, float h, float alphaMult, float glowMult,
                                  String path) {

        SpriteAPI sprite = SpriteLoader.loadSprite(path);
        if (sprite == null || alphaMult <= 0f) return;

        float available = Math.min(w, h) - FishConstants.ITEM_ICON_INSET * 2f;
        if (available <= 0f) return;

        float scale = Math.min(available / sprite.getWidth(), available / sprite.getHeight());
        sprite.setSize(sprite.getWidth() * scale, sprite.getHeight() * scale);

        float centerX = x + w * 0.5f;
        float centerY = y + h * 0.5f;

        sprite.setNormalBlend();
        sprite.setAlphaMult(alphaMult);
        sprite.renderAtCenter(centerX, centerY);

        if (FishConstants.ITEM_ICON_SHARPEN) sharpen(sprite, centerX, centerY, alphaMult);

        if (glowMult <= 0f) return;

        sprite.setAdditiveBlend();
        sprite.setAlphaMult(alphaMult * glowMult * FishConstants.ITEM_ICON_MOUSEOVER_MULT);
        sprite.renderAtCenter(centerX, centerY);
        sprite.setNormalBlend();
    }

    /**
     * An unsharp mask over an icon that has already been drawn.
     * <p>
     * The definition of one: image + amount * (image - blurred). The second term is done as two
     * passes, one adding the image back and one subtracting a blurred copy, and the blurred copy is
     * the same sprite drawn a couple of pixels wider - which linear filtering makes a soft version
     * of it. Cheap, needs no shader, and the only thing it can be wrong about is by how much.
     * <p>
     * The sprite is left at the size it came in at, since the caller may still be using it.
     */
    protected static void sharpen(SpriteAPI sprite, float centerX, float centerY, float alphaMult) {
        float amount = FishConstants.ITEM_ICON_SHARPEN_AMOUNT;
        if (amount <= 0f) return;

        float width = sprite.getWidth();
        float height = sprite.getHeight();
        float radius = FishConstants.ITEM_ICON_SHARPEN_RADIUS;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);

        //the image again, at the strength the difference is being added at
        sprite.setAdditiveBlend();
        sprite.setAlphaMult(alphaMult * amount);
        sprite.renderAtCenter(centerX, centerY);

        //minus its blurred self, at the same strength - what is left is the edges
        GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
        sprite.setSize(width + radius * 2f, height + radius * 2f);
        sprite.renderAtCenter(centerX, centerY);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);

        GL11.glPopAttrib();

        sprite.setSize(width, height);
        sprite.setNormalBlend();
        sprite.setAlphaMult(alphaMult);
    }

    /**
     * One row across the top left of the icon: the species' rarity as a single unbroken bar three
     * pips long, then the specimen's grade as pips after it.
     */
    public static void render(float x, float y, float w, float h, float alphaMult,
                              FishRarity rarity, FishGrade grade) {

        if (alphaMult <= 0f) return;

        float size = FishConstants.ITEM_GRADE_PIP_SIZE;
        float gap = FishConstants.ITEM_GRADE_PIP_GAP;

        int steps = FishGrade.values().length;
        float pipsWidth = steps * size + (steps - 1) * gap;
        float barWidth = FishConstants.ITEM_RARITY_BAR_PIPS * size
                + (FishConstants.ITEM_RARITY_BAR_PIPS - 1f) * gap;

        //laid out from the top left corner, so the row keeps that corner whatever is being drawn
        float barX = x + FishConstants.ITEM_MARK_INSET;
        float rowY = y + h - FishConstants.ITEM_MARK_INSET - size;
        float pipX = barX + barWidth + FishConstants.ITEM_RARITY_BAR_GAP;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (rarity != null) {
            //a dark backing under it, or the common grey sits on the art and reads as part of it
            backing(barX, rowY, barWidth, size, alphaMult);

            quad(barX, rowY, barWidth, size, rarity.color, FishConstants.ITEM_MARK_ALPHA * alphaMult);
        }

        if (grade != null) renderPips(pipX, rowY, size, gap, pipsWidth, alphaMult, grade);

        GL11.glPopAttrib();
    }

    /**
     * One pip per grade step, filled up to this specimen\'s. The empty ones are still drawn, faintly,
     * so the mark reads as a scale with a position on it rather than as a number of dots.
     */
    protected static void renderPips(float pipX, float pipY, float size, float gap, float total,
                                     float alphaMult, FishGrade grade) {

        int steps = FishGrade.values().length;
        int filled = grade.ordinal() + 1;

        backing(pipX, pipY, total, size, alphaMult);

        for (int i = 0; i < steps; i++) {
            boolean on = i < filled;
            Color color = on ? grade.getColor() : Color.BLACK;
            float alpha = (on ? FishConstants.ITEM_MARK_ALPHA : FishConstants.ITEM_MARK_EMPTY_ALPHA) * alphaMult;

            quad(pipX + i * (size + gap), pipY, size, size, color, alpha);
        }
    }

    /** A dark pad a pixel out from a mark, so the mark reads against the art rather than into it. */
    protected static void backing(float x, float y, float w, float h, float alphaMult) {
        float pad = FishConstants.ITEM_MARK_BACKING_PAD;

        quad(x - pad, y - pad, w + pad * 2f, h + pad * 2f, Color.BLACK,
                FishConstants.ITEM_MARK_BACKING_ALPHA * alphaMult);
    }

    protected static void quad(float x, float y, float w, float h, Color color, float alpha) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }
}
