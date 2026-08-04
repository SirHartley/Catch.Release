package catchrelease.campaign.fish.map;

import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * The organic shape around a species' systems: one filled, outlined blob rather than a pile of
 * circles.
 * <p>
 * Drawing a circle per system and letting them overlap gives lens-shaped seams where they cross -
 * double-darkened fill inside, hard corners on the outline. What is wanted is the union, with the
 * waists between neighbouring circles rounded off - which is exactly what a metaball field gives
 * for free: each system contributes a smooth falloff, the contributions sum, and the contour at a
 * fixed threshold is a single shape whose bridges and junctions are rounded by the addition
 * itself. No seam exists because no circle exists - only the field does.
 * <p>
 * The contour is cut by marching triangles rather than marching squares: each grid cell is split
 * into four triangles around its centre, and a triangle's three corners admit no ambiguous case,
 * so the saddle configurations that make marching squares fiddly never arise. Out the other end
 * come world-space triangles for the fill and world-space segments for the outline; the renderer
 * transforms them by whatever the map's camera is doing that frame.
 * <p>
 * Built once per filter change and cached by the caller - the field is a function of which
 * systems host the fish, not of where the camera looks.
 */
public class FishPresenceField {

    /**
     * How much bigger the kernel's reach is than the radius the eye should read. The kernel falls
     * off as (1 - d^2/r^2)^3 and the contour sits at {@link #THRESHOLD}, so a lone system's blob
     * crosses the threshold at 0.543 of the kernel radius - this constant is 1/0.543, putting the
     * visible edge where the caller asked for it.
     */
    public static final float REACH = 1.84f;

    public static final float THRESHOLD = 0.35f;

    /** Grid cells per visual radius. More is smoother and costs quadratically. */
    public static final float CELLS_PER_RADIUS = 3f;

    /** One blob's geometry, in world coordinates. */
    public static class Mesh {
        /** Flat triangles: x1,y1,x2,y2,x3,y3 per entry. */
        public final List<float[]> fill = new ArrayList<>();

        /** Flat segments: x1,y1,x2,y2 per entry. */
        public final List<float[]> outline = new ArrayList<>();

        public boolean isEmpty() {
            return fill.isEmpty();
        }
    }

    /**
     * Cuts the merged shape around the given centres.
     *
     * @param centers      where the systems are, in world coordinates
     * @param visualRadius how far from a lone system its blob edge should sit
     */
    public static Mesh build(List<Vector2f> centers, float visualRadius) {
        Mesh mesh = new Mesh();
        if (centers == null || centers.isEmpty() || visualRadius <= 0f) return mesh;

        float reach = visualRadius * REACH;
        float cell = visualRadius / CELLS_PER_RADIUS;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        for (Vector2f center : centers) {
            minX = Math.min(minX, center.x - reach);
            minY = Math.min(minY, center.y - reach);
            maxX = Math.max(maxX, center.x + reach);
            maxY = Math.max(maxY, center.y + reach);
        }

        int cols = (int) Math.ceil((maxX - minX) / cell);
        int rows = (int) Math.ceil((maxY - minY) / cell);

        //corner values are shared by four cells each, so the row above is carried over rather
        //than recomputed - the field evaluation is the expensive part of the whole cut
        float[] below = sampleRow(centers, reach, minX, minY, cols, cell);

        for (int row = 0; row < rows; row++) {
            float yBottom = minY + row * cell;
            float yTop = yBottom + cell;
            float[] above = sampleRow(centers, reach, minX, yTop, cols, cell);

            for (int col = 0; col < cols; col++) {
                float xLeft = minX + col * cell;
                float xRight = xLeft + cell;

                float v0 = below[col];
                float v1 = below[col + 1];
                float v2 = above[col + 1];
                float v3 = above[col];

                //a cell nowhere near the threshold cuts nothing and fills either everything
                //or nothing; both are answered without touching its triangles
                boolean in0 = v0 >= THRESHOLD, in1 = v1 >= THRESHOLD;
                boolean in2 = v2 >= THRESHOLD, in3 = v3 >= THRESHOLD;

                if (!in0 && !in1 && !in2 && !in3) continue;

                if (in0 && in1 && in2 && in3) {
                    mesh.fill.add(new float[]{xLeft, yBottom, xRight, yBottom, xRight, yTop});
                    mesh.fill.add(new float[]{xLeft, yBottom, xRight, yTop, xLeft, yTop});
                    continue;
                }

                //a boundary cell: four triangles around the centre, each unambiguous
                float xMid = xLeft + cell * 0.5f;
                float yMid = yBottom + cell * 0.5f;
                float vMid = sample(centers, reach, xMid, yMid);

                cut(mesh, xLeft, yBottom, v0, xRight, yBottom, v1, xMid, yMid, vMid);
                cut(mesh, xRight, yBottom, v1, xRight, yTop, v2, xMid, yMid, vMid);
                cut(mesh, xRight, yTop, v2, xLeft, yTop, v3, xMid, yMid, vMid);
                cut(mesh, xLeft, yTop, v3, xLeft, yBottom, v0, xMid, yMid, vMid);
            }

            below = above;
        }

        return mesh;
    }

