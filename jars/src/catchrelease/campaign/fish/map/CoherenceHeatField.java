package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.Aberration;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * The coherence heat map: how well the fabric is holding, painted over the hyperspace map as a
 * gradient - clear where it holds, purple where it runs thin, leaning hot where it is barely
 * there. Colonies cut clear, five-light-year stabilizing basins into that field through the same
 * centralized reading. What it shows is {@link Aberration#baseAt}, the same reading every specimen
 * and the thin-fabric terrain already answer to.
 * <p>
 * The reading is a walk of the whole sector's sources, so the field is sampled onto a
 * one-light-year grid on a budget - a few hundred nodes per rendered frame - and sweeps in as a
 * front while the survey completes. The sources are static or seasonal; a field sampled once is
 * good for the whole map session.
 * <p>
 * It samples bare points rather than systems, deliberately: what it paints is the water
 * <i>between</i> the stars as much as at them, and a gate's reach is a disc that does not care
 * whose system it crosses. {@code Aberration} has to answer a point that belongs to nothing with
 * everything it knows for this to mean anything, and for two commits it did not.
 */
public class CoherenceHeatField {

    /** One light-year per grid node; GL interpolates the gradient across each cell. */
    public static final float CELL = 2000f;

    /** Sampling budget per rendered frame - the whole sector fills in about a second. */
    public static final int SAMPLES_PER_FRAME = 500;

    /** Below this the fabric counts as holding and the map stays clear. */
    public static final float SHOW_FLOOR = 0.08f;

    /**
     * The ceiling on this layer, and the shape of the climb to it.
     * <p>
     * {@code ALPHA_CAP} is the only alpha number here and nothing on the layer ever exceeds it -
     * the worst water in the sector paints at exactly this and everywhere else paints at less, so
     * turning it down turns the whole overlay down and nothing else has to be touched.
     * <p>
     * {@code HEAT_EASE} shapes what "less" means. It was 0.7, and an exponent under one <i>front
     * loads</i>: a tenth of the way up the scale was already a fifth of the way up the alpha, a
     * third of the way up was nearly half. So most of the sector arrived at once at close to full
     * strength and the range between "mildly thin" and "barely holding" had nothing left to say.
     * Above one it does the opposite - mild thinness is a hint you have to look for, and the colour
     * is earned.
     */
    public static final float ALPHA_CAP = 0.2f;
    public static final float HEAT_EASE = 1.8f;

    /** The pond glow's purple for thin water, leaning hot where it is barely holding. */
    public static final Color THIN = new Color(150, 30, 190);
    public static final Color WORST = new Color(255, 120, 235);

    protected final float minX, minY;
    protected final int cols, rows;
    protected final float[] values;
    protected int filled = 0;

    /**
     * The sector rectangle, exactly, and not one node past it.
     * <p>
     * It used to be the systems' own bounding box with six light-years of margin thrown around it,
     * on the reasoning that the abyss reaches past where anybody lives. It does - but the abyss it
     * was reaching into is not water. Outside the map rectangle {@code getAbyssalDepth} stops
     * describing hyperspace and starts describing how far off the edge you have wandered: it climbs
     * a full point every two thousand units and caps. So of the twelve thousand units of margin,
     * ten thousand were a flat maximum reading, and the map wore a saturated purple frame all the
     * way round that had nothing whatever to do with the fabric.
     * <p>
     * The map draws the rectangle, so the field paints the rectangle. The last node lands on the
     * boundary rather than past it, where the out-of-map term is still exactly zero.
     */
    public CoherenceHeatField() {
        float w = Global.getSettings().getFloat("sectorWidth");
        float h = Global.getSettings().getFloat("sectorHeight");

        minX = -w * 0.5f;
        minY = -h * 0.5f;

        cols = (int) Math.floor(w / CELL) + 1;
        rows = (int) Math.floor(h / CELL) + 1;

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

        //eased so the bottom of the range is faint and the top of it is earned - see HEAT_EASE
        float alpha = ALPHA_CAP * (float) Math.pow(heat, HEAT_EASE) * alphaMult;

        float r = (THIN.getRed() + (WORST.getRed() - THIN.getRed()) * heat) / 255f;
        float g = (THIN.getGreen() + (WORST.getGreen() - THIN.getGreen()) * heat) / 255f;
        float b = (THIN.getBlue() + (WORST.getBlue() - THIN.getBlue()) * heat) / 255f;

        GL11.glColor4f(r, g, b, alpha);
    }
}
