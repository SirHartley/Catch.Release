package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.codex.FishCodexEntryState;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

/**
 * A species' face, drawn the way the player's knowledge allows: the art as painted for a
 * species somebody has landed, and the same art as a black silhouette for one known only from
 * range data - the shape of the thing is on the chart, the look of it still has to be caught.
 * <p>
 * The silhouette keeps the art's alpha, so it is the fish's outline rather than a black box,
 * and wears a faint light rim - the same sprite offset a pixel each way underneath - so it
 * still reads on the dark fields most of these screens are.
 * <p>
 * <b>The rim is the artwork, and that is a thing to be careful with.</b> {@code setColor}
 * multiplies, so white is not a tint but the absence of one: the four rim copies are the fish as
 * painted, and the only reason nobody sees them is the black copy over the top. Anything that
 * makes that copy less than opaque - a fading panel, most of all - lets the real fish through.
 * See {@link #RIM_COVER_FLOOR}.
 */
public final class FishIcons {

    /** Rarity-coloured light behind portraits in the Codex, chart shop, and Intel. */
    public static final float BACKLIGHT_ALPHA = 0.35f;

    /** The rim: the silhouette's own shape in light, one pixel proud on each side. */
    public static final float RIM_ALPHA = 0.45f;
    public static final float RIM_OFFSET = 1f;

    /**
     * Below this much panel opacity the rim is not drawn at all, and above it fades in.
     * <p>
     * The rim is four copies of the sprite drawn <i>untinted</i> - {@code setColor} multiplies, so
     * white is the artwork in its own colours - and the only thing hiding them is the black copy
     * over the top. That copy is drawn at the panel's own alpha, so while a panel is fading in it
     * is translucent, and four translucent copies of the real fish show straight through it. Every
     * screen that lines these up fades in, which is why opening the map flashed the artwork of
     * species nobody has caught.
     * <p>
     * There is no tint that fixes it: a multiply cannot lighten, so a light rim has to be the art.
     * What can be fixed is when it is allowed to exist - which is only once the thing covering it
     * is nearly opaque. Below the floor the icon is a plain silhouette fading in, which is the
     * safe direction to be wrong in; at rest it looks exactly as it did.
     */
    public static final float RIM_COVER_FLOOR = 0.85f;

    private FishIcons() {
    }

    /**
     * The complete portrait stage shared by screens that present one named species: rarity light
     * behind the knowledge-aware face. Keeping the two draws together prevents a new surface from
     * remembering the silhouette but losing the backlight (or inventing a slightly different one).
     */
    public static void drawBacklit(FishSpec spec, float centerX, float centerY,
                                   float backlightRadius, float artSize, float alphaMult) {
        if (spec == null || alphaMult <= 0f) return;

        Disc.draw(centerX, centerY, backlightRadius, spec.rarity.color,
                BACKLIGHT_ALPHA * alphaMult, 0f, true);
        draw(spec, centerX, centerY, artSize, alphaMult);
    }

    /** The face at a centre point, fitted (never stretched) into a square of the given size. */
    public static void draw(FishSpec spec, float centerX, float centerY, float size,
                            float alphaMult) {
        if (spec == null || alphaMult <= 0f) return;

        String path = FishCodex.getIcon(spec);
        SpriteAPI icon = SpriteLoader.loadSprite(path);
        if (icon == null || icon.getWidth() <= 0f || icon.getHeight() <= 0f) return;

        try {
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

            //the rim first, then the shape over it - but only while the shape is opaque enough to
            //be a lid rather than a tint. See RIM_COVER_FLOOR: these four draws are the artwork
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
        } finally {
            //the sprite object is shared across the Codex, map, cargo and result screens
            SpriteLoader.resetSprite(path, icon);
        }
    }

    /**
     * How much of the rim this panel's opacity has earned: nothing until the black copy is nearly
     * a lid, then all of it. Fades rather than snaps, so the rim arrives with the panel instead of
     * appearing on it.
     */
    protected static float coverShare(float alphaMult) {
        if (alphaMult >= 1f) return 1f;
        if (alphaMult <= RIM_COVER_FLOOR) return 0f;

        return (alphaMult - RIM_COVER_FLOOR) / (1f - RIM_COVER_FLOOR);
    }
}
