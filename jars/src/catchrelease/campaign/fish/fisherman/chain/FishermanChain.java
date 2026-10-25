package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.shop.ShopEntry;
import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.campaign.fish.tackle.Tackle;
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
        // T3 is offered from here
        T2_SENSOR_BOUGHT,
        T3_HUNT,
        T3_CAUGHT,
        T3_TRADE,
        T3_TRADED,
        // T4 starts in T3's closing conversation
        T4_LISTEN,
        T4_LANDED,
        // "Hunting the legendary fish", until every legendary is caught
        HUNTING,
        HUNT_DONE
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

    public static final String T3_OFFER = "$catchreleaseChainT3Offer";
    public static final String T3_PLACED = "$catchreleaseChainT3Placed";
    public static final String T3_SYSTEM = "$catchreleaseChainT3System";
    public static final String T3_PLANET = "$catchreleaseChainT3Planet";
    public static final String T3_SPECIES = "$catchreleaseChainT3Species";
    public static final String T3_HUNT = "$catchreleaseChainT3Hunt";
    public static final String T3_CAUGHT = "$catchreleaseChainT3Caught";
    public static final String T3_MARKET_PLACED = "$catchreleaseChainT3MarketPlaced";
    public static final String T3_MARKET = "$catchreleaseChainT3Market";
    public static final String T3_MARKET_SYSTEM = "$catchreleaseChainT3MarketSystem";
    public static final String T3_PRICE = "$catchreleaseChainT3Price";
    public static final String T3_HAGGLED = "$catchreleaseChainT3Haggled";
    public static final String T3_CAN_PAY = "$catchreleaseChainT3CanPay";
    public static final String T3_TRADED = "$catchreleaseChainT3Traded";

    public static final String T4_LANDED = "$catchreleaseChainT4Landed";

    // a quest step waits on an Outfitter purchase: the Fisherman's Outfitter option is highlighted
    public static final String OUTFITTER_WANTED = "$catchreleaseChainOutfitterWanted";

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

        public String t3SystemId;
        public String t3SystemName;
        public String t3PlanetId;
        public String t3PlanetName;
        public String t3SpeciesId;
        // the Reliquary's owner drinks at this market's bar; a story point brings his price down once
        public String t3MarketId;
        public String t3MarketName;
        public String t3MarketSystemName;
        public boolean t3Haggled;

        // legendary id to the name its latest rumour used
        public Map<String, String> toldNames;
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
        WoundedEpic.register();
    }

    // The module a quest step is waiting for the player to buy, if any.
    public static Tackle getWantedPurchase() {
        return switch (getStage()) {
            case T1_LENS -> Tackle.GLITTER_LENS;
            case T2_SENSOR -> Tackle.NEEDLE_SENSOR;
            default -> null;
        };
    }

    // Puts the wanted module on the Outfitter's shopping list; buying it takes it off again (ShopEntry.grant).
    public static void listWantedPurchase() {
        Tackle wanted = getWantedPurchase();
        if (wanted == null) return;

        ShopEntry entry = ShopEntry.of(wanted);
        if (ShopMarks.isMarkable(entry)) ShopMarks.mark(ShopMarks.getMarkKey(entry));
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

        local.set(T3_OFFER, stage == Stage.T2_SENSOR_BOUGHT, 0);
        local.set(T3_PLACED, saved != null && saved.t3PlanetName != null, 0);
        local.set(T3_SYSTEM, saved == null || saved.t3SystemName == null ? "" : saved.t3SystemName, 0);
        local.set(T3_PLANET, saved == null || saved.t3PlanetName == null ? "" : saved.t3PlanetName, 0);
        local.set(T3_SPECIES, WoundedEpic.getSpeciesName(), 0);
        local.set(T3_HUNT, stage == Stage.T3_HUNT, 0);
        local.set(T3_CAUGHT, stage == Stage.T3_CAUGHT, 0);
        local.set(T3_MARKET_PLACED, ReliquaryTrade.getMarket() != null, 0);
        local.set(T3_MARKET, saved == null || saved.t3MarketName == null ? "" : saved.t3MarketName, 0);
        local.set(T3_MARKET_SYSTEM, saved == null || saved.t3MarketSystemName == null ? ""
                : saved.t3MarketSystemName, 0);
        local.set(T3_PRICE, ReliquaryTrade.getPrice() == ReliquaryTrade.HAGGLED_PRICE ? "two" : "three", 0);
        local.set(T3_HAGGLED, saved != null && saved.t3Haggled, 0);
        local.set(T3_CAN_PAY, ReliquaryTrade.canPay(), 0);
        local.set(T3_TRADED, stage == Stage.T3_TRADED, 0);

        local.set(T4_LANDED, stage == Stage.T4_LANDED, 0);
        local.set(OUTFITTER_WANTED, getWantedPurchase() != null, 0);
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
