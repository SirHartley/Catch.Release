package catchrelease.campaign.fish.colony;

public final class FishSpritePose {

    public final float aspect;

    private final float cos;
    private final float sin;
    private final float width;
    private final float height;
    private final float up;

    public FishSpritePose(float imageWidth, float imageHeight, float direction) {
        float angle = Float.isFinite(direction) ? (direction % 360f + 360f) % 360f : 180f;
        cos = (float) Math.cos(Math.toRadians(angle));
        sin = (float) Math.sin(Math.toRadians(angle));
        float w = Math.max(1f, imageWidth);
        float h = Math.max(1f, imageHeight);
        float length = Math.abs(cos) * w + Math.abs(sin) * h;
        width = w / length;
        height = h / length;
        aspect = Math.abs(sin) * width + Math.abs(cos) * height;

        // Left-painted art was mirrored, not rolled belly-up; retain that convention.
        up = angle >= 135f && angle < 225f ? -1f : 1f;
    }

    public float x(float u, float v) {
        return (u - 0.5f) * width * cos + (v - 0.5f) * height * sin;
    }

    public float y(float u, float v) {
        return (-(u - 0.5f) * width * sin + (v - 0.5f) * height * cos) * up;
    }
}
