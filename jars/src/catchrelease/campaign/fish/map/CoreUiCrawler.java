package catchrelease.campaign.fish.map;

import catchrelease.reflection.ReflectionUtils;

/**
 * The short walk from the running game to the screen the player is looking at. The class and
 * member names on this path - {@code AppDriver}, {@code CampaignState}, {@code getCore} - are
 * stable across game versions, but everything is still reached by name through reflection: the
 * types in those declarations are obfuscated, and a compiled mod that names them in a call
 * descriptor is a mod that dies on the next release. Names in strings survive; names in bytecode
 * do not.
 * <p>
 * Everything here answers null rather than throwing: a failed crawl means "nothing to attach to
 * this frame", which the caller treats the same as the screen not being open.
 */
public final class CoreUiCrawler {

    public static final String APP_DRIVER = "com.fs.state.AppDriver";
    public static final String CAMPAIGN_STATE = "com.fs.starfarer.campaign.CampaignState";

    private CoreUiCrawler() {
    }

    /**
     * The core UI currently on screen - docked inside an encounter dialog if one is up, since the
     * core lives in a different parent while docked, else the campaign's own. Null when there is
     * no campaign, or no core UI to speak of.
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
     * The intel screen, if that is the tab the core UI is currently showing. Identified by
     * capability rather than by class: {@code getEventsPanel} exists on exactly one class in the
     * game, and it is this screen - the Command screen carries look-alike members, so anything
     * less specific would wander over there.
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
