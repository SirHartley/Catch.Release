package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * The two marks a catch carries on its cargo icon: rarity down the left edge, grade as pips along
 * the bottom.
 * <p>
 * Drawn rather than authored as sprites because both vary per specimen, and a hundred and eighty
 * icon variants is not a thing anyone wants to keep in step with the table. Deliberately quiet: the
 * icon has to stay readable as an icon, so the rarity is a bar at the edge rather than a wash over
 * the art, and the pips only fill as far as the grade goes.
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

        if (glowMult <= 0f) return;

        sprite.setAdditiveBlend();
        sprite.setAlphaMult(alphaMult * glowMult * FishConstants.ITEM_ICON_MOUSEOVER_MULT);
        sprite.renderAtCenter(centerX, centerY);
        sprite.setNormalBlend();
    }

    /** Rarity as a bar down the left edge, and grade as up to five pips along the bottom. */
    public static void render(float x, float y, float w, float h, float alphaMult,
                              FishRarity rarity, FishGrade grade) {

        if (alphaMult <= 0f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (rarity != null) {
            quad(x + FishConstants.ITEM_MARK_INSET, y + FishConstants.ITEM_MARK_INSET,
                    FishConstants.ITEM_RARITY_BAR_WIDTH, h - FishConstants.ITEM_MARK_INSET * 2f,
                    rarity.color, FishConstants.ITEM_MARK_ALPHA * alphaMult);
        }

        if (grade != null) renderPips(x, y, w, h, alphaMult, grade);

        GL11.glPopAttrib();
    }

    /**
     * One pip per grade step, filled up to this specimen\'s. The empty ones are still drawn, faintly,
     * so the mark reads as a scale with a position on it rather than as a number of dots.
     */
    protected static void renderPips(float x, float y, float w, float h, float alphaMult, FishGrade grade) {
        int steps = FishGrade.values().length;
        int filled = grade.ordinal() + 1;

        float size = FishConstants.ITEM_GRADE_PIP_SIZE;
        float gap = FishConstants.ITEM_GRADE_PIP_GAP;
        float total = steps * size + (steps - 1) * gap;

        float pipX = x + w - FishConstants.ITEM_MARK_INSET - total;
        float pipY = y + FishConstants.ITEM_MARK_INSET;

        for (int i = 0; i < steps; i++) {
            boolean on = i < filled;
            Color color = on ? grade.getColor() : Color.BLACK;
            float alpha = (on ? FishConstants.ITEM_MARK_ALPHA : FishConstants.ITEM_MARK_EMPTY_ALPHA) * alphaMult;

            quad(pipX + i * (size + gap), pipY, size, size, color, alpha);
        }
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
