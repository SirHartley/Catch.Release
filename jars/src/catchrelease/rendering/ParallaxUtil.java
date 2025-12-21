package catchrelease.rendering;

import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.util.vector.Vector2f;

public class ParallaxUtil {

    public static Vector2f computeFillUvOffsetPx(ViewportAPI viewport,
                                                 Vector2f entityLoc,
                                                 float maxDisplacementWorld,
                                                 float fillSizeWorld,
                                                 float fillTexW,
                                                 float fillTexH) {

        Vector2f center = viewport.getCenter();
        Vector2f direction = Vector2f.sub(center, entityLoc, null);
        float distToCenter = direction.length();
        if (distToCenter <= 0f) return new Vector2f(0f, 0f);

        float halfW = viewport.getVisibleWidth() * 0.5f;
        float halfH = viewport.getVisibleHeight() * 0.5f;
        float radius = (float) Math.sqrt(halfW * halfW + halfH * halfH);
        if (radius <= 0f) return new Vector2f(0f, 0f);

        float t = distToCenter / radius;
        if (t > 1f) t = 1f;

        float displacementWorld = maxDisplacementWorld * t;

        direction.normalise(direction);
        direction.scale(displacementWorld);

        float uvOffX = direction.x * (fillTexW / fillSizeWorld);
        float uvOffY = direction.y * (fillTexH / fillSizeWorld);

        return new Vector2f(uvOffX, uvOffY);
    }
}

