package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.ArrayList;
import java.util.List;


public class FishermanShelf {


    public static class SurveyOffer {
        public FishSpec spec;
        public FishRarity costRarity;
        public int costCount;
    }


    public static boolean isShared(SectorEntityToken fleet) {
        return fleet != null
                && fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.SHARED_SHELF_FLAG);
    }


    public static int getSlots() {
        return FishermanConstants.SURVEY_SLOTS_BASE + Global.getSector().getMemoryWithoutUpdate()
                .getInt(FishermanConstants.SURVEY_SLOTS_KEY);
    }


    public static void widen(int by) {
        if (by <= 0) return;

        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.SURVEY_SLOTS_KEY,
                Global.getSector().getMemoryWithoutUpdate()
                        .getInt(FishermanConstants.SURVEY_SLOTS_KEY) + by);
    }


    public static List<String> getStock(SectorEntityToken fleet) {
        if (fleet == null) return new ArrayList<>();

        return isShared(fleet) ? getSharedStock() : getPrivateStock(fleet);
    }

    @SuppressWarnings("unchecked")
    protected static List<String> getPrivateStock(SectorEntityToken fleet) {
        Object stored = fleet.getMemoryWithoutUpdate().get(FishermanConstants.SURVEY_STOCK_KEY);
        if (stored instanceof List) return (List<String>) stored;

        List<String> stock = new ArrayList<>();
        roll(stock, getSlots());

        fleet.getMemoryWithoutUpdate().set(FishermanConstants.SURVEY_STOCK_KEY, stock);

        return stock;
    }


    @SuppressWarnings("unchecked")
    public static List<String> getSharedStock() {
        Object stored = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.SHARED_STOCK_KEY);

        if (stored instanceof List) {
            List<String> stock = (List<String>) stored;
            redeem(stock);

            return stock;
        }

        List<String> stock = new ArrayList<>();
        roll(stock, getSlots());

        Global.getSector().getMemoryWithoutUpdate()
                .set(FishermanConstants.SHARED_STOCK_KEY, stock);

        return stock;
    }


    protected static void redeem(List<String> stock) {
        List<Long> pending = getPending();

        for (int i = pending.size() - 1; i >= 0; i--) {
            if (Global.getSector().getClock().getElapsedDaysSince(pending.get(i))
                    < FishermanConstants.SHARED_REGEN_DAYS) {

                continue;
            }

            pending.remove(i);
        }

        roll(stock, Math.max(0, getSlots() - pending.size()));
    }


    @SuppressWarnings("unchecked")
    public static List<Long> getPending() {
        Object stored = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.SHARED_PENDING_KEY);
        if (stored instanceof List) return (List<Long>) stored;

        List<Long> pending = new ArrayList<>();
        Global.getSector().getMemoryWithoutUpdate()
                .set(FishermanConstants.SHARED_PENDING_KEY, pending);

        return pending;
    }


    public static void roll(List<String> stock, int upTo) {
        if (stock == null || stock.size() >= upTo) return;

        List<String> listed = getListed();

        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id)) continue;
            if (listed.contains(spec.id)) continue;

            int rung = Math.min(spec.rarity.rank,
                    FishermanConstants.SURVEY_RARITY_WEIGHTS.length - 1);
            picker.add(spec, FishermanConstants.SURVEY_RARITY_WEIGHTS[rung]);
        }

        while (!picker.isEmpty() && stock.size() < upTo) {
            String id = picker.pickAndRemove().id;

            stock.add(id);
            listed.add(id);
        }
    }


    @SuppressWarnings("unchecked")
    public static List<String> getListed() {
        Object stored = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.LISTED_KEY);
        if (stored instanceof List) return (List<String>) stored;

        List<String> listed = new ArrayList<>();
        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.LISTED_KEY, listed);

        return listed;
    }


    public static void release(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        getListed().removeAll(ids);
    }


    @SuppressWarnings("unchecked")
    public static void releaseFor(SectorEntityToken fleet) {
        if (fleet == null || isShared(fleet)) return;

        Object stored = fleet.getMemoryWithoutUpdate().get(FishermanConstants.SURVEY_STOCK_KEY);
        if (stored instanceof List) release((List<String>) stored);
    }


    public static List<SurveyOffer> getOffers(SectorEntityToken fleet) {
        List<SurveyOffer> offers = new ArrayList<>();
        List<String> stock = getStock(fleet);

        List<String> known = new ArrayList<>();
        for (String id : stock) {
            if (FishLog.isCaught(id) || FishLog.isLocationDataUnlocked(id)) known.add(id);
        }

        stock.removeAll(known);
        release(known);

        for (String id : stock) {
            FishSpec spec = FishSpecLoader.getFishSpec(id);
            if (spec == null) continue;

            SurveyOffer offer = new SurveyOffer();
            offer.spec = spec;

            int rung = spec.rarity.rank;
            offer.costRarity = rung == 0 ? FishRarity.COMMON : FishRarity.ofRank(rung - 1);
            offer.costCount = FishermanConstants.SURVEY_COST;

            offers.add(offer);
        }

        return offers;
    }


    public static void take(SectorEntityToken fleet, String specId) {
        getStock(fleet).remove(specId);
        getListed().remove(specId);

        if (isShared(fleet)) {
            getPending().add(Global.getSector().getClock().getTimestamp());
        }
    }


    public static void putBack(SectorEntityToken fleet, String specId, int index) {
        List<String> stock = getStock(fleet);

        if (!stock.contains(specId)) stock.add(Math.min(index, stock.size()), specId);
        if (!getListed().contains(specId)) getListed().add(specId);

        if (!isShared(fleet)) return;

        // the newest booking is this purchase's, since undo only ever takes back the last one
        List<Long> pending = getPending();
        if (!pending.isEmpty()) pending.remove(pending.size() - 1);
    }
}
