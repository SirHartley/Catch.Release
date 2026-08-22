package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.Global;

import java.util.Map;

public final class FishermanBycatch {
    public static final String FOUND_KEY = "$catchrelease_bycatch_found";
    public static final String EXPLAINED_KEY = "$catchrelease_bycatch_explained";

    private FishermanBycatch() {
    }

    public static void recordFound() {
        if (Global.getSector() == null) return;

        Map<String, Object> data = Global.getSector().getPersistentData();
        if (!Boolean.TRUE.equals(data.get(EXPLAINED_KEY))) data.put(FOUND_KEY, true);
    }

    public static boolean isPending() {
        if (Global.getSector() == null) return false;

        Map<String, Object> data = Global.getSector().getPersistentData();
        return Boolean.TRUE.equals(data.get(FOUND_KEY))
                && !Boolean.TRUE.equals(data.get(EXPLAINED_KEY));
    }

    public static void markExplained() {
        if (Global.getSector() == null) return;

        Map<String, Object> data = Global.getSector().getPersistentData();
        data.remove(FOUND_KEY);
        data.put(EXPLAINED_KEY, true);
    }
}
