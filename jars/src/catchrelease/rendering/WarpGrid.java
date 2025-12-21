package catchrelease.rendering;

public final class WarpGrid {
    public static final class MutatingValue {
        public float value;
        public float min;
        public float max;
        public float rate;
        public float rateSign;
        public float sign = 0f;

        public MutatingValue() {}

        public MutatingValue(float min, float max, float rate) {
            this.min = min;
            this.max = max;
            this.rate = Math.abs(rate);
            this.value = min + (float) Math.random() * (max - min);
            this.rateSign = Math.signum(rate);
        }

        public void set(float min, float max) {
            this.min = min;
            this.max = max;
            this.value = min + (float) Math.random() * (max - min);
        }

        public void advance(float amount) {
            if (rateSign != 0) value += amount * rate * rateSign;
            else value += amount * rate;

            if (value > max) {
                value = max;
                rateSign = -1f;
            } else if (value < min) {
                value = min;
                rateSign = 1f;
            }
        }

        public float getValue() {
            return sign != 0f ? value * sign : value;
        }

        public void setRandomSign() {
            sign = (float) Math.signum(Math.random() - 0.5f);
            if (sign == 0f) sign = 1f;
        }

        public void setRandomRateSign() {
            rateSign = (float) Math.signum(Math.random() - 0.5f);
            if (rateSign == 0f) rateSign = 1f;
        }
    }

    public static final class WSVertex {
        public final MutatingValue theta;
        public final MutatingValue radius;

        public WSVertex() {
            theta = new MutatingValue(
                    -360f * ((float) Math.random() * 30f + 1f),
                    360f * ((float) Math.random() * 30f + 1f),
                    30f + 70f * (float) Math.random()
            );
            radius = new MutatingValue(
                    0f,
                    10f + 15f * (float) Math.random(),
                    3f + 7f * (float) Math.random()
            );
        }

        public void advance(float amount) {
            theta.advance(amount);
            radius.advance(amount);
        }
    }

    public static final class WarpOffset {
        public float dx;
        public float dy;
        public WarpOffset(float dx, float dy) { this.dx = dx; this.dy = dy; }
    }

    private final int wide;
    private final int tall;
    private final WSVertex[][] v;

    public WarpGrid(int verticesWide, int verticesTall,
                    float minWarpRadius, float maxWarpRadius,
                    float warpRateMult) {
        this.wide = Math.max(2, verticesWide);
        this.tall = Math.max(2, verticesTall);

        v = new WSVertex[wide][tall];
        for (int i = 0; i < wide; i++) {
            for (int j = 0; j < tall; j++) {
                v[i][j] = new WSVertex();
                v[i][j].radius.set(minWarpRadius, maxWarpRadius);
                v[i][j].radius.rate *= warpRateMult;
                v[i][j].theta.rate *= warpRateMult;
            }
        }
    }

    public int getWide() { return wide; }
    public int getTall() { return tall; }

    public void advance(float amount) {
        for (int i = 0; i < wide; i++) {
            for (int j = 0; j < tall; j++) {
                v[i][j].advance(amount);
            }
        }
    }

    /**
     * Returns the warp offset (dx, dy) for a grid vertex.
     * Border vertices do not warp.
     */
    public WarpOffset getOffset(int i, int j) {
        if (i <= 0 || j <= 0 || i >= wide - 1 || j >= tall - 1) return new WarpOffset(0f, 0f);

        float thetaRad = (float) Math.toRadians(v[i][j].theta.getValue());
        float radius = v[i][j].radius.getValue();

        float dx = (float) Math.cos(thetaRad) * radius;
        float dy = (float) Math.sin(thetaRad) * radius;
        return new WarpOffset(dx, dy);
    }
}
