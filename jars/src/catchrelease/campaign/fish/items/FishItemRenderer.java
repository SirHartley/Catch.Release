package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class FishItemRenderer {
    public static void renderIcon(float x, float y, float w, float h, float alphaMult, float glowMult,
                                  String path) {
        SpriteAPI sprite = SpriteLoader.loadSprite(path);
        if (sprite == null || alphaMult <= 0f) return;

        float available = Math.min(w, h) - FishConstants.ITEM_ICON_INSET * 2f;
        if (available <= 0f) return;

        float scale = Math.min(1f, Math.min(available / sprite.getWidth(), available / sprite.getHeight()));
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

    public static void renderIconWithCorners(String path,
                                             float blX, float blY, float tlX, float tlY,
                                             float trX, float trY, float brX, float brY,
                                             float alphaMult, float glowMult) {
        SpriteAPI sprite = SpriteLoader.loadSprite(path);
        if (sprite == null || alphaMult <= 0f) return;

        sprite.setColor(Color.WHITE);
        sprite.setNormalBlend();
        sprite.setAlphaMult(alphaMult);
        sprite.renderWithCorners(blX, blY, tlX, tlY, trX, trY, brX, brY);

        if (glowMult > 0f) {
            sprite.setAdditiveBlend();
            sprite.setAlphaMult(alphaMult * glowMult * FishConstants.ITEM_ICON_MOUSEOVER_MULT);
            sprite.renderWithCorners(blX, blY, tlX, tlY, trX, trY, brX, brY);
        }

        sprite.setNormalBlend();
        sprite.setAlphaMult(1f);
    }

    public static void render(float x, float y, float w, float h, float alphaMult,
                              FishRarity rarity, FishGrade grade) {
        if (alphaMult <= 0f) return;

        float size = FishConstants.ITEM_GRADE_PIP_SIZE;
        float gap = FishConstants.ITEM_GRADE_PIP_GAP;

        int steps = FishGrade.values().length;
        float pipsWidth = steps * size + (steps - 1) * gap;
        float barWidth = FishConstants.ITEM_RARITY_BAR_PIPS * size
                + (FishConstants.ITEM_RARITY_BAR_PIPS - 1f) * gap;

        float barX = x + FishConstants.ITEM_MARK_INSET;
        float rowY = y + h - FishConstants.ITEM_MARK_INSET - size;
        float pipX = barX + barWidth + FishConstants.ITEM_RARITY_BAR_GAP;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (rarity != null) {
            // backing needed or the pale Common accent blends into the art instead of reading as a mark
            backing(barX, rowY, barWidth, size, alphaMult);

            quad(barX, rowY, barWidth, size, rarity.color, FishConstants.ITEM_MARK_ALPHA * alphaMult);
        }

        if (grade != null) renderPips(pipX, rowY, size, gap, pipsWidth, alphaMult, grade);

        GL11.glPopAttrib();
    }

    protected static void renderPips(float pipX, float pipY, float size, float gap, float total,
                                     float alphaMult, FishGrade grade) {
        int steps = FishGrade.values().length;
        int filled = grade.rank + 1;

        backing(pipX, pipY, total, size, alphaMult);

        for (int i = 0; i < steps; i++) {
            boolean on = i < filled;
            Color color = on ? grade.getColor() : Color.BLACK;
            float alpha = (on ? FishConstants.ITEM_MARK_ALPHA : FishConstants.ITEM_MARK_EMPTY_ALPHA) * alphaMult;

            quad(pipX + i * (size + gap), pipY, size, size, color, alpha);
        }
    }

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
