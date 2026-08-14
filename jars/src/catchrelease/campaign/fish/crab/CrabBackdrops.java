package catchrelease.campaign.fish.crab;

import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.campaign.fish.colony.Backdrops;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.BackdropLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rolled-up scene under Crablobab's arm, and which one it is today.
 * <p>
 * Separate from {@link CrabWares} because it is a different kind of stock and the difference is the
 * whole behaviour. A permanent ware is one named object sold once ever - the constant <i>is</i> the
 * item and the dialogue rows are written per constant. (The explosive charge is the explicit
 * consumable exception.) A backdrop is stock: there are as many as the table has rows, he carries
 * exactly one at a time, and which one depends on where you happen to have run into him.
 * <p>
 * <b>It is a rotation, not a roll.</b> He works down the table, one scene per port, and the port
 * remembers what he had there - so a man met twice on the same rock is carrying the same thing both
 * times, and a man met on the next rock along is carrying the next thing. Table order is rarity
 * order, which is the arrangement that has him leading with a reef and not with the abyss.
 * <p>
 * Anything already owned drops out of the rotation, including the scene a port was holding for you:
 * a man who keeps offering what is already hanging in your conservatory has not looked in his own
 * coat.
 */
public class CrabBackdrops {

    /** What he had at each port, by market id, so a shop does not reshuffle while you look at it. */
    public static final String OFFER_KEY = "$catchrelease_crabBackdropAt";

    /** How far down the table he has got. Advances once per port that asks. */
    public static final String TURN_KEY = "$catchrelease_crabBackdropTurn";

    /** Last completed backdrop sale at each port. A sale empties that local roll for two months. */
    public static final String SOLD_AT_KEY = "$catchrelease_crabBackdropSoldAt";
    public static final float RESTOCK_DAYS = 60f;

    /**
     * What he wants for one, by rarity. Steeper than the ladder the fish are priced on, because a
     * scene is worth exactly what somebody will pay for a picture and he knows it.
     */
    public static final int[] CREDITS_BY_RARITY = {12000, 25000, 45000, 75000, 120000};
    public static final int[] CRABS_BY_RARITY = {2, 3, 5, 7, 10};

    /**
     * The scene he is carrying at this port, or null when the coat is out of them.
     * <p>
     * Assigns one on the first ask and keeps it: the stall is drawn from tokens, which are rebuilt
     * on every rules pass, so anything that decided afresh each time would have the thing change
     * name between the option and the price.
     */
    public static Backdrop getOffer(MarketAPI market) {
        if (market == null || Global.getSector() == null) return null;
        if (isRestocking(market)) return null;

        List<Backdrop> pool = getPool();
        if (pool.isEmpty()) return null;

        Map<String, String> held = getAssignments();

        Backdrop standing = BackdropLoader.get(held.get(market.getId()));
        if (standing != null && pool.contains(standing)) return standing;

        Backdrop next = pool.get(Math.floorMod(getTurn(), pool.size()));

        held.put(market.getId(), next.id);
        setTurn(getTurn() + 1);

        return next;
    }

    /** Everything he is allowed to carry and you do not already have, in table order. */
    public static List<Backdrop> getPool() {
        List<Backdrop> pool = new ArrayList<>();

        for (Backdrop backdrop : BackdropLoader.getAll()) {
            if (!backdrop.crabStock) continue;
            if (Backdrops.isOwned(backdrop)) continue;

            pool.add(backdrop);
        }

        return pool;
    }

    public static int getCredits(Backdrop backdrop) {
        return backdrop == null ? 0 : CREDITS_BY_RARITY[backdrop.rarity.rank];
    }

    public static int getCrabs(Backdrop backdrop) {
        return backdrop == null ? 0 : CRABS_BY_RARITY[backdrop.rarity.rank];
    }

    /** The crabs half of the price, as the same ask everything else in the mod is written in. */
    public static FishRequirement getCatch(Backdrop backdrop) {
        FishRequirement req = new FishRequirement();

        req.tag = "crab";
        req.count = getCrabs(backdrop);

        return req;
    }

    public static boolean canAfford(Backdrop backdrop) {
        if (backdrop == null || Global.getSector().getPlayerFleet() == null) return false;

        if (FishCurrency.count(getCatch(backdrop)) < getCrabs(backdrop)) return false;

        return Global.getSector().getPlayerFleet().getCargo().getCredits().get()
                >= getCredits(backdrop);
    }

    /**
     * Takes both halves and hands the roll over, or takes neither - crabs first, on
     * {@link CrabWares#buy}'s own argument: fish are the half that can fail, and charging for a
     * purchase that then did not happen is the one outcome worth writing an order for.
     */
    public static boolean buy(MarketAPI market) {
        Backdrop offer = getOffer(market);
        if (offer == null || !canAfford(offer)) return false;

        if (!FishCurrency.spend(getCatch(offer))) return false;

        int credits = getCredits(offer);
        if (credits > 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(credits);
        }

        Backdrops.own(offer.id);
        getSaleTimes().put(market.getId(), Global.getSector().getClock().getTimestamp());

        return true;
    }

    /**
     * Whether this port's backdrop slot is still empty after a sale. The timestamp is redeemed on
     * ask rather than by a campaign script: no ticking object is needed for stock that matters only
     * while the stall is open, and a save loaded after the deadline repairs itself immediately.
     */
    public static boolean isRestocking(MarketAPI market) {
        if (market == null || Global.getSector() == null) return false;

        Map<String, Long> soldAt = getSaleTimes();
        Long timestamp = soldAt.get(market.getId());
        if (timestamp == null) return false;

        if (Global.getSector().getClock().getElapsedDaysSince(timestamp) < RESTOCK_DAYS) {
            return true;
        }

        soldAt.remove(market.getId());
        return false;
    }

    protected static int getTurn() {
        return Global.getSector().getMemoryWithoutUpdate().getInt(TURN_KEY);
    }

    protected static void setTurn(int turn) {
        Global.getSector().getMemoryWithoutUpdate().set(TURN_KEY, turn);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, String> getAssignments() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(OFFER_KEY);
        if (stored instanceof Map) return (Map<String, String>) stored;

        Map<String, String> held = new LinkedHashMap<>();
        data.put(OFFER_KEY, held);

        return held;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Long> getSaleTimes() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(SOLD_AT_KEY);
        if (stored instanceof Map) return (Map<String, Long>) stored;

        Map<String, Long> soldAt = new LinkedHashMap<>();
        data.put(SOLD_AT_KEY, soldAt);

        return soldAt;
    }
}
