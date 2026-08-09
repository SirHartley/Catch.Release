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

/**
 * What range data is for sale, and on which boat.
 * <p>
 * Two shelves, because there are two kinds of schedule. The standing boats in the inhabited core all
 * sell off <b>one</b> shelf held in sector memory - it is one person running the same charts between
 * the same ports, and six boats each rolling their own stock would mean flying a circuit until the
 * roll came up right.
 * <p>
 * The boat out past the last colony keeps its own, rolled fresh each visit and gone with it - that
 * run is not part of any circuit and nothing out there restocks anything.
 * <p>
 * Both are narrow: {@link FishermanConstants#SURVEY_SLOTS_BASE} charts out at once, plus whatever
 * has been earned working for the trade - see {@link #getSlots}. Two is a supply, not a shop, and
 * widening it is the reason to take a job.
 * <p>
 * The shared shelf restocks off the <b>sale</b>, not off a calendar: every purchase books a
 * replacement due {@link FishermanConstants#SHARED_REGEN_DAYS} later, and asking is what redeems the
 * ones that have come due. A monthly tick pays out to whoever happens to ask just after it, which
 * rewards standing still; dating it off the purchase means the wait is the same wait for everybody
 * and starts when the player caused it.
 * <p>
 * One registry sits across both: {@link FishermanConstants#LISTED_KEY} holds every chart on sale
 * anywhere, and no roll picks something already on it. Otherwise the two shelves would sooner or
 * later put the same species up twice, and a player who bought it in one place would find the other
 * copy quietly vanish - which reads as the shop having lied about its stock.
 */
public class FishermanShelf {

    /** One species on offer, with the fish that pay for it. */
    public static class SurveyOffer {
        public FishSpec spec;
        public FishRarity costRarity;
        public int costCount;
    }

    //---------------------------------------------------------------- which shelf

