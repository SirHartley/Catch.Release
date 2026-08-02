package catchrelease.helper.loading;

import catchrelease.ModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SpriteLoader {

    /** Paths already asked for, with what came back - a miss included, so it is only logged once. */
    protected static final Map<String, SpriteAPI> BY_PATH = new HashMap<>();

    public static SpriteAPI getSprite(String id){
        return Global.getSettings().getSprite(ModPlugin.MOD_ID, id);
    }

    /**
     * A sprite by its path rather than by the id it was registered under - which is what the data
     * tables hold, since a table names a file.
     * <p>
     * Loaded on first ask and remembered after, misses included: this is called from render passes,
     * and a missing file should cost one line in the log rather than one per frame.
     *
     * @return null if there is nothing at that path, which callers are expected to survive
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
