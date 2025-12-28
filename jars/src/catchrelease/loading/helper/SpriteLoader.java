package catchrelease.loading.helper;

import catchrelease.ModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

public class SpriteLoader {

    public static SpriteAPI getSprite(String id){
        return Global.getSettings().getSprite(ModPlugin.MOD_ID, id);
    }
}
