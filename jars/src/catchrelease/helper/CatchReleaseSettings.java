package catchrelease.helper;

import catchrelease.ModPlugin;
import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;

/**
 * Player toggles from the LunaLib settings menu, read through here rather than LunaSettings directly
 * so ids aren't repeated as strings everywhere, and so a fallback is used before the menu has loaded
 * defaults from the csv (LunaLib returns null until then).
 */
public class CatchReleaseSettings {

    public static final String CELEBRATION = "catchrelease_celebration";

    /** Confetti, flash and the specimen thrown up over the track. The readout is not affected. */
    public static boolean isCelebrationEnabled() {
        return getBoolean(CELEBRATION, false);
    }

    /** @param fallback used when unset, or when LunaLib itself is missing despite being a declared dependency */
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
