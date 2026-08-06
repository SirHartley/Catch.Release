package catchrelease.helper.loading;

import catchrelease.ModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SpriteLoader {

    /** Cache including misses (null), so a missing file is only logged once. */
    protected static final Map<String, SpriteAPI> BY_PATH = new HashMap<>();

    public static SpriteAPI getSprite(String id){
        return Global.getSettings().getSprite(ModPlugin.MOD_ID, id);
    }

    /**
     * Loads a sprite by file path (what data tables store), caching the result including misses -
     * called from render passes, so a missing file logs once, not every frame.
     *
     * @return null if nothing exists at that path; callers must handle it
     */
    public static SpriteAPI loadSprite(String path) {
        if (path == null || path.isEmpty()) return null;
        if (BY_PATH.containsKey(path)) return BY_PATH.get(path);

        SpriteAPI sprite = null;

        try {
            Global.getSettings().loadTexture(path);
            sprite = Global.getSettings().getSprite(path);
        } catch (IOException e) {
            Global.getLogger(SpriteLoader.class).warn("No texture at " + path, e);
        }

        BY_PATH.put(path, sprite);

        return sprite;
    }
}
