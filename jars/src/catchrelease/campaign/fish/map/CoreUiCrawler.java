package catchrelease.campaign.fish.map;

import catchrelease.reflection.ReflectionUtils;

/**
 * The short walk from the running game to the screen the player is looking at. Names on this path
 * ({@code AppDriver}, {@code CampaignState}, {@code getCore}) are stable across versions, but
 * reached via reflection since the declared types are obfuscated - a compiled call descriptor
 * naming them breaks on the next release.
 * <p>
 * Everything here returns null rather than throwing; the caller treats a failed crawl the same as
 * the screen not being open.
 */
public final class CoreUiCrawler {

    public static final String APP_DRIVER = "com.fs.state.AppDriver";
    public static final String CAMPAIGN_STATE = "com.fs.starfarer.campaign.CampaignState";

    private CoreUiCrawler() {
    }

    /**
     * Core UI currently on screen - docked inside an encounter dialog if one is up (the core lives
     * in a different parent while docked), else the campaign's own. Null if neither exists.
     */
    public static Object getCoreUi() {
        try {
            Object driver = ReflectionUtils.invokeStatic(Class.forName(APP_DRIVER), "getInstance");
            Object state = ReflectionUtils.invokeIfExists(driver, "getCurrentState");
            if (state == null || !CAMPAIGN_STATE.equals(state.getClass().getName())) return null;

            Object dialog = ReflectionUtils.invokeIfExists(state, "getEncounterDialog");
            if (dialog != null) {
                Object docked = ReflectionUtils.invokeIfExists(dialog, "getCoreUI");
                if (docked != null) return docked;
            }

            return ReflectionUtils.invokeIfExists(state, "getCore");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Intel screen, if that's the current tab. Identified by capability ({@code getEventsPanel},
     * unique to this screen) rather than by class, since the Command screen has look-alike members.
     */
    public static Object getIntelScreen(Object core) {
        try {
            Object tab = ReflectionUtils.invokeIfExists(core, "getCurrentTab");
            if (tab == null) return null;

            return ReflectionUtils.hasMethodOfName(tab, "getEventsPanel") ? tab : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
