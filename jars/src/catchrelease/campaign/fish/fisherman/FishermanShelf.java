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
 * What survey data is for sale, and on which boat.
 * <p>
 * Two shelves, because there are two kinds of boat. The standing fleets in the inhabited core all
 * sell off <b>one</b> shelf held in sector memory - they are the same trade running the same charts
 * between the same ports, and six boats each rolling their own stock would mean flying a circuit
 * until the roll came up right. That shelf is not restocked on a visit either; it comes back at
 * {@link FishermanConstants#SHARED_REGEN_PER_MONTH} charts a month whether anybody is buying or not,
 * which is a supply rather than a shop.
 * <p>
 * The wanderer out past the last colony keeps his own, rolled fresh each visit and gone with him -
 * he is not part of any circuit and nothing out there restocks anything.
 * <p>
 * One registry sits across both: {@link FishermanConstants#LISTED_KEY} holds every chart on sale
 * anywhere, and no roll picks something already on it. Otherwise the two shelves would sooner or
 * later put the same species up twice, and a player who bought it in one place would find the other
 * copy quietly vanish - which reads as the shop having lied about its stock.
 * <p>
 * The shared shelf tops itself up lazily, on the ask, from the timestamp of the last top-up. A
 * script ticking once a month for a shelf nobody may look at for a year is work for nothing, and the
 * answer to "how much has come back" is arithmetic either way.
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
        roll(stock, FishermanConstants.SURVEY_STOCK);

        fleet.getMemoryWithoutUpdate().set(FishermanConstants.SURVEY_STOCK_KEY, stock);

        return stock;
    }

    /** The core's one shelf, filled on the first ask of the campaign and topped up by the clock. */
    @SuppressWarnings("unchecked")
    public static List<String> getSharedStock() {
        Object stored = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.SHARED_STOCK_KEY);

        if (stored instanceof List) {
            List<String> stock = (List<String>) stored;
            regenerate(stock);

            return stock;
        }

        List<String> stock = new ArrayList<>();
        roll(stock, FishermanConstants.SURVEY_STOCK);

        Global.getSector().getMemoryWithoutUpdate()
                .set(FishermanConstants.SHARED_STOCK_KEY, stock);
        stamp(0f);

        return stock;
    }

    /**
     * Whole months since the last top-up, paid out at the monthly rate.
     * <p>
     * The part-month is banked rather than thrown away: the stamp always moves to now, and what was
     * left over is carried in days. Moving the stamp back by the unspent remainder instead would
     * mean doing arithmetic on a timestamp whose units are the engine's business, and a player who
     * asked on the 29th day of every month would otherwise never be owed anything at all.
     */
    protected static void regenerate(List<String> stock) {
        Object last = Global.getSector().getMemoryWithoutUpdate()
                .get(FishermanConstants.SHARED_STAMP_KEY);

        if (!(last instanceof Long)) {
            stamp(0f);
            return;
        }

        float banked = Global.getSector().getMemoryWithoutUpdate()
                .getFloat(FishermanConstants.SHARED_BANKED_KEY);

        float elapsed = banked + Global.getSector().getClock().getElapsedDaysSince((Long) last);

        int months = (int) (elapsed / FishermanConstants.SHARED_REGEN_DAYS);
        if (months <= 0) return;

        roll(stock, Math.min(FishermanConstants.SURVEY_STOCK,
                stock.size() + months * FishermanConstants.SHARED_REGEN_PER_MONTH));

        stamp(elapsed - months * FishermanConstants.SHARED_REGEN_DAYS);
    }

    protected static void stamp(float banked) {
        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.SHARED_STAMP_KEY,
                Global.getSector().getClock().getTimestamp());
        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.SHARED_BANKED_KEY,
                banked);
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

    /** A chart sold: off the shelf it was on, and out of the pool. */
    public static void take(SectorEntityToken fleet, String specId) {
        getStock(fleet).remove(specId);
        getListed().remove(specId);
    }

    /** A sale taken back, put where it stood. */
    public static void putBack(SectorEntityToken fleet, String specId, int index) {
        List<String> stock = getStock(fleet);

        if (!stock.contains(specId)) stock.add(Math.min(index, stock.size()), specId);
        if (!getListed().contains(specId)) getListed().add(specId);
    }
}
