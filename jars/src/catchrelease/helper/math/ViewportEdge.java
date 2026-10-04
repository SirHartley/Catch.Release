package catchrelease.helper.math;

import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.util.vector.Vector2f;

public final class ViewportEdge {

    private ViewportEdge() {
    }

    public static boolean contains(ViewportAPI viewport, Vector2f point, float margin) {
        return valid(viewport) && point != null
                && point.x >= viewport.getLLX() - margin
                && point.x <= viewport.getLLX() + viewport.getVisibleWidth() + margin
                && point.y >= viewport.getLLY() - margin
                && point.y <= viewport.getLLY() + viewport.getVisibleHeight() + margin;
    }

    public static Vector2f outside(ViewportAPI viewport, float marginPixels, float angleDegrees) {
        if (!valid(viewport)) return null;

        float padX = viewport.convertScreenWidthToWorldWidth(marginPixels);
        float padY = viewport.convertScreenHeightToWorldHeight(marginPixels);
        if (!Float.isFinite(padX) || !Float.isFinite(padY) || padX < 0f || padY < 0f) return null;

        float halfWidth = viewport.getVisibleWidth() * 0.5f;
        float halfHeight = viewport.getVisibleHeight() * 0.5f;
        double radians = Math.toRadians(angleDegrees);
        double dx = Math.cos(radians);
        double dy = Math.sin(radians);
        double distance = Math.min((halfWidth + padX) / Math.abs(dx),
                (halfHeight + padY) / Math.abs(dy));
        return new Vector2f(viewport.getLLX() + halfWidth + (float) (dx * distance),
                viewport.getLLY() + halfHeight + (float) (dy * distance));
    }

    private static boolean valid(ViewportAPI viewport) {
        return viewport != null && Float.isFinite(viewport.getLLX()) && Float.isFinite(viewport.getLLY())
                && Float.isFinite(viewport.getVisibleWidth()) && viewport.getVisibleWidth() > 0f
                && Float.isFinite(viewport.getVisibleHeight()) && viewport.getVisibleHeight() > 0f;
    }
}
