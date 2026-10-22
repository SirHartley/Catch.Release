package catchrelease.campaign.fish.fisherman.chain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.io.Serializable;
import java.util.Map;

// The post-tutorial Fisherman quests, one saved stage for the whole chain. Rows read it only through the Boolean tokens
// written here; the quests' own state (targets, counters, claims) lives on Saved beside the stage.
public final class FishermanChain {

    public enum Stage {

        NONE,
        T1_OFFERED,
        T1_ACTIVE,
        T1_LENS,
        T1_DONE
    }

    public static final String KEY = "$catchrelease_fisherChain";

    public static final String T1_OFFER = "$catchreleaseChainT1Offer";
    public static final String T1_ACTIVE = "$catchreleaseChainT1Active";
    public static final String T1_LENS = "$catchreleaseChainT1Lens";
    public static final String T1_SYSTEM = "$catchreleaseChainT1System";
    public static final String T1_PITCH = "$catchreleaseChainT1Pitch";
    public static final String T1_PLACED = "$catchreleaseChainT1Placed";

    public static class Saved implements Serializable {

        public Stage stage = Stage.NONE;

        public String systemId;
        public String systemName;
        // the Fisherman has raised T1 himself, at graduation or on the first call after a skip
        public boolean pitched;
    }

    private FishermanChain() {
    }

    public static Stage getStage() {
        Saved saved = get(false);

        return saved == null || saved.stage == null ? Stage.NONE : saved.stage;
    }

    public static boolean isAt(Stage stage) {
        return getStage() == stage;
    }

    public static void setStage(Stage stage) {
        get(true).stage = stage;
    }

    // The tutorial's end, finished or skipped, opens the chain; a chain already under way is left alone.
    public static void offerFirst() {
        if (getStage() == Stage.NONE) setStage(Stage.T1_OFFERED);
    }

    // Display values for one dialog; expiry 0 as every CatchReleaseCMD token.
    public static void writeTokens(MemoryAPI local) {
        Saved saved = get(false);
        Stage stage = getStage();

        local.set(T1_OFFER, stage == Stage.T1_OFFERED, 0);
        local.set(T1_ACTIVE, stage == Stage.T1_ACTIVE, 0);
        local.set(T1_LENS, stage == Stage.T1_LENS, 0);
        local.set(T1_SYSTEM, saved == null || saved.systemName == null ? "" : saved.systemName, 0);
        local.set(T1_PITCH, stage == Stage.T1_OFFERED && saved != null && !saved.pitched, 0);
        local.set(T1_PLACED, saved != null && saved.systemName != null, 0);
    }

    public static Saved get(boolean create) {
        if (Global.getSector() == null) return null;

        Map<String, Object> data = Global.getSector().getPersistentData();
        if (data.get(KEY) instanceof Saved saved) return saved;
        if (!create) return null;

        Saved fresh = new Saved();
        data.put(KEY, fresh);

        return fresh;
    }
}
