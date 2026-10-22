package catchrelease.campaign.fish.fisherman.chain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
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
        T1_BOUGHT,
        // T2 can be offered from here
        T1_DONE,
        T2_MEET,
        T2_LAMPS,
        T2_HUNT,
        T2_REPORT,
        T2_DEBRIEF,
        T2_SENSOR,
        // T3 starts in the next Fisherman conversation
        T2_SENSOR_BOUGHT
    }

    public static final String KEY = "$catchrelease_fisherChain";
    public static final String PLANTER_PREFIX = "catchrelease_chain";

    public static final String T1_OFFER = "$catchreleaseChainT1Offer";
    public static final String T1_ACTIVE = "$catchreleaseChainT1Active";
    public static final String T1_LENS = "$catchreleaseChainT1Lens";
    public static final String T1_BOUGHT = "$catchreleaseChainT1Bought";
    public static final String T1_SYSTEM = "$catchreleaseChainT1System";
    public static final String T1_PITCH = "$catchreleaseChainT1Pitch";
    public static final String T1_PLACED = "$catchreleaseChainT1Placed";
    public static final String T1_HAS_COMPASS = "$catchreleaseChainT1HasCompass";
    public static final String T1_PULLED = "$catchreleaseChainT1Pulled";
    public static final String T1_LOST_SOLD = "$catchreleaseChainT1LostSold";
    public static final String T1_LOST_STORED = "$catchreleaseChainT1LostStored";
    public static final String T1_LOST_GONE = "$catchreleaseChainT1LostGone";
    public static final String T1_LOST_MARKET = "$catchreleaseChainT1LostMarket";

    public static final String T2_OFFER = "$catchreleaseChainT2Offer";
    public static final String T2_SYSTEM = "$catchreleaseChainT2System";
    public static final String T2_SPECIES = "$catchreleaseChainT2Species";
    public static final String T2_PLACED = "$catchreleaseChainT2Placed";
    public static final String T2_MEET = "$catchreleaseChainT2Meet";
    public static final String T2_SEARCHING = "$catchreleaseChainT2Searching";
    public static final String T2_HUNT = "$catchreleaseChainT2Hunt";
    public static final String T2_CAUGHT = "$catchreleaseChainT2Caught";
    public static final String T2_LOST = "$catchreleaseChainT2Lost";
    public static final String T2_DEBRIEF = "$catchreleaseChainT2Debrief";
    public static final String T2_LANDED = "$catchreleaseChainT2Landed";
    public static final String T2_HAS_SENSOR = "$catchreleaseChainT2HasSensor";

    public static class Saved implements Serializable {

        public Stage stage = Stage.NONE;

        public String systemId;
        public String systemName;
        // the Fisherman has raised T1 himself, at graduation or on the first call after a skip
        public boolean pitched;
        // T1 lamp minigames in the target system that could have shown the golden chest, and whether it was taken
        public int chestMinigames;
        public boolean compassPulled;
        // where the compass was last sold (free-transfer storage does not count), and the answer to "I lost it"
        public String soldMarketId;
        public String soldMarketName;
        public TreasureRumour.Lost lost;
        public String lostMarketName;

        public String t2SystemId;
        public String t2SystemName;
        public String t2SpeciesId;
        public CampaignFleetAPI t2Fleet;
        // the point the fleet circles; removed with the fleet
        public SectorEntityToken t2Hold;
        public boolean t2Caught;
    }

    private FishermanChain() {
    }

    // Quest motes the chain plants carry these planter ids; they are no chart request.
    public static boolean isPlanter(String id) {
        return id != null && id.startsWith(PLANTER_PREFIX);
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

    // Transient listeners for whichever quest is under way; called on load and when a quest starts.
    public static void register() {
        TreasureRumour.CompassWatcher.register();
        CaptainsFleet.register();
    }

    // Display values for one dialog; expiry 0 as every CatchReleaseCMD token.
    public static void writeTokens(MemoryAPI local) {
        Saved saved = get(false);
        Stage stage = getStage();

        local.set(T1_OFFER, stage == Stage.T1_OFFERED, 0);
        local.set(T1_ACTIVE, stage == Stage.T1_ACTIVE, 0);
        local.set(T1_LENS, stage == Stage.T1_LENS, 0);
        local.set(T1_BOUGHT, stage == Stage.T1_BOUGHT, 0);
        local.set(T1_SYSTEM, saved == null || saved.systemName == null ? "" : saved.systemName, 0);
        local.set(T1_PITCH, stage == Stage.T1_OFFERED && saved != null && !saved.pitched, 0);
        local.set(T1_PLACED, saved != null && saved.systemName != null, 0);
        local.set(T1_HAS_COMPASS, stage == Stage.T1_ACTIVE && TreasureRumour.isCarried(), 0);
        local.set(T1_PULLED, saved != null && saved.compassPulled, 0);

        TreasureRumour.Lost lost = saved == null ? null : saved.lost;
        local.set(T1_LOST_SOLD, lost == TreasureRumour.Lost.SOLD, 0);
        local.set(T1_LOST_STORED, lost == TreasureRumour.Lost.STORED, 0);
        local.set(T1_LOST_GONE, lost == TreasureRumour.Lost.GONE, 0);
        local.set(T1_LOST_MARKET, saved == null || saved.lostMarketName == null ? "" : saved.lostMarketName, 0);

        local.set(T2_OFFER, stage == Stage.T1_DONE, 0);
        local.set(T2_SYSTEM, saved == null || saved.t2SystemName == null ? "" : saved.t2SystemName, 0);
        local.set(T2_SPECIES, CaptainsFleet.getSpeciesName(), 0);
        local.set(T2_PLACED, saved != null && saved.t2SystemName != null, 0);
        local.set(T2_MEET, stage == Stage.T2_MEET, 0);
        local.set(T2_SEARCHING, stage == Stage.T2_LAMPS, 0);
        local.set(T2_HUNT, stage == Stage.T2_HUNT, 0);
        local.set(T2_CAUGHT, stage == Stage.T2_REPORT && saved != null && saved.t2Caught, 0);
        local.set(T2_LOST, stage == Stage.T2_REPORT && (saved == null || !saved.t2Caught), 0);
        local.set(T2_DEBRIEF, stage == Stage.T2_DEBRIEF, 0);
        local.set(T2_LANDED, stage == Stage.T2_DEBRIEF && saved != null && saved.t2Caught, 0);
        local.set(T2_HAS_SENSOR, SensorBuild.isOwned(), 0);
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