    protected static float[] sampleRow(List<Vector2f> centers, float reach, float minX, float y,
                                       int cols, float cell) {
        float[] row = new float[cols + 1];

        for (int col = 0; col <= cols; col++) {
            row[col] = sample(centers, reach, minX + col * cell, y);
        }

        return row;
    }

    /** The field at a point: every system's falloff, summed. The summing is where the merging is. */
    protected static float sample(List<Vector2f> centers, float reach, float x, float y) {
        float reachSq = reach * reach;
        float total = 0f;

        for (Vector2f center : centers) {
            float dx = x - center.x;
            float dy = y - center.y;
            float distSq = dx * dx + dy * dy;

            if (distSq >= reachSq) continue;

            float q = 1f - distSq / reachSq;
            total += q * q * q;
        }

        return total;
    }

    /**
     * One triangle against the threshold. Three corners give eight cases and none of them is
     * ambiguous: all in fills the triangle whole, one in fills the corner cut off by the contour,
     * two in fill the quad the contour leaves behind - and the contour piece itself is always the
     * one segment between the two crossed edges.
     */
    protected static void cut(Mesh mesh, float x0, float y0, float v0, float x1, float y1, float v1,
                              float x2, float y2, float v2) {

        boolean in0 = v0 >= THRESHOLD, in1 = v1 >= THRESHOLD, in2 = v2 >= THRESHOLD;
        int count = (in0 ? 1 : 0) + (in1 ? 1 : 0) + (in2 ? 1 : 0);

        if (count == 0) return;

        if (count == 3) {
            mesh.fill.add(new float[]{x0, y0, x1, y1, x2, y2});
            return;
        }

        //rotate so the odd corner out is corner 0 - the lone inside one, or the lone outside one
        if (count == 1) {
            if (in1) {
                cutOne(mesh, x1, y1, v1, x2, y2, v2, x0, y0, v0);
            } else if (in2) {
                cutOne(mesh, x2, y2, v2, x0, y0, v0, x1, y1, v1);
            } else {
                cutOne(mesh, x0, y0, v0, x1, y1, v1, x2, y2, v2);
            }
            return;
        }

        if (!in1) {
            cutTwo(mesh, x1, y1, v1, x2, y2, v2, x0, y0, v0);
        } else if (!in2) {
            cutTwo(mesh, x2, y2, v2, x0, y0, v0, x1, y1, v1);
        } else {
            cutTwo(mesh, x0, y0, v0, x1, y1, v1, x2, y2, v2);
        }
    }

    /** Corner 0 inside, the other two out: the fill is the clipped corner. */
    protected static void cutOne(Mesh mesh, float x0, float y0, float v0, float x1, float y1,
                                 float v1, float x2, float y2, float v2) {

        float[] a = lerp(x0, y0, v0, x1, y1, v1);
        float[] b = lerp(x0, y0, v0, x2, y2, v2);

        mesh.fill.add(new float[]{x0, y0, a[0], a[1], b[0], b[1]});
        mesh.outline.add(new float[]{a[0], a[1], b[0], b[1]});
    }

    /** Corner 0 outside, the other two in: the fill is the quad past the contour. */
    protected static void cutTwo(Mesh mesh, float x0, float y0, float v0, float x1, float y1,
                                 float v1, float x2, float y2, float v2) {

        float[] a = lerp(x1, y1, v1, x0, y0, v0);
        float[] b = lerp(x2, y2, v2, x0, y0, v0);

        mesh.fill.add(new float[]{x1, y1, x2, y2, b[0], b[1]});
        mesh.fill.add(new float[]{x1, y1, b[0], b[1], a[0], a[1]});
        mesh.outline.add(new float[]{a[0], a[1], b[0], b[1]});
    }

    /** Where the threshold crosses the edge between two samples. */
    protected static float[] lerp(float xA, float yA, float vA, float xB, float yB, float vB) {
        float span = vB - vA;
        float t = Math.abs(span) < 0.0001f ? 0.5f : (THRESHOLD - vA) / span;

        return new float[]{xA + (xB - xA) * t, yA + (yB - yA) * t};
    }
}
