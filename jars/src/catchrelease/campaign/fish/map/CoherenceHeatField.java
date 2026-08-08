package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.Aberration;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * The coherence heat map: how well the fabric is holding, painted over the hyperspace map as a
 * gradient - clear where it holds, purple where it runs thin, leaning hot where it is barely
 * there. What it shows is {@link Aberration#baseAt}, the same reading every specimen and the
 * thin-fabric terrain already answer to.
 * <p>
 * That reading walks every system, gate and slipstream in the sector, and is priced for once a
 * conversation rather than per pixel - so the field is sampled onto a one-light-year grid on a
 * budget, a few hundred nodes per rendered frame, and sweeps in as a front while the survey
 * completes. The sources are static or seasonal; a field sampled once is good for the whole
 * map session.
 */
public class CoherenceHeatField {

    /** One light-year per grid node; GL interpolates the gradient across each cell. */
    public static final float CELL = 2000f;
    public static final float MARGIN = 6f * 2000f;

    /** Sampling budget per rendered frame - the whole sector fills in about a second. */
    public static final int SAMPLES_PER_FRAME = 500;

    /** Below this the fabric counts as holding and the map stays clear. */
    public static final float SHOW_FLOOR = 0.08f;
    public static final float MAX_ALPHA = 0.38f;

    /** The pond glow's purple for thin water, leaning hot where it is barely holding. */
    public static final Color THIN = new Color(150, 30, 190);
    public static final Color WORST = new Color(255, 120, 235);

    protected final float minX, minY;
    protected final int cols, rows;
    protected final float[] values;
    protected int filled = 0;

    /** Bounds from the systems that exist, with enough margin to catch the abyss around them. */
    public CoherenceHeatField() {
        float loX = Float.MAX_VALUE, hiX = -Float.MAX_VALUE;
        float loY = Float.MAX_VALUE, hiY = -Float.MAX_VALUE;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            Vector2f at = system.getLocation();

            loX = Math.min(loX, at.x);
            hiX = Math.max(hiX, at.x);
            loY = Math.min(loY, at.y);
            hiY = Math.max(hiY, at.y);
        }

        if (loX > hiX) loX = hiX = loY = hiY = 0f;

        minX = loX - MARGIN;
        minY = loY - MARGIN;
        cols = (int) ((hiX + MARGIN - minX) / CELL) + 2;
        rows = (int) ((hiY + MARGIN - minY) / CELL) + 2;
        values = new float[cols * rows];
    }

    /** A budget of samples toward completion; call once per rendered frame until done. */
    public void sampleSome() {
        int budget = SAMPLES_PER_FRAME;
        Vector2f at = new Vector2f();

        while (budget-- > 0 && filled < values.length) {
            at.set(minX + (filled % cols) * CELL, minY + (filled / cols) * CELL);
            values[filled++] = Aberration.baseAt(at, null);
        }
    }

    /**
     * The gradient, world-anchored like everything else on the map. Complete rows only, so the
     * survey sweeps in as a front rather than a torn edge; cells where every corner holds are
     * skipped outright, which is most of the sector.
     */
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

    /** One corner's colour: purple rising with the thinness, hot only near the top of it. */
    protected void corner(float value, float alphaMult) {
        float heat = MathUtils.clamp((value - SHOW_FLOOR) / (1f - SHOW_FLOOR), 0f, 1f);

        //eased so mild thinness is readable without the worst of it clipping flat
        float alpha = MAX_ALPHA * (float) Math.pow(heat, 0.7) * alphaMult;

        float r = (THIN.getRed() + (WORST.getRed() - THIN.getRed()) * heat) / 255f;
        float g = (THIN.getGreen() + (WORST.getGreen() - THIN.getGreen()) * heat) / 255f;
        float b = (THIN.getBlue() + (WORST.getBlue() - THIN.getBlue()) * heat) / 255f;

        GL11.glColor4f(r, g, b, alpha);
    }
}
