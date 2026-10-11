package catchrelease.config;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import org.lwjgl.util.vector.Vector2f;

public final class FishingSounds {

    public static SoundAPI playUISound(String id, float pitch, float volume) {
        return Global.getSoundPlayer().playUISound(id, pitch, volume * PlayerSettings.soundVolume(id));
    }

    public static SoundAPI playSound(String id, float pitch, float volume, Vector2f position, Vector2f velocity) {
        return Global.getSoundPlayer().playSound(id, pitch, volume * PlayerSettings.soundVolume(id), position, velocity);
    }

    public static void playUILoop(String id, float pitch, float volume) {
        Global.getSoundPlayer().playUILoop(id, pitch, volume * PlayerSettings.soundVolume(id));
    }

    public static void playLoop(String id, Object owner, float pitch, float volume, Vector2f position, Vector2f velocity) {
        Global.getSoundPlayer().playLoop(id, owner, pitch, volume * PlayerSettings.soundVolume(id), position, velocity);
    }

    private FishingSounds() {
    }
}
