package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

/**
 * A species' face, drawn the way the player's knowledge allows: the art as painted for a
 * species somebody has landed, and the same art as a black silhouette for one known only from
 * survey data - the shape of the thing is on the chart, the look of it still has to be caught.
 * <p>
 * The silhouette keeps the art's alpha, so it is the fish's outline rather than a black box,
 * and wears a faint light rim - the same sprite offset a pixel each way underneath - so it
 * still reads on the dark fields most of these screens are.
 */
public final class FishIcons {

    /** The rim: the silhouette's own shape in light, one pixel proud on each side. */
    public static final float RIM_ALPHA = 0.45f;
    public static final float RIM_OFFSET = 1f;

    private FishIcons() {
    }

    /** The face at a centre point, fitted (never stretched) into a square of the given size. */
    public static void draw(FishSpec spec, float centerX, float centerY, float size,
                            float alphaMult) {
        if (spec == null || alphaMult <= 0f) return;

        SpriteAPI icon = SpriteLoader.loadSprite(FishCodex.getIcon(spec));
        if (icon == null || icon.getWidth() <= 0f || icon.getHeight() <= 0f) return;

        float scale = Math.min(size / icon.getWidth(), size / icon.getHeight());
        icon.setSize(icon.getWidth() * scale, icon.getHeight() * scale);
        icon.setNormalBlend();

        int x = Math.round(centerX);
        int y = Math.round(centerY);

        if (FishLog.isCaught(spec.id)) {
            icon.setColor(Color.WHITE);
            icon.setAlphaMult(alphaMult);
            icon.renderAtCenter(x, y);
            return;
        }

        //the rim first, then the shape over it
        icon.setColor(Color.WHITE);
        icon.setAlphaMult(RIM_ALPHA * alphaMult);
        icon.renderAtCenter(x - RIM_OFFSET, y);
        icon.renderAtCenter(x + RIM_OFFSET, y);
        icon.renderAtCenter(x, y - RIM_OFFSET);
        icon.renderAtCenter(x, y + RIM_OFFSET);

        icon.setColor(Color.BLACK);
        icon.setAlphaMult(alphaMult);
        icon.renderAtCenter(x, y);
    }
}
