package catchrelease.campaign.fish.map;

import catchrelease.reflection.ReflectionUtils;

public final class CoreUiCrawler {
    public static final String APP_DRIVER = "com.fs.state.AppDriver";
    public static final String CAMPAIGN_STATE = "com.fs.starfarer.campaign.CampaignState";

    private CoreUiCrawler() {
    }

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
