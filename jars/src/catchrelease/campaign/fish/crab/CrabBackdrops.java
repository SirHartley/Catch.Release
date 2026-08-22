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

public class CrabBackdrops {
    public static final String OFFER_KEY = "$catchrelease_crabBackdropAt";
    public static final String TURN_KEY = "$catchrelease_crabBackdropTurn";
    public static final String SOLD_AT_KEY = "$catchrelease_crabBackdropSoldAt";
    public static final float RESTOCK_DAYS = 60f;
    public static final int[] CREDITS_BY_RARITY = {12000, 25000, 45000, 75000, 120000};
    public static final int[] CRABS_BY_RARITY = {2, 3, 5, 7, 10};

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
