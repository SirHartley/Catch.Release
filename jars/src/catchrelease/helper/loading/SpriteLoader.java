package catchrelease.helper.loading;

import catchrelease.ModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Sprites for the rest of the mod, one fresh instance per ask.
 * <p>
 * The engine builds a new {@link SpriteAPI} around the shared GL texture on every
 * {@code getSprite} call - path and category alike - so instance state (size, colour, alpha,
 * blend) is always the caller's own. This loader used to cache and share one instance per path
 * and hand it back "neutral", which meant every screen was mutating every other screen's sprite:
 * the codex blacking cargo icons, result cards inheriting map tints. The cache of instances is
 * gone; what remains cached is only whether a path's texture has been loaded, so a good file is
 * uploaded once and a missing one logs once instead of every frame.
 */
public class SpriteLoader {

    /** Paths already pushed through loadTexture; false marks a miss so it is only logged once. */
    protected static final Map<String, Boolean> LOADED = new HashMap<>();

    /** A fresh instance over the settings-registered texture behind this mod id. */
    public static SpriteAPI getSprite(String id) {
        return Global.getSettings().getSprite(ModPlugin.MOD_ID, id);
    }

    /**
     * Sprite by file path (what data tables store): the texture is loaded on first sight, and
     * every call after that hands back a fresh instance at the image's own size.
     *
     * @return null if nothing exists at that path; callers must handle it
     */
    public static SpriteAPI loadSprite(String path) {
        if (path == null || path.isEmpty()) return null;

        Boolean loaded = LOADED.get(path);

        if (loaded == null) {
            try {
                Global.getSettings().loadTexture(path);
                loaded = true;
            } catch (IOException e) {
                Global.getLogger(SpriteLoader.class).warn("No texture at " + path, e);
                loaded = false;
            }

            LOADED.put(path, loaded);
        }

        return loaded ? Global.getSettings().getSprite(path) : null;
    }
}
