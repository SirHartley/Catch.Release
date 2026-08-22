package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.Aberration;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class CoherenceHeatField {
    public static final float CELL = 2000f;
    public static final int SAMPLES_PER_FRAME = 500;
    public static final float SHOW_FLOOR = 0.08f;
    public static final float ALPHA_CAP = 0.2f;
    public static final float HEAT_EASE = 1.8f;
    public static final Color THIN = new Color(150, 30, 190);
    public static final Color WORST = new Color(255, 120, 235);

    protected final float minX, minY;
    protected final int cols, rows;
    protected final float[] values;
    protected int filled = 0;

    public CoherenceHeatField() {
        float w = Global.getSettings().getFloat("sectorWidth");
        float h = Global.getSettings().getFloat("sectorHeight");

        minX = -w * 0.5f;
        minY = -h * 0.5f;

        cols = (int) Math.floor(w / CELL) + 1;
        rows = (int) Math.floor(h / CELL) + 1;

        values = new float[cols * rows];
    }

    public void sampleSome() {
        int budget = SAMPLES_PER_FRAME;
        Vector2f at = new Vector2f();

        while (budget-- > 0 && filled < values.length) {
            at.set(minX + (filled % cols) * CELL, minY + (filled / cols) * CELL);
            values[filled++] = Aberration.baseAt(at, null);
        }
    }

    public void render(float factor, float centerX, float centerY, float alphaMult) {
        int fullRows = filled / cols;
        if (fullRows < 2) return;

        GL11.glBegin(GL11.GL_QUADS);

        for (int r = 0; r < fullRows - 1; r++) {
            for (int c = 0; c < cols - 1; c++) {
                float v00 = values[r * cols + c];
                float v10 = values[r * cols + c + 1];
                float v01 = values[(r + 1) * cols + c];
                float v11 = values[(r + 1) * cols + c + 1];

                if (v00 < SHOW_FLOOR && v10 < SHOW_FLOOR
                        && v01 < SHOW_FLOOR && v11 < SHOW_FLOOR) {
                    continue;
                }

                float x0 = (minX + c * CELL) * factor + centerX;
                float x1 = (minX + (c + 1) * CELL) * factor + centerX;
                float y0 = (minY + r * CELL) * factor + centerY;
                float y1 = (minY + (r + 1) * CELL) * factor + centerY;

                corner(v00, alphaMult);
                GL11.glVertex2f(x0, y0);
                corner(v10, alphaMult);
                GL11.glVertex2f(x1, y0);
                corner(v11, alphaMult);
                GL11.glVertex2f(x1, y1);
                corner(v01, alphaMult);
                GL11.glVertex2f(x0, y1);
            }
        }

        GL11.glEnd();
    }

    protected void corner(float value, float alphaMult) {
        float heat = MathUtils.clamp((value - SHOW_FLOOR) / (1f - SHOW_FLOOR), 0f, 1f);

        float alpha = ALPHA_CAP * (float) Math.pow(heat, HEAT_EASE) * alphaMult;

        float r = (THIN.getRed() + (WORST.getRed() - THIN.getRed()) * heat) / 255f;
        float g = (THIN.getGreen() + (WORST.getGreen() - THIN.getGreen()) * heat) / 255f;
        float b = (THIN.getBlue() + (WORST.getBlue() - THIN.getBlue()) * heat) / 255f;

        GL11.glColor4f(r, g, b, alpha);
    }
}
