package catchrelease.helper.loading;

import catchrelease.ModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class SpriteLoader {


    protected static final Map<String, Boolean> LOADED = new HashMap<>();


    public static SpriteAPI getSprite(String id) {
        return Global.getSettings().getSprite(ModPlugin.MOD_ID, id);
    }


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
