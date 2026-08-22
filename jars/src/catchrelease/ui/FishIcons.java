package catchrelease.ui;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.codex.FishCodexEntryState;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;


public final class FishIcons {


    public static final float BACKLIGHT_ALPHA = 0.35f;


    public static final float RIM_ALPHA = 0.45f;
    public static final float RIM_OFFSET = 1f;


    public static final float RIM_COVER_FLOOR = 0.85f;

    private FishIcons() {
    }


    public static void drawBacklit(FishSpec spec, float centerX, float centerY,
                                   float backlightRadius, float artSize, float alphaMult) {
        if (spec == null || alphaMult <= 0f) return;

        Disc.draw(centerX, centerY, backlightRadius, spec.rarity.color,
                BACKLIGHT_ALPHA * alphaMult, 0f, true);
        draw(spec, centerX, centerY, artSize, alphaMult);
    }


    public static void draw(FishSpec spec, float centerX, float centerY, float size,
                            float alphaMult) {
        if (spec == null || alphaMult <= 0f) return;

        String path = FishCodex.getIcon(spec);
        SpriteAPI icon = SpriteLoader.loadSprite(path);
        if (icon == null || icon.getWidth() <= 0f || icon.getHeight() <= 0f) return;

            float scale = Math.min(size / icon.getWidth(), size / icon.getHeight());
        icon.setSize(icon.getWidth() * scale, icon.getHeight() * scale);
        icon.setNormalBlend();

        int x = Math.round(centerX);
        int y = Math.round(centerY);

        if (FishCodexEntryState.resolve(spec.id).isCaught()) {
            icon.setColor(Color.WHITE);
            icon.setAlphaMult(alphaMult);
            icon.renderAtCenter(x, y);
            return;
        }

        float rim = RIM_ALPHA * alphaMult * coverShare(alphaMult);

        if (rim > 0f) {
            icon.setColor(Color.WHITE);
            icon.setAlphaMult(rim);
            icon.renderAtCenter(x - RIM_OFFSET, y);
            icon.renderAtCenter(x + RIM_OFFSET, y);
            icon.renderAtCenter(x, y - RIM_OFFSET);
            icon.renderAtCenter(x, y + RIM_OFFSET);
        }

        icon.setColor(Color.BLACK);
        icon.setAlphaMult(alphaMult);
        icon.renderAtCenter(x, y);
    }


    protected static float coverShare(float alphaMult) {
        if (alphaMult >= 1f) return 1f;
        if (alphaMult <= RIM_COVER_FLOOR) return 0f;

        return (alphaMult - RIM_COVER_FLOOR) / (1f - RIM_COVER_FLOOR);
    }
}
