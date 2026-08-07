package catchrelease.helper.loading;

import catchrelease.ModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SpriteLoader {

    /** Cache including misses (null), so a missing file is only logged once. */
    protected static final Map<String, SpriteAPI> BY_PATH = new HashMap<>();

    /**
     * Each sprite's size as it was loaded, because nothing else remembers it.
     * <p>
     * {@link SpriteAPI#getWidth()} reports the size the sprite was last drawn at, not the size of
     * the image, and {@code setSize} overwrites it - so once something has resized one there is no
     * way left to ask how big it actually is. {@code getTextureWidth()} is not it either: that is
     * the fraction of the padded GL texture the image occupies, not a number of pixels.
     */
    protected static final Map<String, float[]> NATIVE_BY_PATH = new HashMap<>();

    public static SpriteAPI getSprite(String id){
        return Global.getSettings().getSprite(ModPlugin.MOD_ID, id);
    }

    /**
     * Loads a sprite by file path (what data tables store), caching the result including misses -
     * called from render passes, so a missing file logs once, not every frame.
     * <p>
     * Always handed back at the size it was loaded at. One sprite object is shared by everything
     * that asks for that path, and drawing it is a matter of resizing it first, so without this the
     * last caller to draw a fish decides how big it is for every other caller - and a sprite left
     * small by the sector map's icons stays small in the cargo hold, since a view that fits an image
     * to a cell will shrink one that is too big and has no reason to grow one that already fits.
     *
     * @return null if nothing exists at that path; callers must handle it
     */
    public static SpriteAPI loadSprite(String path) {
        if (path == null || path.isEmpty()) return null;
        if (BY_PATH.containsKey(path)) return atNativeSize(path, BY_PATH.get(path));

        SpriteAPI sprite = null;

        try {
            Global.getSettings().loadTexture(path);
            sprite = Global.getSettings().getSprite(path);
        } catch (IOException e) {
            Global.getLogger(SpriteLoader.class).warn("No texture at " + path, e);
        }

        BY_PATH.put(path, sprite);

        //recorded before anything has had the chance to draw it, so this is the image's own size
        if (sprite != null) NATIVE_BY_PATH.put(path, new float[]{sprite.getWidth(), sprite.getHeight()});

        return sprite;
    }

    /**
     * Undoes whatever the last caller left on this one.
     * <p>
     * Size was the first thing to leak and is not the only one. A {@link SpriteAPI} carries colour,
     * alpha and blend mode as well, all of them sticky, and one object is shared by every caller
     * that asks for the path - so the sector map drawing an undiscovered species as a black
     * silhouette ({@code FishIcons}) left every later draw of that fish black, the cargo hold
     * included. Handing the sprite back neutral is what makes a shared object safe to share:
     * a caller that wants it tinted says so, and a caller that says nothing gets the image.
     */
    protected static SpriteAPI atNativeSize(String path, SpriteAPI sprite) {
        if (sprite == null) return null;

        float[] size = NATIVE_BY_PATH.get(path);
        if (size != null) sprite.setSize(size[0], size[1]);

        sprite.setColor(Color.WHITE);
        sprite.setAlphaMult(1f);
        sprite.setNormalBlend();

        return sprite;
    }
}
