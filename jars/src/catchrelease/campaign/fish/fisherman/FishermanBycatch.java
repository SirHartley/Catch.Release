package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.Global;

import java.util.Map;

/**
 * Remembers the first piece of treasure landed alongside a fish until the Fisherman has named it.
 * <p>
 * Discovery is recorded at the same point the treasure is actually awarded, rather than when it is
 * rolled or touched during the minigame. Once explained, later bycatch does not reopen the greeting.
 */
public final class FishermanBycatch {

    public static final String FOUND_KEY = "$catchrelease_bycatch_found";
    public static final String EXPLAINED_KEY = "$catchrelease_bycatch_explained";

    private FishermanBycatch() {
    }

    /** Marks the first successful bycatch recovery. Safe before the campaign sector exists. */
    public static void recordFound() {
        if (Global.getSector() == null) return;

        Map<String, Object> data = Global.getSector().getPersistentData();
        if (!Boolean.TRUE.equals(data.get(EXPLAINED_KEY))) data.put(FOUND_KEY, true);
    }

    /** True between the first recovery and the next eligible Fisherman conversation. */
    public static boolean isPending() {
        if (Global.getSector() == null) return false;

        Map<String, Object> data = Global.getSector().getPersistentData();
        return Boolean.TRUE.equals(data.get(FOUND_KEY))
                && !Boolean.TRUE.equals(data.get(EXPLAINED_KEY));
    }

    /** Consumes the one-time explanation without allowing later recoveries to queue it again. */
    public static void markExplained() {
        if (Global.getSector() == null) return;

        Map<String, Object> data = Global.getSector().getPersistentData();
        data.remove(FOUND_KEY);
        data.put(EXPLAINED_KEY, true);
    }
}
