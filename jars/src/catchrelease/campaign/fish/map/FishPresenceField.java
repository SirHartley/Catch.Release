package catchrelease.campaign.fish.map;

import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The organic shape around a set of systems: one filled, outlined blob rather than a pile of
 * circles.
 * <p>
 * Drawing a circle per system and letting them overlap gives lens-shaped seams where they cross -
 * double-darkened fill inside, hard corners on the outline. What is wanted is the union, with the
 * waists between neighbouring circles rounded off - which is exactly what a metaball field gives
 * for free: each system contributes a smooth falloff, the contributions sum, and the contour at a
 * fixed threshold is a single shape whose bridges and junctions are rounded by the addition
 * itself. No seam exists because no circle exists - only the field does.
 * <p>
 * The contour is cut by marching triangles - a grid cell split into four triangles around its
 * centre admits no ambiguous case - and what comes out is not triangles but <i>loops</i>: the
 * contour segments chained end to end into closed rings, then rounded twice over with Chaikin's
 * corner cutting, so the per-cell facets melt into a curve. The renderer fills the rings by
 * stencil parity and strokes them directly, which keeps the fill and the outline the same shape
 * by construction.
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

    /** Grid cells per visual radius. Chaikin does the smoothing; the grid only has to be honest. */
    public static final float CELLS_PER_RADIUS = 3f;

    /** Rounds of corner cutting. Each round doubles the points and halves the corners. */
    public static final int SMOOTHING_ROUNDS = 2;

    /** One blob's geometry: closed rings in world coordinates, and the box around all of them. */
    public static class Mesh {
        /** Each ring is a flat x,y polyline; the last point connects back to the first. */
        public final List<float[]> loops = new ArrayList<>();

        public float minX, minY, maxX, maxY;

        public boolean isEmpty() {
            return loops.isEmpty();
        }
    }

    /** A contour crossing, kept with the exact bits of its endpoints so chaining can match them. */
    protected static class Segment {
        final float x1, y1, x2, y2;
        boolean used = false;

        Segment(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
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

        List<Segment> segments = cutSegments(centers, visualRadius);
        List<float[]> loops = chainLoops(segments);

        for (float[] loop : loops) {
            float[] smoothed = loop;
            for (int round = 0; round < SMOOTHING_ROUNDS; round++) {
                smoothed = chaikin(smoothed);
            }

            mesh.loops.add(smoothed);
        }

        measure(mesh);
        return mesh;
    }

    /** Marching triangles over the field, keeping only the contour crossings. */
    protected static List<Segment> cutSegments(List<Vector2f> centers, float visualRadius) {
        List<Segment> segments = new ArrayList<>();

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
            //always minX + n * cell, never the neighbour plus cell: the two round differently in
            //float, and a vertex that exists twice with different bits is a chain that breaks
            float yBottom = minY + row * cell;
            float yTop = minY + (row + 1) * cell;
            float[] above = sampleRow(centers, reach, minX, yTop, cols, cell);

            for (int col = 0; col < cols; col++) {
                float xLeft = minX + col * cell;
                float xRight = minX + (col + 1) * cell;

                float v0 = below[col];
                float v1 = below[col + 1];
                float v2 = above[col + 1];
                float v3 = above[col];

                boolean in0 = v0 >= THRESHOLD, in1 = v1 >= THRESHOLD;
                boolean in2 = v2 >= THRESHOLD, in3 = v3 >= THRESHOLD;

                //a cell wholly in or wholly out crosses nothing
                if (in0 == in1 && in1 == in2 && in2 == in3) continue;

                float xMid = xLeft + cell * 0.5f;
                float yMid = yBottom + cell * 0.5f;
                float vMid = sample(centers, reach, xMid, yMid);

                cut(segments, xLeft, yBottom, v0, xRight, yBottom, v1, xMid, yMid, vMid);
                cut(segments, xRight, yBottom, v1, xRight, yTop, v2, xMid, yMid, vMid);
                cut(segments, xRight, yTop, v2, xLeft, yTop, v3, xMid, yMid, vMid);
                cut(segments, xLeft, yTop, v3, xLeft, yBottom, v0, xMid, yMid, vMid);
            }

            below = above;
        }

        return segments;
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
     * One triangle against the threshold: any triangle the contour passes through contributes
     * exactly one segment, between its two crossed edges. Adjacent triangles compute the crossing
     * on their shared edge from the same two samples, so the endpoints match to the bit - which
     * is what lets the chaining below match them exactly instead of within a tolerance.
     */
    protected static void cut(List<Segment> segments, float x0, float y0, float v0, float x1,
                              float y1, float v1, float x2, float y2, float v2) {

        boolean in0 = v0 >= THRESHOLD, in1 = v1 >= THRESHOLD, in2 = v2 >= THRESHOLD;
        int count = (in0 ? 1 : 0) + (in1 ? 1 : 0) + (in2 ? 1 : 0);

        if (count == 0 || count == 3) return;

        //rotate so the odd corner out is corner 0, then the crossed edges are 0-1 and 0-2
        if (count == 1) {
            if (in1) addSegment(segments, x1, y1, v1, x2, y2, v2, x0, y0, v0);
            else if (in2) addSegment(segments, x2, y2, v2, x0, y0, v0, x1, y1, v1);
            else addSegment(segments, x0, y0, v0, x1, y1, v1, x2, y2, v2);
        } else {
            if (!in0) addSegment(segments, x0, y0, v0, x1, y1, v1, x2, y2, v2);
            else if (!in1) addSegment(segments, x1, y1, v1, x2, y2, v2, x0, y0, v0);
            else addSegment(segments, x2, y2, v2, x0, y0, v0, x1, y1, v1);
        }
    }

    protected static void addSegment(List<Segment> segments, float x0, float y0, float v0,
                                     float x1, float y1, float v1, float x2, float y2, float v2) {

        float[] a = lerp(x0, y0, v0, x1, y1, v1);
        float[] b = lerp(x0, y0, v0, x2, y2, v2);

        segments.add(new Segment(a[0], a[1], b[0], b[1]));
    }

    /**
     * Where the threshold crosses the edge between two samples - computed in a canonical order,
     * because the two triangles sharing an edge arrive with the endpoints swapped, and the same
     * crossing computed from opposite ends rounds to different bits. Sorted first, both sides run
     * the identical arithmetic, and the chaining can match endpoints exactly.
     */
    protected static float[] lerp(float xA, float yA, float vA, float xB, float yB, float vB) {
        if (xB < xA || (xB == xA && yB < yA)) {
            float tx = xA, ty = yA, tv = vA;
            xA = xB;
            yA = yB;
            vA = vB;
            xB = tx;
            yB = ty;
            vB = tv;
        }

        float span = vB - vA;
        float t = Math.abs(span) < 0.0001f ? 0.5f : (THRESHOLD - vA) / span;

        return new float[]{xA + (xB - xA) * t, yA + (yB - yA) * t};
    }

    /**
     * Chains the segments into closed rings. A marching contour is manifold - every endpoint is
     * shared by exactly two segments - so each walk is unambiguous: leave a point by the one
     * segment that did not bring you there, until the start comes round again.
     */
    protected static List<float[]> chainLoops(List<Segment> segments) {
        Map<Long, List<Segment>> byPoint = new HashMap<>(segments.size() * 2);

        for (Segment segment : segments) {
            byPoint.computeIfAbsent(key(segment.x1, segment.y1), k -> new ArrayList<>(2)).add(segment);
            byPoint.computeIfAbsent(key(segment.x2, segment.y2), k -> new ArrayList<>(2)).add(segment);
        }

        List<float[]> loops = new ArrayList<>();

        for (Segment start : segments) {
            if (start.used) continue;

            List<Float> points = new ArrayList<>();
            Segment current = start;
            float atX = start.x1, atY = start.y1;

            while (current != null && !current.used) {
                current.used = true;
                points.add(atX);
                points.add(atY);

                //cross the segment to its far end, then leave by the other segment there
                if (atX == current.x1 && atY == current.y1) {
                    atX = current.x2;
                    atY = current.y2;
                } else {
                    atX = current.x1;
                    atY = current.y1;
                }

                Segment next = null;
                List<Segment> here = byPoint.get(key(atX, atY));

                if (here != null) {
                    for (Segment candidate : here) {
                        if (!candidate.used) {
                            next = candidate;
                            break;
                        }
                    }
                }

                current = next;
            }

            //a ring needs room to be one; anything shorter is grid noise
            if (points.size() >= 6) {
                float[] loop = new float[points.size()];
                for (int i = 0; i < loop.length; i++) loop[i] = points.get(i);

                loops.add(loop);
            }
        }

        return loops;
    }

    /** The endpoint's exact bits as a key - matching floats, not nearby ones. */
    protected static long key(float x, float y) {
        return ((long) Float.floatToIntBits(x) << 32) | (Float.floatToIntBits(y) & 0xFFFFFFFFL);
    }

    /**
     * One round of Chaikin's corner cutting on a closed ring: every edge donates the points a
     * quarter in from each end, and the corners between them fall away. Twice over, the marching
     * grid's facets read as a drawn curve.
     */
    protected static float[] chaikin(float[] loop) {
        int count = loop.length / 2;
        float[] out = new float[count * 4];

        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;

            float x1 = loop[i * 2], y1 = loop[i * 2 + 1];
            float x2 = loop[j * 2], y2 = loop[j * 2 + 1];

            out[i * 4] = x1 * 0.75f + x2 * 0.25f;
            out[i * 4 + 1] = y1 * 0.75f + y2 * 0.25f;
            out[i * 4 + 2] = x1 * 0.25f + x2 * 0.75f;
            out[i * 4 + 3] = y1 * 0.25f + y2 * 0.75f;
        }

        return out;
    }

    protected static void measure(Mesh mesh) {
        mesh.minX = Float.MAX_VALUE;
        mesh.minY = Float.MAX_VALUE;
        mesh.maxX = -Float.MAX_VALUE;
        mesh.maxY = -Float.MAX_VALUE;

        for (float[] loop : mesh.loops) {
            for (int i = 0; i < loop.length; i += 2) {
                mesh.minX = Math.min(mesh.minX, loop[i]);
                mesh.maxX = Math.max(mesh.maxX, loop[i]);
                mesh.minY = Math.min(mesh.minY, loop[i + 1]);
                mesh.maxY = Math.max(mesh.maxY, loop[i + 1]);
            }
        }
    }
}
