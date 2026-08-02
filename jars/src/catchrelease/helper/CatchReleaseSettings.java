package catchrelease.helper;

import catchrelease.ModPlugin;
import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;

/**
 * What the player has turned on and off, from the LunaLib settings menu.
 * <p>
 * Every read goes through here rather than through LunaSettings directly, for two reasons: the ids
 * are written down once instead of being repeated as strings at each use, and every setting has a
 * value even when the menu has never been opened - LunaLib hands back null until it has loaded the
 * defaults out of the csv, and a null is not an answer anything can be drawn from.
 */
public class CatchReleaseSettings {

    public static final String CELEBRATION = "catchrelease_celebration";

    /** Confetti, flash and the specimen thrown up over the track. The readout is not affected. */
    public static boolean isCelebrationEnabled() {
        return getBoolean(CELEBRATION, false);
    }

    /**
     * @param fallback used when the setting has no value yet, and when LunaLib is not there at all -
     *                 it is a declared dependency, but a missing one should cost the setting rather
     *                 than the mod
     */
    protected static boolean getBoolean(String id, boolean fallback) {
        try {
            Boolean value = LunaSettings.getBoolean(ModPlugin.MOD_ID, id);

            return value == null ? fallback : value;
        } catch (Throwable t) {
            Global.getLogger(CatchReleaseSettings.class).warn("Could not read setting " + id, t);

            return fallback;
        }
    }
}
