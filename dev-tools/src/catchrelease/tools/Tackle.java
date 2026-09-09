package catchrelease.tools;

enum Tackle {

    NONE(1f, 1f, 1f, 1f, 1f),
    SPOOL_GOVERNOR(1.35f, 0.85f, 1f, 1f, 1f),
    INERTIAL_DAMPER(1f, 0.8f, 0.7f, 1f, 1f),
    HOLDFAST_CLAMP(1f, 1f, 1f, 1f, 0.6f),
    BARBED_HEAD(1f, 1f, 1f, 1.35f, 1f);

    final float barSizeMult;
    final float barLiftMult;
    final float barGravityMult;
    final float progressMult;
    final float escapeMult;

    Tackle(float size, float lift, float gravity, float gain, float loss) {
        barSizeMult = size;
        barLiftMult = lift;
        barGravityMult = gravity;
        progressMult = gain;
        escapeMult = loss;
    }
}