    /** Whether a boat sells off the core's shelf rather than one of its own. */
    public static boolean isShared(SectorEntityToken fleet) {
        return fleet != null
                && fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.SHARED_SHELF_FLAG);
    }

    //---------------------------------------------------------------- how wide

    /** How many charts are out at once: the floor, plus whatever the trade has been paid in. */
    public static int getSlots() {
        return FishermanConstants.SURVEY_SLOTS_BASE + Global.getSector().getMemoryWithoutUpdate()
                .getInt(FishermanConstants.SURVEY_SLOTS_KEY);
    }

    /** One more chart on the shelf, for good. What a job for the trade actually buys. */
    public static void widen(int by) {
        if (by <= 0) return;

        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.SURVEY_SLOTS_KEY,
                Global.getSector().getMemoryWithoutUpdate()
                        .getInt(FishermanConstants.SURVEY_SLOTS_KEY) + by);
    }

    /**
     * The stock a boat is selling from, rolled on first ask.
     * <p>
     * The list is handed back live rather than copied - buying takes an id straight out of it, which
     * is what makes a purchase stick to the right shelf without anything having to say which one it
     * was.
     */
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

    /** The core's one shelf, filled on the first ask of the campaign and restocked by its sales. */
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

    /**
     * Replacements that have come due since anybody last looked.
     * <p>
     * One booked per sale, redeemed on the ask - a shelf nobody visits for a year does not need a
     * script ticking for it, and the answer to "what is owed" is the same arithmetic either way.
     * Also tops up to the slot count outright, which is what makes a slot earned from a job appear
     * as a chart rather than as an empty space.
     */
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

    /** The sales still owing a replacement, by the day each was made. */
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

    //---------------------------------------------------------------- the roll

    /**
     * Fills a shelf up to {@code upTo}: unknown species only, weighted by rarity, and never
     * something another boat already has out.
     * <p>
     * As the player's knowledge grows the common end of the pool empties, so the same weights offer
     * a seasoned fisher rarer charts without anything having to be told that they are seasoned.
     */
    public static void roll(List<String> stock, int upTo) {
        if (stock == null || stock.size() >= upTo) return;

        List<String> listed = getListed();

        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id)) continue;
            if (listed.contains(spec.id)) continue;

            int rung = Math.min(spec.rarity.ordinal(),
                    FishermanConstants.SURVEY_RARITY_WEIGHTS.length - 1);
            picker.add(spec, FishermanConstants.SURVEY_RARITY_WEIGHTS[rung]);
        }

        while (!picker.isEmpty() && stock.size() < upTo) {
            String id = picker.pickAndRemove().id;

            stock.add(id);
            listed.add(id);
        }
    }

    /** Every chart on sale anywhere, which is what keeps two boats off the same one. */
    @SuppressWarnings("unchecked")
    public static List<String> getListed() {
        Object stored = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.LISTED_KEY);
        if (stored instanceof List) return (List<String>) stored;

        List<String> listed = new ArrayList<>();
        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.LISTED_KEY, listed);

        return listed;
    }

    /** Hands charts back to the pool - for a boat that has left with its shelf unsold. */
    public static void release(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        getListed().removeAll(ids);
    }

    /**
     * Gives a departing boat's own shelf back, if it ever rolled one.
     * <p>
     * Reads the memory key rather than going through {@link #getStock}, which would roll a shelf for
     * a boat nobody ever talked to purely in order to hand it straight back. The shared shelf is
     * nobody's to release - it outlives every boat selling off it.
     */
    @SuppressWarnings("unchecked")
    public static void releaseFor(SectorEntityToken fleet) {
        if (fleet == null || isShared(fleet)) return;

        Object stored = fleet.getMemoryWithoutUpdate().get(FishermanConstants.SURVEY_STOCK_KEY);
        if (stored instanceof List) release((List<String>) stored);
    }

    //---------------------------------------------------------------- what is on it

    /** The shelf as offers, pruned of anything learned since it was rolled. */
    public static List<SurveyOffer> getOffers(SectorEntityToken fleet) {
        List<SurveyOffer> offers = new ArrayList<>();
        List<String> stock = getStock(fleet);

        List<String> known = new ArrayList<>();
        for (String id : stock) {
            if (FishLog.isCaught(id) || FishLog.isLocationDataUnlocked(id)) known.add(id);
        }

        //learned elsewhere, so off both the shelf and the pool - otherwise a chart nobody can sell
        //holds a slot the shelf would rather have given to something the player can still use
        stock.removeAll(known);
        release(known);

        for (String id : stock) {
            FishSpec spec = FishSpecLoader.getFishSpec(id);
            if (spec == null) continue;

            SurveyOffer offer = new SurveyOffer();
            offer.spec = spec;

            int rung = spec.rarity.ordinal();
            offer.costRarity = rung == 0 ? FishRarity.COMMON : FishRarity.values()[rung - 1];
            offer.costCount = rung == 0 ? 1 : FishermanConstants.SURVEY_COST;

            offers.add(offer);
        }

        return offers;
    }

    /**
     * A chart sold: off the shelf it was on, out of the pool, and - on the shared shelf - a
     * replacement booked, dated from today.
     */
    public static void take(SectorEntityToken fleet, String specId) {
        getStock(fleet).remove(specId);
        getListed().remove(specId);

        if (isShared(fleet)) {
            getPending().add(Global.getSector().getClock().getTimestamp());
        }
    }

    /** A sale taken back, put where it stood - and the replacement it booked cancelled with it. */
    public static void putBack(SectorEntityToken fleet, String specId, int index) {
        List<String> stock = getStock(fleet);

        if (!stock.contains(specId)) stock.add(Math.min(index, stock.size()), specId);
        if (!getListed().contains(specId)) getListed().add(specId);

        if (!isShared(fleet)) return;

        //the newest booking is this purchase's, since undo only ever takes back the last one
        List<Long> pending = getPending();
        if (!pending.isEmpty()) pending.remove(pending.size() - 1);
    }
}
